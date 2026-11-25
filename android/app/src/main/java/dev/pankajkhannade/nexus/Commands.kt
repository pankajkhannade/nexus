package dev.pankajkhannade.nexus

/**
 * What a spoken (or typed) command asks Nexus to do. [IntentParser] turns words into one of these and
 * [NexusViewModel.understood] acts on it, so a smarter parser (an on-device model later) can replace
 * [RuleIntentParser] without touching the screens.
 */
sealed interface Command {
    /** "पंकजला व्हॉट्सॲप कर आणि सांग की ..." */
    data class Message(val dictation: Dictation) : Command
    /** "ghar jana hai", "घरी जायचं आहे", "take me home". */
    data object RideHome : Command
    /** "स्वारगेटला जायचं आहे", "take me to Pune airport": [query] as said; [place] when it is a saved place (office). */
    data class RideTo(val query: String, val place: Place? = null) : Command
    /** "बेटे को फ़ोन लगाओ": a saved person. [exact] = the saved name or alias as said (skip the confirm). */
    data class Call(val person: Person, val exact: Boolean) : Command
    /** "whatsapp Pankaj" with nothing to say: opens the chat. */
    data class WhatsApp(val person: Person) : Command
    /** "सुनीलला फोन लाव" with no saved Sunil: look in the phone book. */
    data class FindContact(val name: String, val ask: Ask, val options: List<Person> = emptyList()) : Command
    /** "दूध मागव", "doctor", "ambulance": a Near home tile. */
    data class OpenNeed(val need: Need) : Command
    /** "हे मेसेज खरं आहे का", "is this message safe": open the check, never a draft. */
    data object CheckSafety : Command
    data object Unknown : Command
}

fun interface IntentParser {
    fun parse(said: String, saved: Saved): Command
}

/** The Phase 1 rules (Plan.kt, Dictation.kt, NearHome.kt), in the order that keeps calls and messages first. */
object RuleIntentParser : IntentParser {
    override fun parse(said: String, saved: Saved): Command {
        val text = said.trim()
        if (text.isEmpty()) return Command.Unknown
        if (isSafetyAsk(text)) return Command.CheckSafety
        readDictation(text, saved)?.let { dictated ->
            // "पंकजला व्हॉट्सॲप कर" with nothing to say opens the chat. A body still waits for Send.
            if (dictated.text.isBlank() && dictated.person != null && dictated.channel == Channel.WHATSAPP && dictated.options.isEmpty()) {
                return Command.WhatsApp(dictated.person)
            }
            return Command.Message(dictated)
        }
        val reading = readMessage(text, saved)
        val person = reading.person
        if (wantsRideHome(reading) && saved.home.isSet) return Command.RideHome
        if (person == null) {
            if (reading.trip?.dropoff?.kind == PlaceKind.OFFICE && reading.trip.pickup == null && saved.office.isSet) {
                return Command.RideTo(saved.office.address, saved.office)
            }
            // "hospital la jaycha" alone is the Doctor tile (nearby search); a named place is a ride.
            readDestination(text)?.takeIf { place -> place.contains(' ') || readNeed(place) == null }?.let { return Command.RideTo(it) }
        }
        val need = if (person == null && (reading.ask == null || reading.askedName?.let(::readNeed) != null)) readNeed(text) else null
        if (need != null && (need != Need.RIDE || saved.home.isSet)) return Command.OpenNeed(need)
        if (person == null && reading.options.size > 1 && reading.ask != null) {
            return Command.FindContact(reading.askedName ?: reading.options.first().name, reading.ask, reading.options)
        }
        if (person == null) {
            val asked = reading.askedName
            return if (asked != null && reading.ask != null) Command.FindContact(asked, reading.ask) else Command.Unknown
        }
        return if (reading.ask == Ask.MESSAGE) Command.WhatsApp(person) else Command.Call(person, reading.exact)
    }
}

/**
 * True when [said] is already a whole command, so the mic can stop without waiting for Done.
 * A message is never complete by itself: the parent may still be speaking it. That includes "पंकजला
 * व्हॉट्सॲप कर" with nothing said yet (it opens the chat only if the parent stops there), so a pause
 * after the name never cuts the message off.
 */
fun isCompleteCommand(parser: IntentParser, said: String, saved: Saved): Boolean =
    when (parser.parse(said, saved)) {
        is Command.Message, is Command.WhatsApp, Command.Unknown -> false
        else -> true
    }

/** Words that mean "check this", not "send this". A tell-word ("सांग", "tell") keeps it a message. */
private val SAFETY_WORDS = setOf(
    "safe", "unsafe", "scam", "fraud", "fake", "nakli", "nakali", "real", "khara", "khare", "khota", "khot",
    "सुरक्षित", "फसवणूक", "ठगी", "नकली", "खरा", "खरं", "खरे", "खोटा", "खोटं", "असली",
)

private val SAFETY_TELL = setOf(
    "tell", "सांग", "सांगा", "sang", "sanga", "call", "phone", "फोन", "कॉल", "whatsapp", "sms",
)

fun isSafetyAsk(said: String): Boolean {
    val words = said.lowercase().split(Regex("""[^\p{L}\p{M}]+""")).filter { it.isNotBlank() }
    if (words.none { it in SAFETY_WORDS }) return false
    if (words.any { it in SAFETY_TELL }) return false
    return true
}
