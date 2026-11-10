package dev.pankajkhannade.nexus

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NexusViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NexusStore(app)
    var saved by mutableStateOf(Saved())
        private set
    var loaded by mutableStateOf(false)
        private set
    var draft by mutableStateOf("")
    var notice by mutableStateOf("")

    init {
        viewModelScope.launch {
            saved = store.read()
            loaded = true
            // Retry coordinates that failed earlier (offline at setup, or saved by an older build).
            if (saved.home.isSet && !saved.home.hasCoordinates) locate(PlaceKind.HOME, saved.home.address)
            if (saved.office.isSet && !saved.office.hasCoordinates) locate(PlaceKind.OFFICE, saved.office.address)
        }
    }

    fun share(text: String) {
        draft = text
        notice = ""
    }

    private fun update(next: Saved) {
        saved = next
        viewModelScope.launch { store.write(next) }
    }

    fun finishSetup(country: Country, home: String, office: String, people: List<Person>) {
        update(
            saved.copy(
                setupDone = true,
                country = country.name,
                dial = country.dial,
                countryCode = country.code,
                home = Place(home.trim()),
                office = Place(office.trim()),
                people = people,
            ),
        )
        locate(PlaceKind.HOME, home)
        locate(PlaceKind.OFFICE, office)
    }

    fun rememberCab(which: CabApp) = update(saved.copy(lastCab = which.id))

    fun explainContacts() = update(saved.copy(contactsExplained = true))

    /** Saves a person through the single normaliser. Returns the saved person, or null if the number is unusable. */
    fun savePerson(name: String, rawPhone: String): Person? {
        val phone = normalizePhone(rawPhone, saved.dial) ?: return null
        val person = Person(id = phoneDigits(phone), name = name.trim(), phone = phone)
        val next = saved.people.filterNot { nameMatches(it.name, person.name) || it.phone == phone } + person
        update(saved.copy(people = next))
        return person
    }

    fun removePerson(id: String) = update(saved.copy(people = saved.people.filterNot { it.id == id }))

    fun savePlace(kind: PlaceKind, address: String) {
        val trimmed = address.trim()
        val current = if (kind == PlaceKind.OFFICE) saved.office else saved.home
        if (trimmed == current.address) return
        if (trimmed.isNotEmpty() && trimmed.length < 4) return
        update(if (kind == PlaceKind.OFFICE) saved.copy(office = Place(trimmed)) else saved.copy(home = Place(trimmed)))
        locate(kind, trimmed)
    }

    /** Adds coordinates with the on-device Geocoder in the background; the text address works meanwhile. */
    private fun locate(kind: PlaceKind, address: String) {
        val text = address.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val place = withContext(Dispatchers.IO) { geocode(getApplication(), text) }
            Log.i("NexusPlace", "${kind.name} geocoded=${place.hasCoordinates} geocoderPresent=${android.location.Geocoder.isPresent()}")
            if (!place.hasCoordinates) return@launch
            val current = if (kind == PlaceKind.OFFICE) saved.office else saved.home
            if (current.address != text) return@launch
            update(if (kind == PlaceKind.OFFICE) saved.copy(office = place) else saved.copy(home = place))
        }
    }
}

class MainActivity : ComponentActivity() {
    private val model: NexusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consume(intent)
        setContent { NexusApp(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotEmpty()) model.share(text)
    }
}
