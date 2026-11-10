package dev.pankajkhannade.nexus

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.Locale

/**
 * S15 Near home: the parent's everyday needs, as big tiles in their language. Each need maps to one
 * app the child picked at setup (or maps search for Doctor, Medicines and Emergency).
 * Nexus never checks whether an app delivers to the pincode: no app offers a public API for that.
 */
enum class Need(val key: String, val event: Event) {
    GROCERY("grocery", Event.NEAR_GROCERY),
    MEDICINE("medicine", Event.NEAR_MEDICINE),
    DOCTOR("doctor", Event.NEAR_DOCTOR),
    RIDE("ride", Event.NEAR_RIDE),
    HOME_SERVICE("home_service", Event.NEAR_HOME_SERVICE),
    EMERGENCY("emergency", Event.NEAR_EMERGENCY);

    companion object {
        fun from(key: String): Need? = entries.firstOrNull { it.key == key }

        /** Needs the child picks an app for, in screen order. Emergency has no app. */
        val PICKABLE = listOf(GROCERY, MEDICINE, DOCTOR, RIDE, HOME_SERVICE)
    }
}

data class ServiceApp(val label: String, val pkg: String)

/**
 * Top Indian apps per need, roughly by popularity in India (Play install tiers, Nov 2026).
 * Every package was checked on 2026-11-10 by fetching https://play.google.com/store/apps/details?id=<pkg>
 * (HTTP 200 with the app's title; see docs/decisions.md). Apps not live on Play (Housejoy, unpublished
 * April 2022) are left out. Flipkart Minutes and Amazon Now/Fresh live inside the main Flipkart and
 * Amazon apps; Swiggy Instamart is both its own app and a tab in Swiggy.
 */
val SERVICE_CATALOG: Map<Need, List<ServiceApp>> = mapOf(
    Need.GROCERY to listOf(
        ServiceApp("Blinkit", "com.grofers.customerapp"),
        ServiceApp("Zepto", "com.zeptoconsumerapp"),
        ServiceApp("Swiggy Instamart", "in.swiggy.android.instamart"),
        ServiceApp("Swiggy", "in.swiggy.android"),
        ServiceApp("BigBasket", "com.bigbasket.mobileapp"),
        ServiceApp("JioMart", "com.jpl.jiomart"),
        ServiceApp("Flipkart Minutes", "com.flipkart.android"),
        ServiceApp("Amazon Now / Fresh", "in.amazon.mShop.android.shopping"),
    ),
    Need.MEDICINE to listOf(
        ServiceApp("Tata 1mg", "com.aranoah.healthkart.plus"),
        ServiceApp("PharmEasy", "com.phonegap.rxpal"),
        ServiceApp("Apollo 24|7", "com.apollo.patientapp"),
        ServiceApp("Netmeds", "com.NetmedsMarketplace.Netmeds"),
        ServiceApp("Truemeds", "com.intellihealth.truemeds"),
    ),
    Need.DOCTOR to listOf(
        ServiceApp("Practo", "com.practo.fabric"),
        ServiceApp("Apollo 24|7", "com.apollo.patientapp"),
        ServiceApp("Tata 1mg", "com.aranoah.healthkart.plus"),
    ),
    Need.RIDE to CabApp.entries.map { ServiceApp(it.label, it.pkg) },
    Need.HOME_SERVICE to listOf(
        ServiceApp("Urban Company", "com.urbanclap.urbanclap"),
        ServiceApp("Snabbit", "com.snabbit.customer"),
        ServiceApp("Pronto", "com.company.pronto"),
    ),
)

fun appsFor(need: Need): List<ServiceApp> = SERVICE_CATALOG[need].orEmpty()

fun serviceApp(need: Need, pkg: String): ServiceApp? = appsFor(need).firstOrNull { it.pkg == pkg }

/** The app preselected in setup: the first installed one in popularity order, else none (""). */
fun defaultPick(need: Need, installed: (String) -> Boolean): String = appsFor(need).firstOrNull { installed(it.pkg) }?.pkg.orEmpty()

/** Setup default for every pickable need. */
fun defaultPicks(installed: (String) -> Boolean): Map<String, String> = Need.PICKABLE.associate { it.key to defaultPick(it, installed) }

// ---- Pincode ---------------------------------------------------------------------------------

private val PIN = Regex("""(?<!\d)([1-9]\d{2})\s?(\d{3})(?!\d)""")

/** An Indian PIN code (6 digits, first not 0) written in [address], e.g. "Baner, Pune 411045" -> "411045". */
fun pincodeIn(address: String): String? = PIN.findAll(address).lastOrNull()?.let { it.groupValues[1] + it.groupValues[2] }

/** A PIN typed by the child, cleaned, or null when it isn't 6 digits. */
fun cleanPincode(raw: String): String? = raw.filter { it.isDigit() }.takeIf { it.length == 6 && it[0] != '0' }

/** PIN for the home: written in the address first, then the Geocoder's postal code. */
fun homePincode(address: String, geocoderPostal: String?): String =
    pincodeIn(address) ?: geocoderPostal?.let(::cleanPincode).orEmpty()

// ---- Voice -----------------------------------------------------------------------------------

private val WORD_SPLIT = Regex("""[^\p{L}\p{M}\p{N}]+""")

/** Whole words only (short or ambiguous). */
private val NEED_EXACT: Map<Need, Set<String>> = mapOf(
    Need.EMERGENCY to setOf("108", "112", "emergency", "ambulance", "एम्बुलेंस", "ॲम्बुलन्स", "अ‍ॅम्बुलन्स", "एम्ब्युलन्स", "इमरजेंसी", "आणीबाणी", "bachao", "बचाओ", "वाचवा"),
    Need.DOCTOR to setOf("dr", "doctor", "doctors", "daktar", "dactor", "clinic", "hospital", "डॉक्टर", "डॉक्टरांना", "डॉक्टरकडे", "डॉक्टरला", "डाक्टर", "दवाखाना", "दवाखान्यात", "क्लिनिक", "हॉस्पिटल", "अस्पताल", "रुग्णालय", "dawakhana", "davakhana", "aspatal"),
    Need.MEDICINE to setOf("medicine", "medicines", "medical", "pharmacy", "chemist", "tablet", "tablets", "dawai", "dawa", "davai", "dava", "aushadh", "aushadhe", "ausadh", "goli", "golya", "दवा", "दवाई", "दवाइयाँ", "दवाइयां", "दवाएं", "औषध", "औषधे", "औषधं", "गोळ्या", "गोळी", "गोली", "मेडिकल", "1mg", "pharmeasy"),
    Need.GROCERY to setOf("grocery", "groceries", "milk", "vegetables", "sabzi", "sabji", "bhaji", "doodh", "dudh", "kirana", "rashan", "ration", "saman", "saamaan", "दूध", "दुध", "किराणा", "किराना", "भाजी", "सब्जी", "राशन", "रेशन", "सामान", "blinkit", "zepto", "instamart", "bigbasket", "jiomart"),
    Need.HOME_SERVICE to setOf("plumber", "electrician", "carpenter", "mistri", "repair", "repairs", "cleaning", "maid", "ac", "safai", "durusti", "urban", "प्लंबर", "प्लम्बर", "इलेक्ट्रिशियन", "इलेक्ट्रीशियन", "बिजलीवाला", "सुतार", "बढ़ई", "मिस्त्री", "दुरुस्ती", "मरम्मत", "सफाई", "स्वच्छता", "कामवाली", "घरकाम", "snabbit", "pronto"),
    Need.RIDE to setOf("rickshaw", "riksha", "auto", "cab", "taxi", "ola", "uber", "rapido", "रिक्षा", "रिक्शा", "ऑटो", "कॅब", "कैब", "टॅक्सी", "टैक्सी"),
)

/** Words that mean the sentence is about a person or a message, not a need ("Pankaj la sang..."). */
private val MESSAGE_OR_CALL = setOf("sang", "sanga", "सांग", "सांगा", "bol", "bolo", "बोल", "बोलो", "बता", "batao", "बताओ", "tell", "message", "msg", "whatsapp", "sms")

/**
 * The need a parent asked for, or null. Checked in this order so "ambulance" beats "hospital" and
 * "medical store" means medicines. Callers run it only after calls, messages and "go home" missed.
 */
fun readNeed(said: String): Need? {
    val words = said.lowercase(Locale.ROOT).split(WORD_SPLIT).filter { it.isNotBlank() }
    if (words.isEmpty() || words.any { it in MESSAGE_OR_CALL }) return null
    for (need in listOf(Need.EMERGENCY, Need.MEDICINE, Need.DOCTOR, Need.GROCERY, Need.HOME_SERVICE, Need.RIDE)) {
        val set = NEED_EXACT[need].orEmpty()
        if (words.any { it in set }) return need
    }
    return null
}

// ---- Hand-offs -------------------------------------------------------------------------------

private fun coord(value: Double): String = String.format(Locale.US, "%.6f", value)

/**
 * Maps search for [query] around home. With coordinates: geo:lat,lng?q=query (Google Maps intents,
 * developers.google.com/maps/documentation/urls/android-intents). Without: the query plus the PIN or address.
 */
fun nearbyGeoUri(query: String, home: Place, pincode: String): String = when {
    home.hasCoordinates -> "geo:${coord(home.lat!!)},${coord(home.lng!!)}?q=" + encodeParam(query)
    else -> "geo:0,0?q=" + encodeParam(nearText(query, home, pincode))
}

/** Same search as a Maps URL, for phones with no app that handles geo: links. */
fun nearbyWebUri(query: String, home: Place, pincode: String): String = when {
    home.hasCoordinates -> "https://www.google.com/maps/search/?api=1&query=" + encodeParam(query) +
        "&center=${coord(home.lat!!)},${coord(home.lng!!)}"
    else -> "https://www.google.com/maps/search/?api=1&query=" + encodeParam(nearText(query, home, pincode))
}

private fun nearText(query: String, home: Place, pincode: String): String = when {
    pincode.isNotBlank() -> "$query near $pincode"
    home.isSet -> "$query near ${home.address}"
    else -> "$query near me"
}

/** English map queries: Google Maps matches these best for Indian listings. */
fun mapsQuery(need: Need): String = when (need) {
    Need.MEDICINE -> "medical store"
    Need.DOCTOR -> "doctor clinic"
    Need.EMERGENCY -> "hospital"
    Need.GROCERY -> "grocery store"
    Need.HOME_SERVICE -> "plumber electrician"
    Need.RIDE -> "auto rickshaw stand"
}

fun openNearby(context: Context, query: String, home: Place, pincode: String): Boolean {
    val geo = nearbyGeoUri(query, home, pincode)
    Log.i("NexusNear", "maps q=$query coords=${home.hasCoordinates} pin=${pincode.isNotBlank()}")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(nearbyWebUri(query, home, pincode))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

/** Opens [pkg] with its launcher intent. False when it isn't installed (show the Install screen). */
fun launchApp(context: Context, pkg: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    Log.i("NexusNear", "launch $pkg")
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** The emergency number for the phone's country: 108 (ambulance) in India, else 112. */
fun emergencyNumber(countryCode: String): String = if (countryCode.isBlank() || countryCode == "IN") "108" else "112"

/** Tiles the parent sees, in order. Doctor, Medicines and Emergency always work (maps / dial); the rest need a pick. */
fun visibleNeeds(saved: Saved): List<Need> = Need.entries.filter { need ->
    when (need) {
        Need.MEDICINE, Need.DOCTOR, Need.EMERGENCY -> true
        Need.RIDE -> saved.home.isSet
        Need.GROCERY, Need.HOME_SERVICE -> saved.services[need.key].orEmpty().let { pkg -> pkg.isNotBlank() && serviceApp(need, pkg) != null }
    }
}
