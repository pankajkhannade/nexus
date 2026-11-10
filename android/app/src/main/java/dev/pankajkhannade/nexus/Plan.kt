package dev.pankajkhannade.nexus

/** What the user asked Nexus to do with a person. */
enum class Ask { CALL, MESSAGE }

enum class PlaceKind { HOME, OFFICE, ADDRESS }

/** One end of a ride. [place] may be unset when the user named Home/Office before saving it. */
data class Stop(val kind: PlaceKind, val place: Place)

/** A ride request. "Pick me up at home" has a pickup and no drop-off; "go home" has a drop-off. */
data class Trip(val pickup: Stop? = null, val dropoff: Stop? = null)

data class Reading(
    val ask: Ask? = null,
    val person: Person? = null,
    /** A name the user asked for that is not a saved person (search contacts for it). */
    val askedName: String? = null,
    /** An E.164 number found in the text that does not belong to a saved person. */
    val number: String? = null,
    val trip: Trip? = null,
    /** True when the spoken name equals the person's saved name or alias exactly (safe to skip the confirm step). */
    val exact: Boolean = false,
)

// Unicode-aware word edges. Java's \b breaks on Devanagari vowel signs, so use lookarounds.
private const val B = """(?<![\p{L}\p{M}\p{N}])"""
private const val E = """(?![\p{L}\p{M}\p{N}])"""
private const val OPT = "(?iu)"

private const val LATIN_NAME = """[A-Za-z][A-Za-z.'-]*(?:\s+[A-Za-z][A-Za-z.'-]*){0,2}"""
private const val DEV_NAME = """[\p{IsDevanagari}]+(?:\s+[\p{IsDevanagari}]+){0,2}"""

private const val CALL_WORD = """(?:call|phone|fone|fon|ring|dial|कॉल|फ़ोन|फोन)"""
private const val MSG_WORD = """(?:message|msg|text|sms|whatsapp|मैसेज|मेसेज|मेसेज|व्हॉट्सऍप|व्हाट्सएप)"""

/** English verb-first: "call Aai", "phone Pankaj now", "whatsapp Ravi". */
private val EN_CALL = Regex("""$OPT$B(?:call|phone|ring|dial)\s+(?:to\s+)?($LATIN_NAME)""")
private val EN_MSG = Regex("""$OPT$B(?:text|sms|message|msg|whatsapp)\s+(?:to\s+)?($LATIN_NAME)""")

/**
 * Hindi / Marathi / Hinglish, name first:
 * "Aai ko call karo", "Pankaj ko phone lagao", "Aai la phone lav", "Pankajla phone kar",
 * "पंकज को फ़ोन लगाओ", "पंकजला फोन लाव", "आईला फोन लाव".
 */
private val IN_CALL = Regex("""$OPT($LATIN_NAME|$DEV_NAME)\s*(?:ko|la|को|ला)\s+$CALL_WORD$E""")
private val IN_MSG = Regex("""$OPT($LATIN_NAME|$DEV_NAME)\s*(?:ko|la|को|ला)\s+$MSG_WORD$E""")

private val PHONE = Regex("""[+\d(][\d\s().+-]{8,20}\d""")
private val STREET = Regex(
    """(?i)\b\d{1,6}\s+(?:[A-Za-z0-9.'’]+\s+){0,6}(?:Street|St|Road|Rd|Nagar|Marg|Colony|Cross|Block|Sector|Lane|Avenue|Ave)\b\.?(?:\s*,?\s*(?:Apt|Apartment|Unit|Suite|Flat|#)\s*[A-Za-z0-9-]+)?""",
)

/** Words that are not names: pronouns ("call me at ..."), fillers, and verbs caught by a loose match. */
private val NOT_NAMES = setOf(
    "me", "him", "her", "them", "us", "you", "it", "back", "someone", "everyone",
    "please", "plz", "pls", "zara", "jara", "jaldi", "abhi", "ata", "aata", "ok", "okay", "hey", "nexus",
    "mere", "meri", "mera", "maza", "majha", "mazya", "majhya", "maajhya", "majhi", "mazi",
    "karo", "kar", "kara", "lagao", "lav", "lava", "जरा", "मेरे", "मेरी", "माझ्या", "माझी",
)
private val STOP_AFTER_NAME = Regex("""(?i)\s+(?:at|on|please|now|today|tomorrow|from|to|and|when|if|for|about|asap)(?:\s|$).*""")

fun readMessage(text: String, saved: Saved): Reading {
    val clean = text.trim()
    if (clean.isEmpty()) return Reading()

    val found = findAsk(clean)
    val resolved = found?.let { resolvePerson(it.names, saved.people) }
    val person = resolved?.first
    val asked = if (person == null) found?.names?.lastOrNull()?.let(::cleanName) else null

    val numberInText = PHONE.find(clean)?.value?.let { normalizePhone(it, saved.dial) }
    val byNumber = numberInText?.let { n -> saved.people.firstOrNull { it.phone == n } }
    val matched = person ?: byNumber

    return Reading(
        ask = found?.ask ?: if (numberInText != null) Ask.CALL else null,
        person = matched,
        askedName = if (matched == null && numberInText == null) asked else null,
        number = if (matched == null) numberInText else null,
        trip = readTrip(clean, saved),
        exact = resolved?.second == true,
    )
}

/** [names] holds name readings to try in order, e.g. ["Sheela", "Shee"] for "Sheela phone kar". */
private class Found(val ask: Ask, val names: List<String>)

private fun findAsk(text: String): Found? {
    // Name-first forms are checked first: "Aai ko call karo" must not read "karo" as the name.
    for ((pattern, ask) in listOf(IN_CALL to Ask.CALL, IN_MSG to Ask.MESSAGE)) {
        val match = pattern.find(text) ?: continue
        val group = match.groups[1] ?: continue
        val name = group.value
        val after = text.substring(group.range.last + 1)
        val attachedLa = after.startsWith("la", ignoreCase = true) || after.startsWith("ला")
        val names = if (attachedLa) listOf(name + after.take(2), name) else listOf(name)
        return Found(ask, names)
    }
    for ((pattern, ask) in listOf(EN_CALL to Ask.CALL, EN_MSG to Ask.MESSAGE)) {
        val match = pattern.find(text) ?: continue
        return Found(ask, listOf(match.groupValues[1]))
    }
    return null
}

/** Strips fillers and trailing clauses: "please Pankaj now" -> "Pankaj". Null for pronouns like "me". */
private fun cleanName(raw: String): String? {
    val cut = raw.replace(STOP_AFTER_NAME, "").trim().trim(',', '.', ' ')
    val words = cut.split(Regex("""\s+""")).filter { it.isNotBlank() && it.lowercase() !in NOT_NAMES }
    val name = words.joinToString(" ")
    return name.takeIf { it.length in 2..40 }
}

/** Relation words a parent may say instead of a name ("मुलाला फोन लाव" calls the person marked Son). */
private val RELATION_WORDS: Map<String, String> = buildMap {
    listOf("son", "beta", "bete", "mulga", "mula", "mulala", "बेटा", "बेटे", "मुलगा", "मुला").forEach { put(it, Relation.SON) }
    listOf("daughter", "beti", "mulgi", "muli", "mulila", "बेटी", "मुलगी", "मुली").forEach { put(it, Relation.DAUGHTER) }
    listOf("husband", "wife", "pati", "patni", "navra", "bayko", "पति", "पत्नी", "नवरा", "बायको", "अहो").forEach { put(it, Relation.SPOUSE) }
}

/**
 * Tries each reading, then each trailing sub-phrase ("please Pankaj" -> "Pankaj"), against the saved
 * name and the spoken alias, then relation words. Returns the person and whether the match was exact.
 */
private fun resolvePerson(readings: List<String>, people: List<Person>): Pair<Person, Boolean>? {
    for (raw in readings) {
        val name = cleanName(raw) ?: continue
        val words = name.split(" ")
        for (i in words.indices) {
            val candidate = words.drop(i).joinToString(" ")
            val wanted = candidate.trim().lowercase()
            people.firstOrNull { it.alias.isNotBlank() && it.alias.trim().lowercase() == wanted }?.let { return it to true }
            people.firstOrNull { it.name.trim().lowercase() == wanted }?.let { return it to true }
            people.firstOrNull { nameMatches(it.name, candidate) || (it.alias.isNotBlank() && nameMatches(it.alias, candidate)) }
                ?.let { return it to false }
        }
        RELATION_WORDS[words.last().lowercase()]?.let { relation ->
            people.filter { it.relation == relation }.singleOrNull()?.let { return it to false }
        }
    }
    return null
}

// ---- Rides -------------------------------------------------------------------------------

private const val HOME_WORDS = """(?:home|ghar|ghari|gharun|gharahun|घर|घरी|घरून|घराहून)"""
private const val OFFICE_WORDS = """(?:office|work|daftar|officehun|officeun|ऑफिस|ऑफ़िस|दफ्तर|ऑफिसहून|ऑफिसमधून)"""
private const val THE = """(?:the\s+|my\s+)?"""

private fun pickupPatterns(words: String) = listOf(
    Regex("""$OPT$B(?:pick\s*(?:me\s+)?up|pickup|collect\s+me)\s+(?:at|from)\s+$THE$words$E"""),
    Regex("""$OPT$B(?:pick\s+me|pick)\s+(?:at|from)\s+$THE$words$E"""),
    Regex("""$OPT${B}from\s+$THE$words$E"""),
    Regex("""$OPT$B$words\s+(?:se|से|varun|वरून|hun|हून)$E"""),
    Regex("""$OPT$B(?:gharun|gharahun|officehun|officeun|घरून|घराहून|ऑफिसहून|ऑफिसमधून)$E"""),
)

private fun dropoffPatterns(words: String) = listOf(
    Regex("""$OPT${B}to\s+$THE$words$E"""),
    Regex("""$OPT${B}drop\s+(?:me\s+)?(?:off\s+)?(?:at|to)\s+$THE$words$E"""),
    Regex("""$OPT$B(?:take\s+me|go|going|ride|head(?:ing)?)\s+(?:to\s+)?$THE$words$E"""),
    Regex("""$OPT$B$words\s+(?:jana|jaana|jaana\s+hai|jana\s+hai|chalo|le\s+chalo|tak|जाना|चलो|तक|जायचं|जायचे|जायला)$E"""),
    Regex("""$OPT$B(?:ghari|घरी)\s+(?:ja|jaycha|jaaycha|jayla|जा)$E"""),
)

private fun readTrip(text: String, saved: Saved): Trip? {
    var pickup: Stop? = null
    var dropoff: Stop? = null
    val mentioned = mutableListOf<Stop>()
    val wantsPickup = Regex("""(?i)\bpick""").containsMatchIn(text)

    for ((kind, words) in listOf(PlaceKind.HOME to HOME_WORDS, PlaceKind.OFFICE to OFFICE_WORDS)) {
        val stop = Stop(kind, if (kind == PlaceKind.HOME) saved.home else saved.office)
        when {
            pickup == null && pickupPatterns(words).any { it.containsMatchIn(text) } -> pickup = stop
            dropoff == null && dropoffPatterns(words).any { it.containsMatchIn(text) } -> dropoff = stop
            Regex("$OPT$B$words$E").containsMatchIn(text) -> mentioned += stop
        }
    }

    STREET.find(text)?.let { match ->
        val stop = Stop(PlaceKind.ADDRESS, Place(match.value.trim().trimEnd('.')))
        val before = text.substring(0, match.range.first).takeLast(30)
        val isPickup = Regex("""(?i)(?:from|pick\s*(?:me\s+)?up\s+(?:at|from)|pickup\s+(?:at|from))\s*$""").containsMatchIn(before)
        if (isPickup && pickup == null) pickup = stop else if (dropoff == null) dropoff = stop else mentioned += stop
    }

    for (stop in mentioned) {
        when {
            wantsPickup && pickup == null -> pickup = stop
            dropoff == null && stop != pickup -> dropoff = stop
            pickup == null && stop != dropoff -> pickup = stop
        }
    }
    if (pickup == null && dropoff == null) return null
    return Trip(pickup, dropoff)
}

/** "Ghar jana hai" / "घरी जायचं आहे" / "take me home" with no person named: open Ride home (S10). */
fun wantsRideHome(reading: Reading): Boolean = reading.person == null && reading.trip?.dropoff?.kind == PlaceKind.HOME
