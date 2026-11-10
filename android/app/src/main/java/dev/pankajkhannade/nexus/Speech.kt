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
    /** The session's final hypotheses, best first (up to 5), or empty when the recogniser had none. */
    fun onFinal(texts: List<String>)
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
    /** Names the recogniser should expect (saved people, phone-book names). Used on Android 13+. */
    val biasing: List<String> = emptyList(),
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

/** Words after which a speaker is never done: "पंकजला सांग की …", "दूध आणि …", "tell him that …". */
private val JOINERS = setOf(
    "आणि", "आणी", "अणि", "ani", "aani", "की", "कि", "ki", "kee", "म्हणजे", "mhanje", "और", "aur", "and", "that", "to", "ko", "la", "को", "ला",
    "सांग", "sang", "बोल", "bol", "बोलो", "bolo", "tell", "कह", "kaho", "बता", "bata",
)

private val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")

/** True when the last word is a joiner, so a pause there is thinking, not the end. */
fun endsWithJoiner(text: String): Boolean =
    text.trim().split(NON_WORD).lastOrNull { it.isNotBlank() }?.lowercase() in JOINERS

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
    /** Quiet time after the last word that ends a short command that doesn't parse yet. */
    private val idleMs: Long = 3_500,
    /** Quiet time that ends a dictated message (longer: people pause to think mid-message). */
    private val dictationIdleMs: Long = 4_000,
    private val noSpeechMs: Long = 10_000,
    private val log: (String) -> Unit = { Log.i("NexusVoice", it) },
) {
    var active: Boolean = false
        private set
    /** Sessions started in this dictation (1 = no restart yet). For tests and logs. */
    var sessions: Int = 0
        private set
    /** Why the last dictation ended (analytics: counts only). */
    var endReason: EndReason = EndReason.CANCELLED
        private set
    /** Recogniser error codes seen in the last dictation, quiet ones included (analytics: codes only). */
    val errorCodes = mutableListOf<Int>()
    /** [alternatives] as they were when the last dictation finished. */
    var finalAlternatives: List<String> = emptyList()
        private set

    private var session = 0
    private val pieces = mutableListOf<String>()
    private var partial = ""
    private var startedAt = 0L
    private var lastWordsAt = 0L
    private var recreated = 0
    private var emptyRuns = 0
    private var request = SpeechRequest("en", VoiceMode.ONLINE, longForm = false)
    private var isComplete: (List<String>) -> Boolean = { false }
    private var isDictation: (String) -> Boolean = { false }
    private var lastAlternatives: List<String> = emptyList()
    private var onText: (String) -> Unit = {}
    private var onDone: (String?) -> Unit = {}

    /** Everything heard so far, including the words of the session still running. */
    val text: String get() = join(pieces + partial)

    /**
     * Whole-text readings, best first: everything heard with each of the last session's hypotheses in turn
     * (the recogniser's n-best), so the parser can pick the one that makes sense ("पंकच" vs "पंकज").
     */
    val alternatives: List<String>
        get() {
            if (lastAlternatives.size < 2 || partial.isNotBlank()) return listOfNotNull(text.ifBlank { null })
            val before = pieces.dropLast(1)
            return lastAlternatives.map { join(before + it) }.filter { it.isNotBlank() }.distinct()
        }

    private fun join(parts: List<String>) = parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    fun start(
        language: String,
        mode: VoiceMode,
        longForm: Boolean,
        isComplete: (List<String>) -> Boolean = { false },
        isDictation: (String) -> Boolean = { longForm },
        biasing: List<String> = emptyList(),
        onText: (String) -> Unit,
        onDone: (String?) -> Unit,
    ) {
        cancel()
        this.request = SpeechRequest(language, mode, longForm, biasing = biasing)
        this.isComplete = isComplete
        this.isDictation = isDictation
        lastAlternatives = emptyList()
        this.onText = onText
        this.onDone = onDone
        pieces.clear()
        partial = ""
        recreated = 0
        emptyRuns = 0
        sessions = 0
        errorCodes.clear()
        endReason = EndReason.CANCELLED
        startedAt = now()
        lastWordsAt = startedAt
        active = true
        begin(restart = false)
    }

    /** The parent tapped Done: finish now with everything heard, including the running session's words. */
    fun done() {
        if (active) finish(EndReason.USER_DONE)
    }

    /** Stop / Back: end without a result. */
    fun cancel() {
        if (!active) return
        endReason = EndReason.CANCELLED
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

            override fun onFinal(texts: List<String>) {
                if (id == session && active) ended(texts.map { it.trim() }.filter { it.isNotEmpty() }, null)
            }

            override fun onError(code: Int) {
                if (id == session && active) ended(emptyList(), code)
            }
        })
    }

    private fun ended(finals: List<String>, error: Int?) {
        val words = finals.firstOrNull() ?: partial.trim()
        partial = ""
        if (words.isNotEmpty()) {
            pieces += words
            lastAlternatives = finals
            lastWordsAt = now()
            emptyRuns = 0
        } else {
            emptyRuns++
        }
        if (error != null) errorCodes += error
        val all = text
        val failed = if (all.isNotEmpty()) EndReason.CUT_OFF else EndReason.ERROR
        val thinking = endsWithJoiner(all)
        onText(all)
        log("session=$sessions ended error=$error words=${words.isNotEmpty()} chars=${all.length}")
        val t = now()
        when {
            error != null && error !in QUIET_ERRORS && error !in RECREATE_ERRORS -> return finish(failed)
            error in RECREATE_ERRORS -> {
                if (++recreated > 3) return finish(failed)
                engine.reset()
            }
            all.isNotEmpty() && !thinking && isComplete(alternatives) -> return finish(EndReason.COMPLETE)
        }
        val quietLimit = if (isDictation(all)) dictationIdleMs else idleMs
        when {
            t - startedAt >= maxMs -> finish(EndReason.MAX_TIME)
            all.isNotEmpty() && words.isEmpty() && !thinking && t - lastWordsAt >= quietLimit -> finish(EndReason.SILENCE)
            all.isEmpty() && t - startedAt >= noSpeechMs -> finish(EndReason.NO_SPEECH)
            emptyRuns >= 8 -> finish(if (all.isEmpty()) EndReason.NO_SPEECH else EndReason.SILENCE)
            else -> begin(restart = true)
        }
    }

    private fun finish(reason: EndReason) {
        endReason = reason
        val result = text.ifBlank { null }
        finalAlternatives = alternatives
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
            callbacks.onFinal(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
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
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Hints only ("may have no effect" per the docs, and Google's service often ignores them), which is
            // why ContinuousListener restarts and applies its own quiet-time rules (docs/voice-architecture.md).
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (request.longForm) 4_000L else 3_500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, if (request.longForm) 4_000L else 2_000L)
            .apply {
                if (request.mode == VoiceMode.ON_DEVICE) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && request.biasing.isNotEmpty()) {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(request.biasing.take(200)))
                }
            }
    }
}
