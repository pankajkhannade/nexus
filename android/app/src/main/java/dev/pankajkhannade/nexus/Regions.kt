package dev.pankajkhannade.nexus

/**
 * Everything Nexus needs to know about a country, in one place (docs/i18n-architecture.md, the "region pack").
 * Today there is one pack, India; the code paths that used to hard-code India now read it from here, so a second
 * country is a new [RegionPack] value plus its strings, not a hunt through the code.
 */
data class RegionPack(
    /** ISO 3166-1 alpha-2, as the SIM and Saved.countryCode report it. */
    val countryCode: String,
    /** ISO 3166-1 alpha-3, as the ArcGIS geocoder wants it ("IND"). */
    val countryCode3: String,
    /** Ambulance / emergency number for the Near home Emergency tile (dial pad only). */
    val emergencyNumber: String,
    /** Cyber-fraud helpline for "Money gone" (dial pad only), or null where the country has none. */
    val fraudHelpline: String?,
    /** The official online fraud complaint site, or null. */
    val fraudPortal: String?,
    /** The country's bounding box, so a geocoder never returns a same-named place abroad. */
    val bounds: Bounds,
    /** A postal code written in an address (India: 6-digit PIN, first digit not 0). */
    val postalCode: Regex,
    /** Ride apps offered on the Ride screen, most used first. */
    val cabApps: List<CabApp>,
)

data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lng: Double) = lat in south..north && lng in west..east
}

val INDIA = RegionPack(
    countryCode = "IN",
    countryCode3 = "IND",
    emergencyNumber = "108",
    // India's National Cyber Crime Reporting Portal and its helpline (both run by the Ministry of Home Affairs, I4C).
    fraudHelpline = "1930",
    fraudPortal = "https://cybercrime.gov.in",
    bounds = Bounds(south = 6.5, west = 68.0, north = 37.2, east = 97.5),
    postalCode = Regex("""(?<!\d)([1-9]\d{2})\s?(\d{3})(?!\d)"""),
    cabApps = CabApp.entries.toList(),
)

/** Packs Nexus ships. India is the only one until a second market is chosen (see docs/vision.md). */
val REGION_PACKS: List<RegionPack> = listOf(INDIA)

/**
 * The pack for a saved country code. Address search, the scam flow and rides stay on India for any country
 * today (the product is India-first); only the emergency number already follows the country (112 elsewhere).
 */
fun regionFor(countryCode: String): RegionPack = REGION_PACKS.firstOrNull { it.countryCode == countryCode } ?: INDIA

/** The GSM-standard emergency number that works on almost every mobile network outside India's 108. */
const val GLOBAL_EMERGENCY = "112"
