package dev.pankajkhannade.nexus

/** Why a typed phone number can't be saved yet. The UI maps each value to a string resource. */
enum class PhoneProblem { DIGITS_ONLY, INDIA_LENGTH, INDIA_START, LENGTH }

private val SEPARATORS = setOf(' ', '-', '(', ')', '.', '\u00A0')

/**
 * The single phone normaliser. Every save path (setup, edit screen, contacts picker, stored data)
 * goes through here. Returns an E.164 number such as "+919876543210", or null when [raw] is not a
 * usable number for the given country dial code (e.g. "+91").
 *
 * India rules: 10 digits starting 6-9, optionally written with +91, 91, 0, 091 or 0091 in front.
 */
fun normalizePhone(raw: String, dial: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.any { !it.isDigit() && it != '+' && it !in SEPARATORS }) return null
    if (trimmed.lastIndexOf('+') > 0) return null
    val digits = trimmed.filter { it.isDigit() }
    val code = dial.filter { it.isDigit() }

    if (trimmed.startsWith("+") || digits.startsWith("00")) {
        val full = if (trimmed.startsWith("+")) digits else digits.removePrefix("00")
        return validInternational(full)
    }
    if (code == "91") return indianMobile(digits)?.let { "+91$it" }
    if (code.isEmpty()) return null
    val local = digits.removePrefix("0")
    if (local.startsWith(code) && local.length >= code.length + 8) return validInternational(local)
    return validInternational(code + local)
}

private fun validInternational(digits: String): String? {
    if (digits.length !in 8..15 || digits.startsWith("0")) return null
    if (digits.startsWith("91")) return indianMobile(digits)?.let { "+91$it" }
    return "+$digits"
}

/** Returns the 10-digit Indian mobile number inside [digits], or null. */
private fun indianMobile(digits: String): String? {
    val local = when {
        digits.length == 10 -> digits
        digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        digits.length == 13 && digits.startsWith("091") -> digits.drop(3)
        else -> return null
    }
    return local.takeIf { it.first() in '6'..'9' }
}

/** Explains why [raw] would not normalise, or null when it is fine (or still being typed and [strict] is false). */
fun phoneProblem(raw: String, dial: String, strict: Boolean): PhoneProblem? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.any { !it.isDigit() && it != '+' && it !in SEPARATORS }) return PhoneProblem.DIGITS_ONLY
    if (normalizePhone(trimmed, dial) != null) return null
    val digits = trimmed.filter { it.isDigit() }
    val india = dial == "+91" && !trimmed.startsWith("+") && !digits.startsWith("00")
    if (!strict && digits.length < (if (india) 10 else 8)) return null
    if (india) {
        val local = digits.removePrefix("0").let { if (it.length == 12 && it.startsWith("91")) it.drop(2) else it }
        return if (local.length == 10) PhoneProblem.INDIA_START else PhoneProblem.INDIA_LENGTH
    }
    return PhoneProblem.LENGTH
}

/**
 * Repairs numbers saved by older builds: 10-digit Indian numbers starting with 91 were stored as
 * "+91xxxxxxxx" (country code lost), and the edit screen saved raw local numbers. Unfixable values
 * are returned unchanged so nothing is silently deleted.
 */
fun repairStoredPhone(stored: String, dial: String): String {
    val trimmed = stored.trim()
    val digits = trimmed.filter { it.isDigit() }
    if (dial == "+91" && trimmed.startsWith("+") && digits.length == 10 && digits.first() in '6'..'9') {
        return "+91$digits"
    }
    return normalizePhone(trimmed, dial) ?: stored
}

/** Digits only, the form wa.me and tel: links need ("919876543210"). */
fun phoneDigits(e164: String): String = e164.filter { it.isDigit() }

/** "+91 98765 43210" for Indian numbers, otherwise the E.164 string. */
fun displayPhone(e164: String): String {
    val digits = phoneDigits(e164)
    return if (e164.startsWith("+91") && digits.length == 12) {
        "+91 ${digits.substring(2, 7)} ${digits.substring(7)}"
    } else {
        e164
    }
}
