package dev.pankajkhannade.nexus

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
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

/**
 * Places the call directly when CALL_PHONE is granted (after the 3-second Cancel on screen),
 * otherwise opens the dialer with the number filled in. Returns true when the call was placed directly.
 */
fun placeCall(context: Context, phone: String): Boolean {
    val direct = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    Log.i("NexusCall", "placeCall direct=$direct")
    if (direct) {
        try {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: Exception) {
            // Fall through to the dialer (e.g. no SIM or a work profile restriction).
        }
    }
    openDial(context, phone)
    return false
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

/** The "Ask family" WhatsApp text: the message quoted, then the question in the parent's language. */
fun askFamilyText(message: String, question: String): String {
    val quoted = message.trim().take(600)
    return if (quoted.isEmpty()) question else "\"$quoted\"\n\n$question"
}

/**
 * Sends text to one family member: WhatsApp chat with the text prefilled (parent presses Send),
 * else an SMS draft. Returns false only if neither could open.
 */
fun askFamilyByText(context: Context, phone: String, text: String): Boolean {
    if (openWhatsAppQuiet(context, phone, text)) return true
    return try {
        Log.i("NexusAsk", "WhatsApp missing; SMS draft")
        openSms(context, phone, text)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun openWhatsAppQuiet(context: Context, phone: String, text: String): Boolean {
    if (!isInstalled(context, "com.whatsapp")) return false
    val link = whatsAppLink(phone, text)
    Log.i("NexusAsk", "open whatsapp ${link.substringBefore('?')}")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Shares a screenshot to WhatsApp (the parent picks the chat; WhatsApp has no documented way to
 * target a number with an image), or to the system share sheet when WhatsApp is missing.
 */
fun askFamilyWithImage(context: Context, file: File, text: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val intent = if (isInstalled(context, "com.whatsapp")) send.setPackage("com.whatsapp") else Intent.createChooser(send, null)
    Log.i("NexusAsk", "share image whatsapp=${isInstalled(context, "com.whatsapp")}")
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Country from the SIM, then the phone's region, else India. */
fun guessCountry(context: Context): Country {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
    val iso = tm?.simCountryIso?.uppercase().orEmpty().ifBlank { java.util.Locale.getDefault().country.uppercase() }
    return COUNTRIES.firstOrNull { it.code == iso } ?: COUNTRIES.first { it.code == "IN" }
}

/** WhatsApp, then WhatsApp Business. Both open wa.me links. */
val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

fun whatsAppPackage(context: Context): String? = WHATSAPP_PACKAGES.firstOrNull { isInstalled(context, it) }

/** Opens the chat with [phone] in [pkg] with [text] typed in. The parent taps Send; nothing is sent from Nexus. */
fun openWhatsAppText(context: Context, pkg: String, phone: String, text: String): Boolean {
    val link = whatsAppLink(phone, text)
    Log.i("NexusMessage", "open $pkg ${link.substringBefore('?')} chars=${text.length}")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Opens the SMS app with [text] typed in (no SEND_SMS permission: the parent taps Send). */
fun openSmsDraft(context: Context, phone: String, text: String): Boolean = try {
    Log.i("NexusMessage", "open sms chars=${text.length}")
    openSms(context, phone, text)
    true
} catch (_: ActivityNotFoundException) {
    false
}
