package dev.pankajkhannade.nexus

/**
 * The safety guard in front of every outgoing message or share Nexus prepares, single or multi-step
 * (2026-11-12). Deterministic, on the phone, and independent of how the request was understood, so a smarter
 * planner later can't switch it off. Lessons from the LFM3 test: a model drafted "My ATM PIN is 4590" to the
 * son, guessed "Sunil Patil" for "Call Sunil", and added unrequested location shares.
 */
enum class GuardReason(val key: String) {
    /** An OTP, PIN, CVV or password together with the code itself ("my ATM PIN 4590"). */
    SECRET_CODE("secret_code"),
    /** A card- or Aadhaar-length number (12–19 digits). */
    LONG_NUMBER("long_number"),
    /** A location link the parent didn't say. */
    LOCATION("location"),
    /** The name matched more than one person, or only roughly: asked, never guessed. */
    NAME_UNSURE("name_unsure");

    companion object {
        fun from(key: String): GuardReason? = entries.firstOrNull { it.key == key }
    }
}

private const val L = """(?<![\p{L}\p{M}\p{N}])"""
private const val R = """(?![\p{L}\p{M}\p{N}])"""

/** Words for a secret code, en / hi / mr, Latin and Devanagari. "PIN code" (the postal code) is not one. */
private val SECRET_WORD = Regex(
    """(?iu)$L(?:otp|o\.t\.p|one[\s-]?time[\s-]?password|(?:atm|upi|card|debit|credit|m)?[\s-]?pin(?!\s*-?\s*code)|mpin|cvv|cvc|password|passcode|""" +
        """ओटीपी|ओ\.टी\.पी|पिन(?!\s*कोड)|पासवर्ड|सीवीवी|गुप्त\s*(?:नंबर|क्रमांक))$R""",
)
/** A run of 3–8 digits (spaces or dashes allowed between digits), the shape of an OTP, PIN or CVV. */
private val CODE_DIGITS = Regex("""(?<![\d])\d(?:[\s-]?\d){2,7}(?![\d])""")
/** Devanagari digits count too. */
private fun latinDigits(text: String) = text.map { if (it in '०'..'९') '0' + (it - '०') else it }.joinToString("")
/** 12–19 digits in a row (card, Aadhaar), allowing spaces or dashes in groups. */
private val LONG_DIGITS = Regex("""(?<![\d])\d(?:[\s-]?\d){11,18}(?![\d])""")
private val LOCATION_LINK = Regex("""(?iu)(?:maps\.google\.|google\.[a-z.]+/maps|maps\.app\.goo\.gl|goo\.gl/maps|geo:-?\d|wa\.me/\?.*location|📍)""")

/** What the guard decided about one outgoing text. */
data class GuardCheck(val reason: GuardReason?) {
    val blocked: Boolean get() = reason != null
}

/**
 * Checks a message the parent is about to send. [said] is what the parent actually dictated or typed: a location
 * link is fine only if it was in their own words.
 */
fun guardMessage(text: String, said: String = text): GuardCheck {
    val t = latinDigits(text)
    if (SECRET_WORD.containsMatchIn(t) && CODE_DIGITS.containsMatchIn(t)) return GuardCheck(GuardReason.SECRET_CODE)
    if (LONG_DIGITS.findAll(t).any { m -> !looksLikePhone(m.value) }) return GuardCheck(GuardReason.LONG_NUMBER)
    if (LOCATION_LINK.containsMatchIn(text) && !LOCATION_LINK.containsMatchIn(said)) return GuardCheck(GuardReason.LOCATION)
    return GuardCheck(null)
}

/** "+91 98765 43210" is a phone number (12 digits starting 91), not an Aadhaar number. */
private fun looksLikePhone(value: String): Boolean {
    val d = value.filter(Char::isDigit)
    return d.length == 12 && d.startsWith("91") && d[2] in '6'..'9'
}

/**
 * For shares that forward someone else's text to family (Ask family with a suspicious SMS): the child sees the
 * message, but never the code in it. "Your OTP is 482113" -> "Your OTP is ••••••".
 */
fun maskSecrets(text: String): String {
    var out = latinDigits(text)
    if (SECRET_WORD.containsMatchIn(out)) out = CODE_DIGITS.replace(out) { m -> "•".repeat(m.value.count(Char::isDigit)) }
    out = LONG_DIGITS.replace(out) { m -> if (looksLikePhone(m.value)) m.value else "•".repeat(m.value.count(Char::isDigit)) }
    return if (out == latinDigits(text)) text else out
}
