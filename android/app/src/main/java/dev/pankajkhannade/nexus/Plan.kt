package dev.pankajkhannade.nexus

data class Reading(
    val placeLabel: String? = null,
    val place: String? = null,
    val person: Person? = null,
    val askedName: String? = null,
    val ask: String? = null,
)

private val STREET = Regex(
    """(?i)\b\d{1,6}\s+(?:[A-Za-z0-9.'’]+\s+){0,6}(?:Street|St|Road|Rd|Nagar|Marg|Colony|Cross|Block|Sector|Lane|Avenue|Ave)\b\.?(?:\s*,?\s*(?:Apt|Apartment|Unit|Suite|Flat|#)\s*[A-Za-z0-9-]+)?""",
)
private val CALL = Regex("""(?i)\b(?:call|phone|ring|dial)\s+([A-Za-z][A-Za-z .'-]{0,40})""")
private val TEXT = Regex("""(?i)\b(?:text|sms|message|whatsapp)\s+([A-Za-z][A-Za-z .'-]{0,40})""")
private val PHONE = Regex("""[+\d(][\d\s().+-]{8,20}\d""")

fun readMessage(text: String, saved: Saved): Reading {
    val clean = text.trim()
    if (clean.isEmpty()) return Reading()
    val callName = nameAfter(CALL, clean)
    val textName = nameAfter(TEXT, clean)
    val asked = callName ?: textName
    val ask = when {
        callName != null -> "call"
        textName != null -> "text"
        else -> null
    }
    val person = saved.people.firstOrNull { asked != null && nameMatches(it.name, asked) }
    val phoneInText = PHONE.find(clean)?.value?.trim()
    val matched = person ?: phoneInText?.let { raw ->
        val digits = raw.filter { it.isDigit() }
        saved.people.firstOrNull { it.phone.filter { ch -> ch.isDigit() }.endsWith(digits.takeLast(10)) }
    }
    val nick = nickname(clean, saved)
    val street = STREET.find(clean)?.value?.trim()?.trimEnd('.')
    val placeLabel = nick?.first ?: street
    val place = nick?.second ?: street
    return Reading(
        placeLabel = placeLabel,
        place = place,
        person = matched,
        askedName = if (matched == null) asked else null,
        ask = ask,
    )
}

private fun nameAfter(pattern: Regex, text: String): String? {
    val raw = pattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val cut = raw.split(Regex("""(?i)\b(?:at|on|please|now|today|tomorrow|from|to)\b"""))[0].trim().trim(',', '.', ' ')
    return cut.takeIf { it.length in 2..40 }
}

private fun nickname(text: String, saved: Saved): Pair<String, String>? {
    val toOffice = Regex("""(?i)\bto\s+(?:the\s+)?(?:office|work)\b""").containsMatchIn(text)
    val toHome = Regex("""(?i)\bto\s+(?:the\s+)?home\b""").containsMatchIn(text)
    val mentionsOffice = Regex("""(?i)\b(?:office|work)\b""").containsMatchIn(text)
    val mentionsHome = Regex("""(?i)\bhome\b""").containsMatchIn(text)
    return when {
        toOffice && saved.office.isNotBlank() -> "Office" to saved.office
        toHome && saved.home.isNotBlank() -> "Home" to saved.home
        mentionsOffice && !mentionsHome && saved.office.isNotBlank() -> "Office" to saved.office
        mentionsHome && !mentionsOffice && saved.home.isNotBlank() -> "Home" to saved.home
        else -> null
    }
}
