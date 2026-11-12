package dev.pankajkhannade.nexus

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** BCP-47 tag for a parent language key ("mr" -> "mr-IN"), from [PARENT_LANGUAGES]. */
fun speechTag(language: String): String = parentLanguage(language).speechTag

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
