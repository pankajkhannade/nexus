package dev.pankajkhannade.nexus

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.nexusData by preferencesDataStore("nexus")
private val STATE = stringPreferencesKey("state")

class NexusStore(context: Context) {
    private val app = context.applicationContext

    suspend fun read(): Saved {
        val raw = app.nexusData.data.first()[STATE] ?: return Saved()
        return decode(raw)
    }

    suspend fun write(saved: Saved) {
        app.nexusData.edit { prefs -> prefs[STATE] = encode(saved) }
    }
}

private fun encode(saved: Saved): String {
    val people = JSONArray()
    saved.people.forEach { person ->
        people.put(JSONObject().put("id", person.id).put("name", person.name).put("phone", person.phone))
    }
    return JSONObject()
        .put("setupDone", saved.setupDone)
        .put("country", saved.country)
        .put("dial", saved.dial)
        .put("countryCode", saved.countryCode)
        .put("lastCab", saved.lastCab)
        .put("contactsExplained", saved.contactsExplained)
        .put("home", saved.home.address)
        .putCoordinates("home", saved.home)
        .put("office", saved.office.address)
        .putCoordinates("office", saved.office)
        .put("people", people)
        .toString()
}

private fun decode(raw: String): Saved {
    val json = JSONObject(raw)
    val peopleJson = json.optJSONArray("people") ?: JSONArray()
    val dial = json.optString("dial")
    val people = (0 until peopleJson.length()).mapNotNull { index ->
        val item = peopleJson.optJSONObject(index) ?: return@mapNotNull null
        Person(
            id = item.optString("id"),
            name = item.optString("name"),
            // Older builds saved some numbers without the country code; repair on read.
            phone = repairStoredPhone(item.optString("phone"), dial),
        )
    }
    return Saved(
        setupDone = json.optBoolean("setupDone"),
        country = json.optString("country"),
        dial = dial,
        countryCode = json.optString("countryCode"),
        lastCab = json.optString("lastCab"),
        contactsExplained = json.optBoolean("contactsExplained"),
        home = json.readPlace("home"),
        office = json.readPlace("office"),
        people = people,
    )
}

private fun JSONObject.putCoordinates(key: String, place: Place): JSONObject {
    if (place.lat != null && place.lng != null) {
        put("${key}Lat", place.lat)
        put("${key}Lng", place.lng)
    }
    return this
}

private fun JSONObject.readPlace(key: String): Place {
    val lat = optDouble("${key}Lat").takeUnless { it.isNaN() }
    val lng = optDouble("${key}Lng").takeUnless { it.isNaN() }
    return Place(optString(key), lat, lng)
}
