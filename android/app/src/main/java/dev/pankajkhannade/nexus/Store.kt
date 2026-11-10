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
        .put("tourDone", saved.tourDone)
        .put("country", saved.country)
        .put("dial", saved.dial)
        .put("countryCode", saved.countryCode)
        .put("themeDark", saved.themeDark)
        .put("lastCab", saved.lastCab)
        .put("contactsExplained", saved.contactsExplained)
        .put("home", saved.home)
        .put("office", saved.office)
        .put("people", people)
        .toString()
}

private fun decode(raw: String): Saved {
    val json = JSONObject(raw)
    val peopleJson = json.optJSONArray("people") ?: JSONArray()
    val people = (0 until peopleJson.length()).mapNotNull { index ->
        val item = peopleJson.optJSONObject(index) ?: return@mapNotNull null
        Person(
            id = item.optString("id"),
            name = item.optString("name"),
            phone = item.optString("phone"),
        )
    }
    return Saved(
        setupDone = json.optBoolean("setupDone"),
        tourDone = json.optBoolean("tourDone"),
        country = json.optString("country"),
        dial = json.optString("dial"),
        countryCode = json.optString("countryCode"),
        themeDark = json.optBoolean("themeDark"),
        lastCab = json.optString("lastCab"),
        contactsExplained = json.optBoolean("contactsExplained"),
        home = json.optString("home"),
        office = json.optString("office"),
        people = people,
    )
}
