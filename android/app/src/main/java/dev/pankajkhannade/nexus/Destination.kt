package dev.pankajkhannade.nexus

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale

/**
 * Rides to anywhere: "स्वारगेटला जायचं आहे", "mujhe station jana hai", "take me to Pune airport",
 * "cab book kar Kothrud la". [readDestination] finds the place words; [PlaceFinder] turns them into up to
 * 3 places near home to confirm; the Ride screen hands the chosen one to the cab app.
 */

private val DEST_WORD = Regex("""[\p{L}\p{M}\p{N}'’.&-]+""")

/** "Go" verbs that end a place phrase: Marathi, Hindi, Hinglish. */
private val GO_VERBS = setOf(
    "जायचं", "जायचे", "जायचंय", "जायचय", "जायचा", "जायची", "जायला", "जाऊ", "जायचंय", "सोड", "सोडा", "सोडून", "पोहोचव", "पोहचव",
    "jaycha", "jaaycha", "jaych", "jaychay", "jaaychay", "jaychy", "jayche", "jaayche", "jaychi", "jayla", "jaayla", "sod", "soda",
    "जाना", "जाऊँ", "जाऊं", "चलो", "चलना", "चलिए", "पहुँचा", "पहुंचा", "छोड़", "छोड़ो", "छोड़", "छोड़ो",
    "jana", "jaana", "janaa", "jaanaa", "chalo", "chalna", "chaliye", "chhodo", "chodo", "pahuncha", "pohcha",
)

/** English (and Hinglish) words before "to"/"at" + place. */
private val EN_LEAD = setOf("take", "go", "going", "drop", "ride", "head", "heading", "reach", "travel", "book", "cab", "taxi", "auto", "rickshaw", "uber", "ola", "rapido")

/** Vehicles and booking words, dropped from the place phrase. */
private val VEHICLE = setOf(
    "rickshaw", "riksha", "rikshaw", "auto", "cab", "taxi", "car", "gaadi", "gadi", "ride", "uber", "ola", "rapido", "bus",
    "रिक्षा", "रिक्शा", "ऑटो", "कॅब", "कैब", "टॅक्सी", "टैक्सी", "गाडी", "गाड़ी", "उबर", "ओला", "रॅपिडो",
    "book", "बुक", "kar", "karo", "kara", "kardo", "कर", "करा", "करो", "please", "plz", "pls", "zara", "jara", "जरा", "now", "abhi", "ata", "आता", "अभी",
    "se", "ne", "ni", "से", "ने", "नी", "ane", "ani", "and", "a", "an",
)

/** Words that start a place phrase but aren't the place: "मला", "mujhe", "I want to". */
private val LEAD_FILLERS = setOf(
    "मला", "mala", "amhala", "आम्हाला", "mujhe", "मुझे", "hume", "humein", "humko", "हमें", "mereko", "मेरेको", "i", "we", "want", "need", "have",
    "to", "me", "us", "please", "plz", "ok", "okay", "nexus", "hey", "मी", "mi", "mai", "main", "मैं", "मेरे", "maza", "majha",
    "आज", "aaj", "उद्या", "udya", "kal", "कल", "आत्ता", "atta", "abhi", "अभी", "lavkar", "लवकर", "jaldi", "जल्दी",
)

/** Case endings and particles after the place: "Kothrud la", "station tak", "airport ko", "मंडईपर्यंत". */
private val TRAIL = setOf(
    "la", "ला", "ko", "को", "tak", "तक", "paryant", "पर्यंत", "kade", "कडे", "var", "वर", "pe", "पे", "par", "पर", "mein", "में", "madhe", "मध्ये",
    "sathi", "साठी", "ke", "liye", "के", "लिए", "aahe", "ahe", "आहे", "hai", "है", "le", "ले", "mala", "मला", "mujhe", "मुझे", "the", "my", "at", "to",
)

private val GLUED_TRAIL = listOf("पर्यंत", "ला", "कडे", "मध्ये")

/** Words that turn the sentence into a call or a message, never a ride. */
private val NOT_A_RIDE = setOf(
    "call", "phone", "fone", "ring", "dial", "कॉल", "फोन", "फ़ोन", "sang", "sanga", "सांग", "सांगा", "bol", "bolo", "बोल", "बोलो", "tell",
    "message", "msg", "sms", "text", "whatsapp", "मेसेज", "मैसेज", "व्हॉट्सॲप", "व्हाट्सएप", "kaho", "कहो", "bata", "batao", "बता", "बताओ",
)

private val HOME_OFFICE = setOf("home", "ghar", "ghari", "घर", "घरी", "office", "work", "daftar", "ऑफिस", "ऑफ़िस", "दफ्तर", "ऑफिसला", "घराला")

private class W(val text: String) { val low = text.lowercase(Locale.ROOT) }

private fun stripGlued(word: String): String {
    if (word.none { it in '\u0900'..'\u097F' }) return word
    val end = GLUED_TRAIL.firstOrNull { word.length > it.length + 2 && word.endsWith(it) } ?: return word
    return word.dropLast(end.length)
}

private fun cleanPlace(words: List<W>): String? {
    var list = words.filterNot { it.low in VEHICLE }
    list = list.dropWhile { it.low in LEAD_FILLERS || it.low in TRAIL }
    list = list.dropLastWhile { it.low in TRAIL }
    if (list.isEmpty()) return null
    val joined = list.mapIndexed { i, w -> if (i == list.lastIndex) stripGlued(w.text) else w.text }.joinToString(" ").trim()
    if (joined.length < 2 || joined.lowercase(Locale.ROOT) in HOME_OFFICE) return null
    if (stripGlued(list.last().text).lowercase(Locale.ROOT) in HOME_OFFICE && list.size == 1) return null
    return joined
}

/**
 * The place a parent wants a ride to, as said ("दीनानाथ हॉस्पिटल", "Pune airport"), or null when the
 * sentence is not a ride somewhere other than home or office. Calls and messages are never rides.
 */
fun readDestination(said: String): String? {
    val words = DEST_WORD.findAll(said.trim()).map { W(it.value.trim('.', '\'', '’', '-')) }.filter { it.text.isNotEmpty() }.toList()
    if (words.isEmpty() || words.any { it.low in NOT_A_RIDE }) return null

    // English: "take me to X", "go to X", "drop me at X", "book a cab to X", "ride to Kothrud".
    val to = words.indices.firstOrNull { i ->
        (words[i].low == "to" || words[i].low == "at" || words[i].low == "till") && i > 0 &&
            words.subList(0, i).any { it.low in EN_LEAD }
    }
    if (to != null) return cleanPlace(words.drop(to + 1))

    // Name first, then "go": "स्वारगेटला जायचं आहे", "mujhe station jana hai", "Phoenix mall jaana hai rickshaw".
    val go = words.indexOfFirst { it.low in GO_VERBS }
    if (go > 0) return cleanPlace(words.subList(0, go))

    // Vehicle first, place last with a case ending: "cab book kar Kothrud la", "rickshaw Swargate tak".
    val first = words.first().low
    val last = words.last()
    if (first in VEHICLE && first !in setOf("se", "ne", "ani", "and", "a", "an") && words.size >= 2 &&
        (last.low in setOf("la", "ला", "tak", "तक", "ko", "को", "paryant", "पर्यंत") || stripGlued(last.text) != last.text)
    ) return cleanPlace(words.drop(1))

    // Place first, vehicle after: "Kothrud la rickshaw book kar", "स्टेशनला रिक्षा".
    val vehicleAt = words.indexOfFirst { it.low in VEHICLE && it.low !in setOf("se", "ne", "ani", "and", "a", "an", "kar", "karo", "kara", "कर", "करा", "करो", "please", "now") }
    if (vehicleAt > 0) {
        val before = words.subList(0, vehicleAt)
        val marker = before.last()
        // "दीनानाथ अस्पताल के लिए कैब बुक करो", "station ke liye auto".
        if (marker.low in setOf("लिए", "liye", "lie") && before.size >= 3 && before[before.size - 2].low in setOf("के", "ke")) return cleanPlace(before.dropLast(2))
        if (marker.low in setOf("la", "ला", "tak", "तक", "ko", "को", "sathi", "साठी") || stripGlued(marker.text) != marker.text) return cleanPlace(before)
    }
    return null
}

// ---- Finding the place --------------------------------------------------------------------------

/** Turns place words into up to 3 real places near home. A seam so tests can fake the Geocoder. */
fun interface PlaceFinder {
    /** Runs off the main thread. Empty when nothing was found (the cab app can still search the words). */
    fun find(query: String, near: Place, language: String): List<Place>
}

/** About 50 km around home, in degrees. */
private const val NEAR_KM = 50.0

/** A short readable name for a Geocoder hit: "Deenanath Mangeshkar Hospital, Erandwane". */
fun placeLabel(a: Address): String {
    val line = a.getAddressLine(0).orEmpty()
    val feature = a.featureName?.takeIf { f -> f.isNotBlank() && f.any { it.isLetter() } }
    val area = listOf(a.subLocality, a.thoroughfare, a.locality).firstOrNull { !it.isNullOrBlank() && it != feature }
    return when {
        feature != null -> listOfNotNull(feature, area).joinToString(", ")
        line.isNotBlank() -> line.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
        else -> listOfNotNull(area).joinToString(", ")
    }
}

/**
 * Android's own Geocoder (free, no key; fair for addresses, weaker for landmarks). Searches a box of about
 * 50 km around home first, then anywhere, and keeps results within about 100 km of home when home is known.
 */
class AndroidPlaceFinder(private val context: Context) : PlaceFinder {
    @Suppress("DEPRECATION")
    override fun find(query: String, near: Place, language: String): List<Place> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, localeOf(language))
        fun lookup(q: String, bounded: Boolean): List<Address> = runCatching {
            if (bounded && near.hasCoordinates) {
                val dLat = NEAR_KM / 111.0
                val dLng = NEAR_KM / (111.0 * Math.cos(Math.toRadians(near.lat!!))).coerceAtLeast(1.0)
                geocoder.getFromLocationName(q, 3, near.lat - dLat, near.lng!! - dLng, near.lat + dLat, near.lng + dLng)
            } else {
                // Never abroad: a "Shivaji Park" search must not come back from another country.
                geocoder.getFromLocationName(q, 3, IndiaBox.SOUTH, IndiaBox.WEST, IndiaBox.NORTH, IndiaBox.EAST)
            }.orEmpty()
        }.getOrDefault(emptyList())
        val spoken = query.trim()
        val latin = if (spoken.any { it in '\u0900'..'\u097F' }) spoken.split(" ").joinToString(" ") { romanize(it) } else null
        var hits = lookup(spoken, bounded = true)
        if (hits.isEmpty() && latin != null) hits = lookup(latin, bounded = true)
        if (hits.isEmpty()) {
            hits = (lookup(spoken, bounded = false).ifEmpty { latin?.let { lookup(it, bounded = false) }.orEmpty() })
                .filter { a -> !near.hasCoordinates || distanceKm(near.lat!!, near.lng!!, a.latitude, a.longitude) <= 2 * NEAR_KM }
        }
        return hits.filter { it.hasLatitude() && it.hasLongitude() && IndiaBox.contains(it.latitude, it.longitude) }
            .map { Place(placeLabel(it).ifBlank { spoken }, it.latitude, it.longitude) }
            .distinctBy { it.address }
            .take(3)
    }
}

internal fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}
