package dev.pankajkhannade.nexus

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "NexusRide"

/** What happened when Nexus handed a trip to an app. The UI turns this into a message. */
enum class RideResult { OPENED, OPENED_AND_COPIED, NOT_INSTALLED_OPENED_MAPS, NOTHING_TO_OPEN }

fun isInstalled(context: Context, pkg: String): Boolean =
    context.packageManager.getLaunchIntentForPackage(pkg) != null

/**
 * Opens [app] for [trip]. If the app is missing, opens the trip in Google Maps (app or browser).
 * Addresses the app can't receive in a link are copied to the clipboard first.
 */
fun openRide(context: Context, app: CabApp, trip: Trip): RideResult {
    val handoff = cabHandoff(app, trip)
    if (!isInstalled(context, handoff.pkg)) {
        Log.i(TAG, "${app.id} not installed; falling back to Maps")
        return if (openMaps(context, trip)) RideResult.NOT_INSTALLED_OPENED_MAPS else RideResult.NOTHING_TO_OPEN
    }
    handoff.copyText?.let { copyToClipboard(context, it) }
    val intent = if (handoff.uri != null) {
        Intent(Intent.ACTION_VIEW, Uri.parse(handoff.uri)).setPackage(handoff.pkg)
    } else {
        context.packageManager.getLaunchIntentForPackage(handoff.pkg)
    } ?: return RideResult.NOTHING_TO_OPEN
    Log.i(TAG, "open ${app.id} uri=${handoff.uri ?: "launcher"} copied=${handoff.copyText != null}")
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (handoff.copyText != null) RideResult.OPENED_AND_COPIED else RideResult.OPENED
    } catch (_: ActivityNotFoundException) {
        if (openMaps(context, trip)) RideResult.NOT_INSTALLED_OPENED_MAPS else RideResult.NOTHING_TO_OPEN
    }
}

/** Opens the trip in Google Maps, or any app that handles the https link. Returns false if nothing could. */
fun openMaps(context: Context, trip: Trip): Boolean {
    val link = mapsLink(trip) ?: return false
    Log.i(TAG, "open maps uri=$link")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("address", text))
}

/**
 * Looks up coordinates for a typed address with the phone's own Geocoder (no Nexus server).
 * Returns the place without coordinates when the phone has no geocoder or the lookup fails.
 */
@Suppress("DEPRECATION")
fun geocode(context: Context, address: String): Place {
    val text = address.trim()
    if (text.isEmpty() || !Geocoder.isPresent()) return Place(text)
    return runCatching {
        val hit = Geocoder(context).getFromLocationName(text, 1)?.firstOrNull()
        if (hit != null) Place(text, hit.latitude, hit.longitude) else Place(text)
    }.getOrElse { Place(text) }
}

fun openDial(context: Context, phone: String) {
    val uri = Uri.parse("tel:$phone")
    context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openSms(context: Context, phone: String, body: String) {
    val uri = Uri.parse("smsto:$phone")
    val intent = Intent(Intent.ACTION_SENDTO, uri)
        .putExtra("sms_body", body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** wa.me link for an E.164 number, optional prefilled text. Pure, so it is unit tested. */
fun whatsAppLink(phone: String, body: String): String {
    val base = "https://wa.me/${phoneDigits(phone)}"
    return if (body.isBlank()) base else "$base?text=${encodeParam(body)}"
}

/** Returns false when WhatsApp is not installed (the Play Store page is opened instead). */
fun openWhatsApp(context: Context, phone: String, body: String): Boolean {
    if (!isInstalled(context, "com.whatsapp")) {
        openPlay(context, "com.whatsapp")
        return false
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsAppLink(phone, body))).setPackage("com.whatsapp")
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        openPlay(context, "com.whatsapp")
        false
    }
}

private fun openPlay(context: Context, pkg: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Contacts matching [asked]. Numbers are normalised; ones that can't be are skipped. */
fun lookupContacts(context: Context, asked: String, dial: String): List<Person> {
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    val found = mutableListOf<Person>()
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
            val phone = if (phoneIdx >= 0) cursor.getString(phoneIdx).orEmpty() else ""
            val e164 = normalizePhone(phone, dial)
            if (nameMatches(name, asked) && e164 != null) {
                found += Person(id = phoneDigits(e164), name = name, phone = e164)
            }
            if (found.size == 8) break
        }
    }
    return found.distinctBy { it.phone }
}

/** Reads the contact the user picked in the system picker (no permission needed). Null if the number is unusable. */
fun readPickedPhone(context: Context, uri: Uri): Pair<String, String>? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val name = cursor.getString(0).orEmpty()
        val phone = cursor.getString(1).orEmpty()
        if (name.isBlank() || phone.isBlank()) return null
        return name to phone
    }
    return null
}

fun suggestPlaces(query: String, countryCode: String): List<String> {
    val url = URL(
        "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/suggest?f=json&maxSuggestions=5&text=" +
            URLEncoder.encode(query, "UTF-8") + "&countryCode=" + URLEncoder.encode(countryCode, "UTF-8"),
    )
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("Accept", "application/json")
    }
    connection.inputStream.bufferedReader().use { reader ->
        val array = JSONObject(reader.readText()).optJSONArray("suggestions") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.optString("text")?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct()
    }
}
