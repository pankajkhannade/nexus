package dev.pankajkhannade.nexus

import java.net.URLEncoder
import java.util.Locale

enum class CabApp(val id: String, val label: String, val pkg: String) {
    UBER("uber", "Uber", "com.ubercab"),
    // Play package checked 2026-11-10: "com.olacabs" is 404 on Play; the live Ola app is com.olacabs.customer.
    OLA("ola", "Ola", "com.olacabs.customer"),
    RAPIDO("rapido", "Rapido", "com.rapido.passenger"),
    NAMMA_YATRI("namma_yatri", "Namma Yatri", "in.juspay.nammayatri");

    companion object {
        fun from(id: String): CabApp? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How to hand a trip to a cab app.
 * - [uri]: an http(s) link to open, or null to launch the app with its launcher intent.
 * - [copyText]: an address to put on the clipboard because the app can't receive it in a link.
 */
data class Handoff(val app: CabApp, val pkg: String, val uri: String?, val copyText: String?)

/** Query-safe encoding with %20 for spaces (some deep-link parsers don't turn '+' back into a space). */
fun encodeParam(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private fun coord(value: Double): String = String.format(Locale.US, "%.6f", value)

/**
 * Builds the handoff for one cab app. Only documented parameters are used:
 * - Uber universal link (developer.uber.com/docs/riders/ride-requests/tutorials/deep-links):
 *   action=setPickup, pickup=my_location or pickup[latitude|longitude|formatted_address],
 *   dropoff[latitude|longitude|formatted_address]. Coordinates are required for a prefilled stop,
 *   so a stop without them is copied to the clipboard instead.
 * - Ola, Rapido and Namma Yatri have no public deep-link format for a destination, so Nexus opens the app and
 *   copies the address for the user to paste.
 */
fun cabHandoff(app: CabApp, trip: Trip): Handoff {
    val pickup = trip.pickup?.place?.takeIf { it.isSet }
    val dropoff = trip.dropoff?.place?.takeIf { it.isSet }
    if (app != CabApp.UBER) {
        return Handoff(app, app.pkg, uri = null, copyText = (dropoff ?: pickup)?.address)
    }
    val params = mutableListOf("action=setPickup")
    var copy: String? = null
    if (pickup != null && pickup.hasCoordinates) {
        params += "pickup[latitude]=${coord(pickup.lat!!)}"
        params += "pickup[longitude]=${coord(pickup.lng!!)}"
        params += "pickup[formatted_address]=${encodeParam(pickup.address)}"
    } else {
        params += "pickup=my_location"
        if (pickup != null) copy = pickup.address
    }
    if (dropoff != null && dropoff.hasCoordinates) {
        params += "dropoff[latitude]=${coord(dropoff.lat!!)}"
        params += "dropoff[longitude]=${coord(dropoff.lng!!)}"
        params += "dropoff[formatted_address]=${encodeParam(dropoff.address)}"
    } else if (dropoff != null) {
        copy = dropoff.address
    }
    return Handoff(app, app.pkg, "https://m.uber.com/ul/?" + params.joinToString("&"), copy)
}

private fun mapsPoint(place: Place): String =
    if (place.hasCoordinates) "${coord(place.lat!!)},${coord(place.lng!!)}" else encodeParam(place.address)

/** Google Maps URL (works in the Maps app or any browser): directions to the drop-off, or the pickup spot. */
fun mapsLink(trip: Trip): String? {
    val pickup = trip.pickup?.place?.takeIf { it.isSet }
    val dropoff = trip.dropoff?.place?.takeIf { it.isSet }
    return when {
        dropoff != null -> buildString {
            append("https://www.google.com/maps/dir/?api=1&destination=").append(mapsPoint(dropoff))
            if (pickup != null) append("&origin=").append(mapsPoint(pickup))
        }
        pickup != null -> "https://www.google.com/maps/search/?api=1&query=" + mapsPoint(pickup)
        else -> null
    }
}

/** Cab apps for the Ride home screen: the last-used one (or the first installed) first, then the rest. */
fun rideOrder(installed: List<CabApp>, lastId: String): List<CabApp> {
    val first = installed.firstOrNull { it.id == lastId } ?: installed.firstOrNull() ?: return emptyList()
    return listOf(first) + installed.filter { it != first }
}
