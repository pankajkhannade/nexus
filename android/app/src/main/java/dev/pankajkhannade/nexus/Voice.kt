package dev.pankajkhannade.nexus

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors

/** BCP-47 tag for a parent language key ("mr" -> "mr-IN"). */
fun speechTag(language: String): String = when (language) {
    "mr" -> "mr-IN"
    "hi" -> "hi-IN"
    else -> "en-IN"
}

fun localeOf(language: String): Locale = Locale.forLanguageTag(speechTag(language))

/** A context whose resources are in [language], for parent-language text inside child-facing screens. */
fun Context.inLanguage(language: String): Context {
    val config = android.content.res.Configuration(resources.configuration)
    config.setLocale(localeOf(language))
    return createConfigurationContext(config)
}

/** Text to speech in the parent's language. Silent (text still shows) when the phone has no voice for it. */
class Speaker(context: Context) {
    private var ready = false
    private var initDone = false
    private var pending: Pair<String, String>? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        initDone = true
        Log.i("NexusVoice", "tts ready=$ready status=$status")
        pending?.let { (lang, text) -> speak(lang, text) }
        pending = null
    }

    /** LANG_AVAILABLE or better means the voice is installed. */
    /** While the engine is still starting we assume yes (speech is queued); afterwards it is a real check. */
    fun hasVoice(language: String): Boolean =
        !initDone || ready && tts.isLanguageAvailable(localeOf(language)) >= TextToSpeech.LANG_AVAILABLE

    fun speak(language: String, text: String) {
        if (!ready) {
            pending = language to text
            return
        }
        val result = tts.setLanguage(localeOf(language))
        Log.i("NexusVoice", "speak lang=$language setLanguage=$result text=$text")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexus")
    }

    fun stop() = tts.stop()

    fun shutdown() = tts.shutdown()
}

/** Where the voice is turned into text, so the screen can say so plainly. */
enum class VoiceMode { ON_DEVICE, ONLINE, UNAVAILABLE }

/** In-app speech recognition (not the Google dialog), in the parent's language. */
class Listener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Checks whether [language] is installed for on-device recognition (Android 13+); otherwise ONLINE. */
    fun checkMode(language: String, onResult: (VoiceMode) -> Unit) {
        if (!available()) return onResult(VoiceMode.UNAVAILABLE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return onResult(VoiceMode.ONLINE)
        }
        val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val tag = speechTag(language)
        probe.checkRecognitionSupport(
            recognizeIntent(language),
            Executors.newSingleThreadExecutor(),
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val local = support.installedOnDeviceLanguages.any { it.equals(tag, true) }
                    android.os.Handler(context.mainLooper).post {
                        probe.destroy()
                        onResult(if (local) VoiceMode.ON_DEVICE else VoiceMode.ONLINE)
                    }
                }

                override fun onError(error: Int) {
                    android.os.Handler(context.mainLooper).post {
                        probe.destroy()
                        onResult(VoiceMode.ONLINE)
                    }
                }
            },
        )
    }

    fun start(language: String, mode: VoiceMode, onPartial: (String) -> Unit, onDone: (String?) -> Unit) {
        stop()
        val r = if (mode == VoiceMode.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                onDone(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }

            override fun onError(error: Int) {
                Log.i("NexusVoice", "recognizer error=$error")
                onDone(null)
            }
        })
        r.startListening(recognizeIntent(language))
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun recognizeIntent(language: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag(language))
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
}
