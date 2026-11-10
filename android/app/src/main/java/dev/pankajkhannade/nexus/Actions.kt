package dev.pankajkhannade.nexus

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

fun openCab(context: Context, which: String, address: String): String? {
    val encoded = Uri.encode(address)
    val (uri, pkg) = when (which) {
        "ola" -> "olacabs://app/launch?landing_page=bk&drop_name=$encoded" to "com.olacabs"
        "rapido" -> "rapido://app?drop_address=$encoded" to "com.rapido.passenger"
        else -> "https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[formatted_address]=$encoded" to "com.ubercab"
    }
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        openPlay(context, pkg)
        "$which is not installed. Play Store is open."
    }
}

fun openMaps(context: Context, address: String) {
    val uri = "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(address)}"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openDial(context: Context, phone: String) {
    val uri = Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")
    context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openSms(context: Context, phone: String, body: String) {
    val uri = Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}")
    val intent = Intent(Intent.ACTION_SENDTO, uri)
        .putExtra("sms_body", body.ifBlank { "On my way." })
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openWhatsApp(context: Context, phone: String, body: String): String? {
    val digits = phone.filter { it.isDigit() }
    val text = URLEncoder.encode(body.ifBlank { "On my way." }, "UTF-8")
    val uri = "https://wa.me/$digits?text=$text"
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        openPlay(context, "com.whatsapp")
        "WhatsApp is not installed."
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

fun lookupContacts(context: Context, asked: String): List<Person> {
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
            if (nameMatches(name, asked) && phone.isNotBlank()) {
                found += Person(id = phone.filter { it.isDigit() }, name = name, phone = phone)
            }
            if (found.size == 8) break
        }
    }
    return found.distinctBy { it.phone.filter { ch -> ch.isDigit() } }
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
