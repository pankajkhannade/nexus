package dev.pankajkhannade.nexus

data class Person(val id: String, val name: String, val phone: String)

data class Saved(
    val setupDone: Boolean = false,
    val tourDone: Boolean = false,
    val country: String = "",
    val dial: String = "",
    val countryCode: String = "",
    val themeDark: Boolean = false,
    val lastCab: String = "",
    val contactsExplained: Boolean = false,
    val home: String = "",
    val office: String = "",
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

fun phoneProblem(phone: String, dial: String, strict: Boolean): String? {
    val trimmed = phone.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.any { it.isLetter() } || trimmed.any { !it.isDigit() && it !in "+() .-" }) {
        return "Use digits only."
    }
    val digits = trimmed.filter { it.isDigit() }
    val india = dial == "+91" && !trimmed.startsWith("+") && !trimmed.startsWith("00")
    if (india) {
        val local = digits.removePrefix("0")
        if (!strict && local.length < 10) return null
        if (local.length != 10) return "India numbers are 10 digits."
        if (local.first() !in '6'..'9') return "Indian mobile numbers start with 6, 7, 8, or 9."
        return null
    }
    if (!strict && digits.length < 8) return null
    if (digits.length !in 8..15) return "Use 8 to 15 digits."
    return null
}

fun international(phone: String, dial: String): String {
    val trimmed = phone.trim()
    val digits = trimmed.filter { it.isDigit() }
    if (trimmed.startsWith("+")) return "+$digits"
    if (trimmed.startsWith("00")) return "+${digits.removePrefix("00")}"
    val code = dial.filter { it.isDigit() }
    if (code.isEmpty() || digits.length !in 8..11) return trimmed
    if (digits.startsWith(code)) return "+$digits"
    return "+$code$digits"
}

fun nameMatches(candidate: String, asked: String): Boolean {
    val wanted = asked.trim().lowercase()
    val have = candidate.trim().lowercase()
    if (wanted.length < 2 || have.isEmpty()) return false
    if (have == wanted || have.startsWith("$wanted ")) return true
    return have.substringBefore(" ") == wanted
}
