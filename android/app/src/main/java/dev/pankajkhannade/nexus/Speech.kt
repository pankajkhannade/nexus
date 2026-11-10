package dev.pankajkhannade.nexus

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.Executors

/**
 * Speech to text, in two layers so either can be swapped (a better on-device model later) and tests can
 * drive it without a microphone:
 * - [SpeechEngine]: one recogniser session at a time. [AndroidSpeechEngine] wraps Android's SpeechRecognizer.
 * - [ContinuousListener]: keeps listening across the recogniser's own end-of-speech cut-offs until the
 *   parent taps Done, goes quiet, or 60 seconds pass, and joins every piece of text it heard.
 *
 * Why: Android's recogniser ends a session after a short pause (often about 1-2 s, whatever silence extras
 * we pass), and it often ends with ERROR_NO_MATCH even after showing partial words. Phase 1 treated that end
 * as the whole command and threw the partial words away, so a parent who paused mid-message was cut off.
 */
interface SpeechCallbacks {
    /** The words so far in this session (each call replaces the last). */
    fun onPartial(text: String)
    fun onEndOfSpeech()
    /** The session's final words, or null/blank when the recogniser had none. */
    fun onFinal(text: String?)
    /** A SpeechRecognizer.ERROR_* code. The session is over. */
    fun onError(code: Int)
}

data class SpeechRequest(
    val language: String,
    val mode: VoiceMode,
    /** A dictated message (long, with pauses) rather than a short command. */
    val longForm: Boolean,
    /** True when this session continues one that just ended (a short pause first frees the microphone). */
    val restart: Boolean = false,
)

interface SpeechEngine {
    fun available(): Boolean
    fun checkMode(language: String, onResult: (VoiceMode) -> Unit)
    /** Starts one session, replacing any running one. Callbacks arrive on the main thread. */
    fun start(request: SpeechRequest, callbacks: SpeechCallbacks)
    /** Ends and throws away the current recogniser; the next [start] creates a fresh one. */
    fun reset()
}

/** Errors after which a fresh recogniser usually works (ERROR_SERVER_DISCONNECTED = 11). */
private val RECREATE_ERRORS = setOf(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, 11)

/** Errors that mean "nothing more was heard", not "voice is broken". */
private val QUIET_ERRORS = setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

/**
 * Continuous dictation on top of a [SpeechEngine]. Text from every session is kept, in order, and partial
 * words are never thrown away: when a session ends without final words, its last partial is kept instead.
 *
 * A session end leads to one of:
 * - finish: the parent tapped Done, the text is already a complete command ([isComplete]), the parent has
 *   been quiet for [idleMs] after speaking, nothing was said for [noSpeechMs], [maxMs] passed, or voice failed;
 * - restart: otherwise, so a pause mid-sentence never ends the dictation.
 */
class ContinuousListener(
    private val engine: SpeechEngine,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxMs: Long = 60_000,
    private val idleMs: Long = 6_000,
    private val noSpeechMs: Long = 10_000,
) {
    var active: Boolean = false
        private set
    /** Sessions started in this dictation (1 = no restart yet). For tests and logs. */
    var sessions: Int = 0
        private set

    private var session = 0
    private val pieces = mutableListOf<String>()
    private var partial = ""
    private var startedAt = 0L
    private var lastWordsAt = 0L
    private var recreated = 0
    private var emptyRuns = 0
    private var request = SpeechRequest("en", VoiceMode.ONLINE, longForm = false)
    private var isComplete: (String) -> Boolean = { false }
    private var onText: (String) -> Unit = {}
    private var onDone: (String?) -> Unit = {}

    /** Everything heard so far, including the words of the session still running. */
    val text: String get() = (pieces + partial).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    fun start(
        language: String,
        mode: VoiceMode,
        longForm: Boolean,
        isComplete: (String) -> Boolean = { false },
        onText: (String) -> Unit,
        onDone: (String?) -> Unit,
    ) {
        cancel()
        this.request = SpeechRequest(language, mode, longForm)
        this.isComplete = isComplete
        this.onText = onText
        this.onDone = onDone
        pieces.clear()
        partial = ""
        recreated = 0
        emptyRuns = 0
        sessions = 0
        startedAt = now()
        lastWordsAt = startedAt
        active = true
        begin(restart = false)
    }

    /** The parent tapped Done: finish now with everything heard, including the running session's words. */
    fun done() {
        if (active) finish()
    }

    /** Stop / Back: end without a result. */
    fun cancel() {
        if (!active) return
        active = false
        session++
        engine.reset()
    }

    private fun begin(restart: Boolean) {
        val id = ++session
        sessions++
        engine.start(request.copy(restart = restart), object : SpeechCallbacks {
            override fun onPartial(text: String) {
                if (id != session || !active) return
                if (text.isNotBlank() && text.trim() != partial.trim()) lastWordsAt = now()
                partial = text
                onText(this@ContinuousListener.text)
            }

            // The session's onFinal or onError follows; the decision is made there so its words are kept.
            override fun onEndOfSpeech() = Unit

            override fun onFinal(text: String?) {
                if (id == session && active) ended(text, null)
            }

            override fun onError(code: Int) {
                if (id == session && active) ended(null, code)
            }
        })
    }

    private fun ended(final: String?, error: Int?) {
        val words = final?.trim().orEmpty().ifEmpty { partial.trim() }
        partial = ""
        if (words.isNotEmpty()) {
            pieces += words
            lastWordsAt = now()
            emptyRuns = 0
        } else {
            emptyRuns++
        }
        val all = text
        onText(all)
        Log.i("NexusVoice", "session=$sessions ended error=$error words=${words.isNotEmpty()} chars=${all.length}")
        val t = now()
        when {
            error != null && error !in QUIET_ERRORS && error !in RECREATE_ERRORS -> return finish()
            error in RECREATE_ERRORS -> {
                if (++recreated > 3) return finish()
                engine.reset()
            }
            all.isNotEmpty() && isComplete(all) -> return finish()
        }
        when {
            t - startedAt >= maxMs -> finish()
            all.isNotEmpty() && words.isEmpty() && t - lastWordsAt >= idleMs -> finish()
            all.isEmpty() && t - startedAt >= noSpeechMs -> finish()
            emptyRuns >= 8 -> finish()
            else -> begin(restart = true)
        }
    }

    private fun finish() {
        val result = text.ifBlank { null }
        active = false
        session++
        engine.reset()
        onDone(result)
    }
}

/** Android's SpeechRecognizer, one fresh recogniser per session (reusing one invites ERROR_RECOGNIZER_BUSY). */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null
    private var pendingStart: Runnable? = null

    override fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Checks whether [language] is installed for on-device recognition (Android 13+); otherwise ONLINE. */
    override fun checkMode(language: String, onResult: (VoiceMode) -> Unit) {
        if (!available()) return onResult(VoiceMode.UNAVAILABLE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return onResult(VoiceMode.ONLINE)
        }
        val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val tag = speechTag(language)
        probe.checkRecognitionSupport(
            recognizeIntent(SpeechRequest(language, VoiceMode.ON_DEVICE, longForm = false)),
            Executors.newSingleThreadExecutor(),
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val local = support.installedOnDeviceLanguages.any { it.equals(tag, true) }
                    main.post {
                        probe.destroy()
                        onResult(if (local) VoiceMode.ON_DEVICE else VoiceMode.ONLINE)
                    }
                }

                override fun onError(error: Int) {
                    main.post {
                        probe.destroy()
                        onResult(VoiceMode.ONLINE)
                    }
                }
            },
        )
    }

    override fun start(request: SpeechRequest, callbacks: SpeechCallbacks) {
        reset()
        val begin = Runnable {
            pendingStart = null
            val r = if (request.mode == VoiceMode.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            recognizer = r
            r.setRecognitionListener(listenerFor(r, callbacks))
            arm(r, callbacks, 15_000)
            try {
                r.startListening(recognizeIntent(request))
            } catch (e: Exception) {
                Log.i("NexusVoice", "startListening failed: ${e.javaClass.simpleName}")
                callbacks.onError(SpeechRecognizer.ERROR_CLIENT)
            }
        }
        pendingStart = begin
        // A restart right after a session ended can hit ERROR_RECOGNIZER_BUSY while the service lets go of the mic.
        if (request.restart) main.postDelayed(begin, 250) else main.post(begin)
    }

    override fun reset() {
        pendingStart?.let(main::removeCallbacks)
        pendingStart = null
        disarm()
        recognizer?.let {
            it.setRecognitionListener(null)
            it.destroy()
        }
        recognizer = null
    }

    /** Some recognisers go silent after end-of-speech without results or an error; end the session for them. */
    private fun arm(r: SpeechRecognizer, callbacks: SpeechCallbacks, ms: Long) {
        disarm()
        val w = Runnable { if (recognizer === r) callbacks.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
        watchdog = w
        main.postDelayed(w, ms)
    }

    private fun disarm() {
        watchdog?.let(main::removeCallbacks)
        watchdog = null
    }

    private fun listenerFor(r: SpeechRecognizer, callbacks: SpeechCallbacks) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onEndOfSpeech() {
            if (recognizer !== r) return
            arm(r, callbacks, 5_000)
            callbacks.onEndOfSpeech()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (recognizer !== r) return
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(callbacks::onPartial)
        }

        override fun onResults(results: Bundle?) {
            if (recognizer !== r) return
            disarm()
            callbacks.onFinal(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
        }

        override fun onError(error: Int) {
            if (recognizer !== r) return
            disarm()
            Log.i("NexusVoice", "recognizer error=$error")
            callbacks.onError(error)
        }
    }

    private fun recognizeIntent(request: SpeechRequest): Intent {
        val tag = speechTag(request.language)
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Hints only: many recognisers cap or ignore these, which is why ContinuousListener restarts.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, if (request.longForm) 6_000L else 2_000L)
            .apply { if (request.mode == VoiceMode.ON_DEVICE) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) }
    }
}
