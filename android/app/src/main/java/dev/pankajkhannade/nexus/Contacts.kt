package dev.pankajkhannade.nexus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Phone-book calling and messaging: "सुनीलला फोन लाव" finds "Sunil Patil" in the phone's contacts.
 *
 * Contacts stay on the phone. Nexus reads them only after the parent (or the child in setup) allows
 * READ_CONTACTS, keeps an index in memory while the app is open, and never saves or uploads them.
 */
data class PhoneContact(
    val id: String,
    val name: String,
    val numbers: List<String>,
    val starred: Boolean = false,
    val timesContacted: Int = 0,
)

interface ContactsSource {
    fun hasPermission(): Boolean
    /** Every contact with a phone number. Runs off the main thread. */
    fun load(): List<PhoneContact>
}

class AndroidContactsSource(private val context: Context) : ContactsSource {
    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION") // TIMES_CONTACTED is 0 on Android 10+; used only to break ties where it still works.
    override fun load(): List<PhoneContact> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED,
        )
        val byId = LinkedHashMap<String, PhoneContact>()
        runCatching {
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1)?.trim().orEmpty()
                    val number = c.getString(2)?.trim().orEmpty()
                    if (name.isEmpty() || number.isEmpty()) continue
                    val starred = c.getInt(3) == 1
                    val times = c.getInt(4)
                    val old = byId[id]
                    byId[id] = if (old == null) PhoneContact(id, name, listOf(number), starred, times)
                    else old.copy(numbers = old.numbers + number, starred = old.starred || starred, timesContacted = maxOf(old.timesContacted, times))
                }
            }
        }
        return byId.values.toList()
    }
}

// ---- Matching ---------------------------------------------------------------------------------

/** Respect words and family titles said with a name ("Sharma ji", "Ramesh kaka"). Compared separately. */
private val HONORIFICS = setOf(
    "ji", "jee", "जी", "saheb", "sahab", "saab", "साहेब", "साहब", "sir", "सर", "madam", "मॅडम", "shri", "श्री", "smt", "mr", "mrs", "ms",
    "tai", "ताई", "kaka", "काका", "kaku", "काकू", "mama", "मामा", "mami", "मामी", "mavshi", "मावशी", "atya", "आत्या", "bhau", "भाऊ",
    "dada", "दादा", "didi", "दीदी", "bhai", "भाई", "bhaiya", "भैया", "anna", "अण्णा", "appa", "अप्पा", "aunty", "uncle", "अंकल", "आंटी",
    "chacha", "चाचा", "chachi", "चाची", "bua", "बुआ", "jiju", "जीजू", "dr", "डॉ",
)

private val NAME_SPLIT = Regex("""[^\p{L}\p{M}\p{N}]+""")
private val GLUED_HONORIFIC = listOf("जी", "ji")
private val CASE_ENDINGS = listOf("ला", "ना", "ले", "को", "ने", "शी", "la", "ko", "na", "ne")

private class NameKey(val words: List<String>, val titles: Set<String>)

/** Lowercase, Devanagari to Latin sounds ([soundKey]), titles set aside. "शर्माजी" -> words [sarm], titles [ji]. */
private fun nameKey(raw: String): NameKey {
    val words = mutableListOf<String>()
    val titles = mutableSetOf<String>()
    for (w in raw.lowercase().split(NAME_SPLIT).filter { it.isNotBlank() }) {
        if (w in HONORIFICS) { titles += soundKey(w); continue }
        val glued = GLUED_HONORIFIC.firstOrNull { w.length > it.length + 2 && w.endsWith(it) }
        if (glued != null) { titles += soundKey(glued); words += soundKey(w.dropLast(glued.length)); continue }
        words += soundKey(w)
    }
    // "Kaka" alone is the name.
    if (words.isEmpty()) return NameKey(titles.toList(), emptySet())
    return NameKey(words.filter { it.isNotEmpty() }, titles)
}

/** Jaro-Winkler similarity, 0..1. */
internal fun jaroWinkler(a: String, b: String): Double {
    if (a == b) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val window = maxOf(0, maxOf(a.length, b.length) / 2 - 1)
    val aHit = BooleanArray(a.length)
    val bHit = BooleanArray(b.length)
    var matches = 0
    for (i in a.indices) {
        for (j in maxOf(0, i - window)..minOf(b.length - 1, i + window)) {
            if (!bHit[j] && a[i] == b[j]) { aHit[i] = true; bHit[j] = true; matches++; break }
        }
    }
    if (matches == 0) return 0.0
    var t = 0
    var k = 0
    for (i in a.indices) {
        if (!aHit[i]) continue
        while (!bHit[k]) k++
        if (a[i] != b[k]) t++
        k++
    }
    val m = matches.toDouble()
    val jaro = (m / a.length + m / b.length + (m - t / 2.0) / m) / 3
    val prefix = a.zip(b).takeWhile { (x, y) -> x == y }.size.coerceAtMost(4)
    return jaro + prefix * 0.1 * (1 - jaro)
}

private fun wordScore(said: String, have: String): Double {
    if (said == have) return 1.0
    val jw = jaroWinkler(said, have)
    // "Shri" for "Shrikant" is a prefix, not a slip.
    return if (said.length >= 3 && have.startsWith(said)) maxOf(jw, 0.9) else jw
}

/** How well [said] fits a contact name: 1 = same sounds, plus 0.05 when the same family title was said. */
internal fun nameScore(said: String, contactName: String): Double {
    val q = nameKey(said)
    val c = nameKey(contactName)
    if (q.words.isEmpty() || c.words.isEmpty()) return 0.0
    val score = if (q.words.size == 1) {
        // One word: the first name counts fully, a surname slightly less ("Patil" is a weaker clue).
        c.words.mapIndexed { i, w -> wordScore(q.words[0], w) * if (i == 0) 1.0 else 0.97 }.max()
    } else {
        val used = mutableSetOf<Int>()
        val each = q.words.map { w ->
            val best = c.words.indices.filter { it !in used }.maxByOrNull { wordScore(w, c.words[it]) }
            if (best == null) 0.0 else { used += best; wordScore(w, c.words[best]) }
        }
        maxOf(each.average(), jaroWinkler(q.words.joinToString(""), c.words.joinToString("")))
    }
    // "Ramesh kaka" prefers the contact saved as "Ramesh Kaka" over "Ramesh Joshi" (can go above 1).
    val titleBonus = if (q.titles.isNotEmpty() && q.titles.intersect(c.titles).isNotEmpty()) 0.05 else 0.0
    return score + titleBonus
}

/** A phone-book contact turned into a callable person (not saved anywhere). */
fun PhoneContact.asPerson(phone: String) = Person(id = "contact:${phoneDigits(phone)}", name = name, phone = phone)

data class ContactMatch(val person: Person, val score: Double, val starred: Boolean, val timesContacted: Int)

sealed interface ContactLookup {
    /** One clear match. [exact] = the name as said (safe to go straight to the countdown). */
    data class Clear(val match: ContactMatch, val exact: Boolean) : ContactLookup
    /** Two or more close matches, or one unsure match: the parent picks (never auto-dialled). */
    data class Choose(val options: List<ContactMatch>) : ContactLookup
    data object NotFound : ContactLookup
}

const val CONTACT_STRONG = 0.92
const val CONTACT_MIN = 0.84
private const val CONTACT_CLOSE = 0.04

/** Spellings to try: as said, and without a case ending glued on ("सुनीलला" -> "सुनील"). */
private fun spokenForms(said: String): List<String> {
    val t = said.trim()
    val forms = mutableListOf(t)
    val last = t.substringAfterLast(' ')
    CASE_ENDINGS.firstOrNull { last.length > it.length + 2 && last.endsWith(it, ignoreCase = true) }?.let {
        forms += t.dropLast(it.length)
    }
    return forms
}

/**
 * Finds [said] in [contacts]. Scores every (contact, number) with [nameScore], drops numbers that can't be
 * dialled, merges the same number saved twice, and ranks by score, then starred, then times contacted.
 */
fun findContact(said: String, contacts: List<PhoneContact>, dial: String): ContactLookup {
    val forms = spokenForms(said)
    val ranked = contacts.flatMap { contact ->
        val score = forms.maxOf { nameScore(it, contact.name) }
        if (score < CONTACT_MIN) return@flatMap emptyList()
        contact.numbers.mapNotNull { contactNumber(it, dial) }.distinct().map { ContactMatch(contact.asPerson(it), score, contact.starred, contact.timesContacted) }
    }
        .sortedWith(compareByDescending<ContactMatch> { it.score }.thenByDescending { it.starred }.thenByDescending { it.timesContacted })
        .distinctBy { it.person.phone }
    val best = ranked.firstOrNull() ?: return ContactLookup.NotFound
    val close = ranked.filter { it.score >= best.score - CONTACT_CLOSE }
    if (close.size == 1 && best.score >= CONTACT_STRONG) {
        val exact = forms.any { f -> nameKey(f).words.let { w -> w.isNotEmpty() && w == nameKey(best.person.name).words.take(w.size) } } && best.score >= 0.999
        return ContactLookup.Clear(best, exact)
    }
    return ContactLookup.Choose(ranked.filter { it.score >= best.score - 0.1 }.take(3))
}

/**
 * A dialable number from the phone book: the normalised mobile number when it is one, else a landline
 * as saved ("020 2543 1234" -> "02025431234"), which the dialer handles. Too-short service codes are dropped.
 */
fun contactNumber(raw: String, dial: String): String? {
    normalizePhone(raw, dial)?.let { return it }
    val t = raw.trim()
    if (t.any { !it.isDigit() && it !in "+ -().\u00A0" } || t.lastIndexOf('+') > 0) return null
    val kept = t.filter { it.isDigit() || it == '+' }
    return kept.takeIf { k -> k.count(Char::isDigit) in 6..15 }
}

/** "…3210": the last four digits, to tell two Rahuls apart. */
fun numberEnding(phone: String): String = phoneDigits(phone).takeLast(4)
