package dev.pankajkhannade.nexus

/** Family relation keys. The UI maps them to string resources. */
object Relation {
    const val SON = "son"
    const val DAUGHTER = "daughter"
    const val SPOUSE = "spouse"
    const val MAID = "maid"
    const val CARETAKER = "caretaker"
    const val OTHER = "other"
    val ALL = listOf(SON, DAUGHTER, SPOUSE, MAID, CARETAKER, OTHER)
}

/**
 * A saved person. [phone] is always E.164 (see [normalizePhone]).
 * [alias] is how the parent says the name ("Bablu", "पंकज"); [askFamily] marks who receives "Ask family".
 */
data class Person(
    val id: String,
    val name: String,
    val phone: String,
    val relation: String = "",
    val askFamily: Boolean = false,
    val alias: String = "",
    /** A face photo copied into the app's private files (faces/), or "" for a coloured initial. Never uploaded. */
    val photo: String = "",
    /**
     * UX sprint 2: the name in Devanagari ("पंकज") when the child typed one. Shown instead of [name] when the
     * app is in Marathi or Hindi, and matched by voice like [name]. "" = always show [name] (Latin is fine).
     */
    val localName: String = "",
)

/** The name to show in [lang]: the Devanagari one in mr / hi when the child entered it, else the saved name. */
fun Person.shownIn(lang: String): String = if (lang != "en" && hasDevanagari(localName)) localName.trim() else name.trim()

/** Every written form of this person's name that voice may match: saved name, Devanagari name, spoken alias. */
val Person.names: List<String> get() = listOf(name, localName, alias).filter { it.isNotBlank() }

fun hasDevanagari(text: String): Boolean = text.any { it in '\u0900'..'\u097F' }

/** UX sprint 2 (WCAG 2.2.1 Timing Adjustable): the action countdown, set in Family settings. */
val COUNTDOWN_CHOICES = listOf(3, 5, 8)
const val DEFAULT_COUNTDOWN = 5
fun cleanCountdown(seconds: Int): Int = if (seconds in COUNTDOWN_CHOICES) seconds else DEFAULT_COUNTDOWN

/** A saved place. Coordinates come from the on-device Geocoder at setup and may be missing. */
data class Place(val address: String = "", val lat: Double? = null, val lng: Double? = null) {
    val isSet: Boolean get() = address.isNotBlank()
    val hasCoordinates: Boolean get() = lat != null && lng != null
}

data class Saved(
    val setupDone: Boolean = false,
    /** Parent's language: "mr", "hi" or "en". */
    val language: String = "",
    /** Epoch day when setup finished; drives the day-3 / day-7 feedback card. */
    val installDay: Long = 0,
    val micAsked: Boolean = false,
    val callAsked: Boolean = false,
    /** The one-time "you tap Send in WhatsApp" line on the message screen has been shown and used. */
    val messageExplained: Boolean = false,
    val country: String = "",
    val dial: String = "",
    val countryCode: String = "",
    val lastCab: String = "",
    val home: Place = Place(),
    val office: Place = Place(),
    val people: List<Person> = emptyList(),
    /** Home PIN code (S15): from the address, the Geocoder, or typed by the child. Display and maps search only. */
    val pincode: String = "",
    /** S15: the app the child picked per need key ([Need.key] -> package, "" = none). Ride uses [lastCab]. */
    val services: Map<String, String> = emptyMap(),
    /**
     * The leave-behind voice check has been finished or skipped.
     * Missing on older saves means the child already left, so decode treats that as done.
     */
    val practiceDone: Boolean = true,
    /** Seconds before a call is placed, with Cancel on screen: 3, 5 or 8 (default 5). */
    val countdownSeconds: Int = DEFAULT_COUNTDOWN,
)

data class Country(val name: String, val dial: String, val code: String)

val COUNTRIES = listOf(
    Country("Australia", "+61", "AU"),
    Country("Bangladesh", "+880", "BD"),
    Country("Brazil", "+55", "BR"),
    Country("Canada", "+1", "CA"),
    Country("China", "+86", "CN"),
    Country("France", "+33", "FR"),
    Country("Germany", "+49", "DE"),
    Country("Hong Kong", "+852", "HK"),
    Country("India", "+91", "IN"),
    Country("Indonesia", "+62", "ID"),
    Country("Ireland", "+353", "IE"),
    Country("Italy", "+39", "IT"),
    Country("Japan", "+81", "JP"),
    Country("Kenya", "+254", "KE"),
    Country("Malaysia", "+60", "MY"),
    Country("Mexico", "+52", "MX"),
    Country("Nepal", "+977", "NP"),
    Country("Netherlands", "+31", "NL"),
    Country("New Zealand", "+64", "NZ"),
    Country("Nigeria", "+234", "NG"),
    Country("Pakistan", "+92", "PK"),
    Country("Philippines", "+63", "PH"),
    Country("Saudi Arabia", "+966", "SA"),
    Country("Singapore", "+65", "SG"),
    Country("South Africa", "+27", "ZA"),
    Country("South Korea", "+82", "KR"),
    Country("Spain", "+34", "ES"),
    Country("Sri Lanka", "+94", "LK"),
    Country("Sweden", "+46", "SE"),
    Country("Switzerland", "+41", "CH"),
    Country("Thailand", "+66", "TH"),
    Country("United Arab Emirates", "+971", "AE"),
    Country("United Kingdom", "+44", "GB"),
    Country("United States", "+1", "US"),
    Country("Vietnam", "+84", "VN"),
)

/**
 * Family words written in Devanagari or common Latin spellings map to one key, so a contact saved
 * as "Aai" matches a spoken "आई" and "Mummy" matches "mumma". Names in general are not transliterated
 * yet (Phase 1 voice work stores a spoken alias per person instead).
 */
private val RELATION_ALIASES: Map<String, String> = buildMap {
    fun alias(key: String, vararg forms: String) = forms.forEach { put(it, key) }
    alias("aai", "aai", "aayi", "aaee", "आई")
    alias("baba", "baba", "baaba", "बाबा")
    alias("papa", "papa", "paapa", "पापा")
    alias("mummy", "mummy", "mumma", "mummi", "मम्मी")
    alias("maa", "maa", "ma", "माँ", "मां", "मा")
    alias("aaji", "aaji", "aji", "आजी")
    alias("ajoba", "ajoba", "aajoba", "आजोबा")
    alias("didi", "didi", "दीदी", "दीदि")
    alias("tai", "tai", "taai", "ताई")
    alias("dada", "dada", "दादा")
    alias("bhaiya", "bhaiya", "bhaiyya", "bhaia", "भैया", "भय्या")
    alias("beta", "beta", "बेटा")
}

private fun canonical(word: String): String = RELATION_ALIASES[word] ?: word

fun nameMatches(candidate: String, asked: String): Boolean {
    val wanted = asked.trim().lowercase().split(Regex("""\s+""")).joinToString(" ") { canonical(it) }
    val have = candidate.trim().lowercase().split(Regex("""\s+""")).joinToString(" ") { canonical(it) }
    if (wanted.length < 2 || have.isEmpty()) return false
    if (have == wanted || have.startsWith("$wanted ")) return true
    val first = have.substringBefore(" ")
    // One slip of the speech recogniser ("Rahool" for "Rahul"). Callers treat this as a guess and confirm.
    return first == wanted || wanted.length >= 4 && !wanted.contains(' ') && editDistance(first, wanted) <= 1
}

internal fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        prev = cur
    }
    return prev[b.length]
}
