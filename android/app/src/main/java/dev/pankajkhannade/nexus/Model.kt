package dev.pankajkhannade.nexus

/** A saved person. [phone] is always E.164 (see [normalizePhone]). */
data class Person(val id: String, val name: String, val phone: String)

/** A saved place. Coordinates come from the on-device Geocoder at setup and may be missing. */
data class Place(val address: String = "", val lat: Double? = null, val lng: Double? = null) {
    val isSet: Boolean get() = address.isNotBlank()
    val hasCoordinates: Boolean get() = lat != null && lng != null
}

data class Saved(
    val setupDone: Boolean = false,
    val country: String = "",
    val dial: String = "",
    val countryCode: String = "",
    val lastCab: String = "",
    val contactsExplained: Boolean = false,
    val home: Place = Place(),
    val office: Place = Place(),
    val people: List<Person> = emptyList(),
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
    return have.substringBefore(" ") == wanted
}
