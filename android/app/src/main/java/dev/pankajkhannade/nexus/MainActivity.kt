package dev.pankajkhannade.nexus

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

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
                tourDone = false,
                country = country.name,
                dial = country.dial,
                countryCode = country.code,
                home = home.trim(),
                office = office.trim(),
                people = people,
            ),
        )
    }

    fun finishTour() = update(saved.copy(tourDone = true))

    fun setTheme(dark: Boolean) = update(saved.copy(themeDark = dark))

    fun rememberCab(which: String) = update(saved.copy(lastCab = which))

    fun explainContacts() = update(saved.copy(contactsExplained = true))

    fun savePerson(person: Person) {
        val next = saved.people.filterNot { nameMatches(it.name, person.name) } + person.copy(id = person.id.ifBlank { person.phone })
        update(saved.copy(people = next))
    }

    fun removePerson(id: String) = update(saved.copy(people = saved.people.filterNot { it.id == id }))

    fun savePlace(name: String, address: String) {
        val trimmed = address.trim()
        if (trimmed.length < 4) return
        update(
            when (name.lowercase()) {
                "office" -> saved.copy(office = trimmed)
                else -> saved.copy(home = trimmed)
            },
        )
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
