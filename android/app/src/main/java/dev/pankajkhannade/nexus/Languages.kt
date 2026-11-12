package dev.pankajkhannade.nexus

/**
 * The parent languages Nexus ships, in one registry (docs/i18n-architecture.md, step 1 of the language-pack plan).
 * Adding a language starts here: its code, its own name, the speech locale for the recogniser and TTS, and its
 * script. Strings (values-<code>/strings.xml), res/xml/locales_config.xml and the parser word lists must follow;
 * `LanguagesTest` fails when the registry and the resources disagree.
 */
data class ParentLanguage(
    /** BCP-47 language subtag, also the Android resource qualifier: "mr" -> values-mr. */
    val code: String,
    /** The language's name in itself, shown on the language picker. */
    val nativeName: String,
    /** BCP-47 tag for SpeechRecognizer and TextToSpeech, e.g. "mr-IN". */
    val speechTag: String,
    val script: Script,
)

/** Writing systems Nexus can tell apart. A local display name is shown only when it is in the language's script. */
enum class Script(private val range: CharRange?) {
    LATIN(null),
    DEVANAGARI('\u0900'..'\u097F');

    /** True when [text] has at least one letter of this script (always false for LATIN: it's the fallback). */
    fun isIn(text: String): Boolean = range != null && text.any { it in range }
}

/** Marathi first (the Pune beachhead), then Hindi and English. Order is the picker order. */
val PARENT_LANGUAGES: List<ParentLanguage> = listOf(
    ParentLanguage("mr", "मराठी", "mr-IN", Script.DEVANAGARI),
    ParentLanguage("hi", "हिंदी", "hi-IN", Script.DEVANAGARI),
    ParentLanguage("en", "English", "en-IN", Script.LATIN),
)

/** The language setup starts in before the child picks one. */
const val DEFAULT_LANGUAGE = "mr"

private val ENGLISH = PARENT_LANGUAGES.first { it.code == "en" }

/** The registry entry for [code]; English for an unknown or blank code (the same fallback as before). */
fun parentLanguage(code: String): ParentLanguage = PARENT_LANGUAGES.firstOrNull { it.code == code } ?: ENGLISH
