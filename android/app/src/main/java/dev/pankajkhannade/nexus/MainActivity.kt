package dev.pankajkhannade.nexus

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Where the parent is in the app. Setup screens are driven by [NexusViewModel.setupStep] instead. */
sealed interface Screen {
    data object Home : Screen
    data object Listening : Screen
    data class Confirm(val person: Person) : Screen
    data object NotUnderstood : Screen
    data class Calling(val person: Person) : Screen
    data object Safety : Screen
    data object Ride : Screen
    data object Settings : Screen
    data object Privacy : Screen
    /** A dictated message waiting for the parent's OK ([NexusViewModel.draft]). */
    data object Message : Screen
}

class NexusViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NexusStore(app)
    val speaker = Speaker(app)
    val listener = Listener(app)

    var saved by mutableStateOf(Saved())
        private set
    var usage by mutableStateOf(Usage())
        private set
    var feedback by mutableStateOf(Feedback())
        private set
    var loaded by mutableStateOf(false)
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
    var notice by mutableStateOf("")

    /** Debug builds can pretend days have passed (adb extra "debug_days") to test the feedback card. */
    var dayOffset by mutableStateOf(0L)
    val today: Long get() = LocalDate.now().toEpochDay() + dayOffset

    // ---- Setup draft (kept here so it survives the language switch and rotation) ----
    var setupStep by mutableStateOf(0)
    var setupLanguage by mutableStateOf("mr")
    var setupPeople by mutableStateOf<List<Person>>(emptyList())
    var setupHome by mutableStateOf("")

    // ---- Voice ----
    var voiceMode by mutableStateOf(VoiceMode.ONLINE)
        private set
    var heard by mutableStateOf("")
        private set
    var voiceFails by mutableStateOf(0)
        private set
    /** True while the setup "Try it" step is running, so success finishes setup. */
    var tryingInSetup by mutableStateOf(false)

    // ---- Dictated messages ----
    var draft by mutableStateOf<Dictation?>(null)
        private set
    /** Bumped when the draft comes from voice or gets a recipient, so it is read aloud again. */
    var draftVersion by mutableStateOf(0)
        private set
    var dictating by mutableStateOf(false)
        private set

    // ---- Is this safe? ----
    var checkText by mutableStateOf("")
    var checkImage by mutableStateOf<File?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var verdict by mutableStateOf<Verdict?>(null)
        private set

    init {
        viewModelScope.launch {
            // A screenshot from an earlier check is not kept between sessions.
            withContext(Dispatchers.IO) { File(app.cacheDir, "shared/check.jpg").delete() }
            saved = store.read()
            usage = store.readUsage()
            feedback = store.readFeedback()
            loaded = true
            if (saved.setupDone) record(Event.OPEN)
            if (saved.home.isSet && !saved.home.hasCoordinates) locate(saved.home.address)
            refreshVoiceMode()
        }
    }

    override fun onCleared() {
        listener.stop()
        speaker.shutdown()
    }

    val language: String get() = saved.language.ifBlank { setupLanguage }

    /** Dialling code: the saved one, or during setup the one guessed from the SIM / locale. */
    val dial: String get() = saved.dial.ifBlank { guessCountry(getApplication()).dial }

    private fun update(next: Saved) {
        saved = next
        viewModelScope.launch { store.write(next) }
    }

    /** Tests only: Robolectric shares one DataStore per JVM, so each test starts from empty counts. */
    @androidx.annotation.VisibleForTesting
    internal fun resetLocalDataForTest() {
        usage = Usage()
        feedback = Feedback()
        update(saved.copy(installDay = 0, lastCab = ""))
        viewModelScope.launch { store.writeUsage(Usage()); store.writeFeedback(Feedback()) }
    }

    fun record(event: Event) {
        usage = usage.record(event, today)
        val snapshot = usage
        viewModelScope.launch { store.writeUsage(snapshot) }
    }

    private fun updateFeedback(next: Feedback) {
        feedback = next
        viewModelScope.launch { store.writeFeedback(next) }
    }

    fun answerFeedback(round: Int, helped: Helped) = updateFeedback(feedback.answer(round, helped))
    fun easierFeedback(round: Int, text: String) = updateFeedback(feedback.easier(round, text))
    fun skipFeedback(round: Int) = updateFeedback(feedback.skip(round))
    fun finishFeedback(round: Int) = updateFeedback(feedback.finish(round))

    /** Q2 "Speak answer": recognised words only, kept on the phone. */
    fun speakFeedback(onText: (String) -> Unit) {
        if (voiceMode == VoiceMode.UNAVAILABLE || !listener.available()) return
        listener.start(language, voiceMode, onPartial = onText) { said -> listener.stop(); said?.let(onText) }
    }

    // ---- Setup ----

    fun chooseLanguage(lang: String) {
        setupLanguage = lang
        refreshVoiceMode()
    }

    /** Adds or replaces a person in the setup draft. Returns false when the number can't be used. */
    fun putSetupPerson(original: Person?, name: String, rawPhone: String, relation: String, askFamily: Boolean, alias: String): Boolean {
        val phone = normalizePhone(rawPhone, dial) ?: return false
        val person = Person(phoneDigits(phone), name.trim(), phone, relation, askFamily, alias.trim())
        val others = setupPeople.filterNot { it.id == original?.id || it.phone == phone }
        setupPeople = (others + person).take(MAX_PEOPLE)
        return true
    }

    fun removeSetupPerson(person: Person) {
        setupPeople = setupPeople.filterNot { it.id == person.id }
    }

    /** Saves setup. Country is inferred (SIM, then locale, else India) and can be changed in Family settings. */
    fun finishSetup(country: Country) {
        update(
            saved.copy(
                setupDone = true,
                language = setupLanguage,
                installDay = if (saved.installDay > 0) saved.installDay else today,
                country = country.name,
                dial = country.dial,
                countryCode = country.code,
                home = Place(setupHome.trim()),
                people = setupPeople,
            ),
        )
        locate(setupHome)
        record(Event.OPEN)
        applyLanguage(setupLanguage)
    }

    fun applyLanguage(lang: String) {
        if (saved.setupDone && saved.language != lang) update(saved.copy(language = lang))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        refreshVoiceMode()
    }

    fun markMicAsked() = update(saved.copy(micAsked = true))
    fun markCallAsked() = update(saved.copy(callAsked = true))
    fun rememberCab(which: CabApp) = update(saved.copy(lastCab = which.id))

    // ---- People and places (Family settings) ----

    fun savePerson(original: Person?, name: String, rawPhone: String, relation: String, askFamily: Boolean, alias: String): Boolean {
        val phone = normalizePhone(rawPhone, dial) ?: return false
        val person = Person(phoneDigits(phone), name.trim(), phone, relation, askFamily, alias.trim())
        val list = saved.people.toMutableList()
        val at = list.indexOfFirst { it.id == original?.id || it.phone == phone }
        if (at >= 0) list[at] = person else list += person
        update(saved.copy(people = list.take(MAX_PEOPLE)))
        return true
    }

    /** Removes a person and returns an undo action. */
    fun removePerson(person: Person): () -> Unit {
        val before = saved.people
        update(saved.copy(people = before.filterNot { it.id == person.id }))
        return { update(saved.copy(people = before)) }
    }

    fun movePerson(person: Person, up: Boolean) {
        val list = saved.people.toMutableList()
        val i = list.indexOfFirst { it.id == person.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        update(saved.copy(people = list))
    }

    /** Saves the home address and returns an undo action that restores the previous one (with its coordinates). */
    fun saveHome(address: String): () -> Unit {
        val before = saved.home
        val trimmed = address.trim()
        if (trimmed == before.address) return {}
        update(saved.copy(home = Place(trimmed)))
        locate(trimmed)
        return { update(saved.copy(home = before)) }
    }

    fun setCountry(country: Country) = update(saved.copy(country = country.name, dial = country.dial, countryCode = country.code))

    /** Adds coordinates with the on-device Geocoder in the background; the text address works meanwhile. */
    private fun locate(address: String) {
        val text = address.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val place = withContext(Dispatchers.IO) { geocode(getApplication(), text) }
            Log.i("NexusPlace", "HOME geocoded=${place.hasCoordinates} geocoderPresent=${android.location.Geocoder.isPresent()}")
            if (!place.hasCoordinates || saved.home.address != text) return@launch
            update(saved.copy(home = place))
        }
    }

    // ---- Voice ----

    fun refreshVoiceMode() = listener.checkMode(language) { voiceMode = it }

    fun startListening() {
        heard = ""
        screen = Screen.Listening
        if (voiceMode == VoiceMode.UNAVAILABLE || !listener.available()) {
            Log.i("NexusVoice", "no recognizer on this phone")
            understood(null)
            return
        }
        listener.start(language, voiceMode, onPartial = { heard = it }) { said -> understood(said) }
    }

    fun cancelListening() {
        listener.stop()
        screen = Screen.Home
    }

    /** Handles what was heard (or typed in Family settings for testing). */
    fun understood(said: String?, typed: Boolean = false) {
        listener.stop()
        val text = said.orEmpty().trim()
        heard = text
        val reading = readMessage(text, saved)
        val person = reading.person
        if (text.isNotEmpty() && !typed) record(Event.VOICE)
        readDictation(text, saved)?.let { dictation ->
            voiceFails = 0
            tryingInSetup = false
            openDraft(dictation)
            return
        }
        if (wantsRideHome(reading) && saved.home.isSet) {
            voiceFails = 0
            tryingInSetup = false
            screen = Screen.Ride
            return
        }
        if (person == null) {
            voiceFails += 1
            if (!typed) record(Event.VOICE_FAIL)
            speak(R.string.not_understood)
            screen = Screen.NotUnderstood
            return
        }
        voiceFails = 0
        tryingInSetup = false
        if (reading.ask == Ask.MESSAGE) {
            screen = Screen.Home
            openWhatsAppFor(person)
            return
        }
        // An exact name or alias goes straight to Calling, which still has a 3-second Cancel.
        screen = if (reading.exact) Screen.Calling(person) else Screen.Confirm(person)
    }

    fun speak(res: Int, vararg args: Any) {
        val text = getApplication<Application>().inLanguage(language).getString(res, *args)
        speaker.speak(language, text)
    }

    // ---- Calls and messages ----

    fun startCall(person: Person) {
        screen = Screen.Calling(person)
    }

    fun openWhatsAppFor(person: Person) {
        record(Event.WHATSAPP)
        if (!openWhatsApp(getApplication(), person.phone, "")) notice = getApplication<Application>().getString(R.string.whatsapp_missing)
    }

    // ---- Dictated messages ----

    fun openDraft(dictation: Dictation) {
        draft = dictation
        draftVersion += 1
        screen = Screen.Message
    }

    fun setDraftText(text: String) {
        draft = draft?.copy(text = text)
    }

    fun setDraftPerson(person: Person) {
        draft = draft?.copy(person = person, askedName = null)
        draftVersion += 1
    }

    /** "Speak again" on the message screen: the new words replace the message (or the whole command is re-read). */
    fun dictateAgain() {
        if (voiceMode == VoiceMode.UNAVAILABLE || !listener.available()) return
        speaker.stop()
        dictating = true
        listener.start(language, voiceMode, onPartial = {}) { said ->
            listener.stop()
            dictating = false
            said?.let(::redictate)
        }
    }

    fun redictate(said: String) {
        val current = draft ?: return
        val again = readDictation(said, saved)
        draft = if (again != null && again.text.isNotBlank()) {
            current.copy(
                person = again.person ?: current.person,
                askedName = if (again.person != null) null else current.askedName,
                channel = if (again.channel != Channel.ANY) again.channel else current.channel,
                text = again.text,
                rawBody = again.rawBody,
            )
        } else {
            current.copy(text = polishMessage(said), rawBody = said)
        }
        draftVersion += 1
    }

    /** Reads the draft back in the parent's language, or asks who / what. */
    fun speakDraft() {
        val d = draft ?: return
        val person = d.person
        when {
            person == null -> speak(R.string.msg_who)
            d.text.isBlank() -> speak(R.string.msg_what)
            else -> speak(R.string.msg_confirm_tts, person.name, d.text)
        }
    }

    /** Opens WhatsApp or the SMS app with the message typed in. Returns false when nothing could open. */
    fun sendDraft(via: Channel): Boolean {
        val d = draft ?: return false
        val person = d.person ?: return false
        val app = getApplication<Application>()
        val opened = if (via == Channel.SMS) {
            openSmsDraft(app, person.phone, d.text)
        } else {
            whatsAppPackage(app)?.let { openWhatsAppText(app, it, person.phone, d.text) } ?: false
        }
        if (!opened) {
            notice = app.getString(R.string.msg_not_opened)
            return false
        }
        record(Event.MESSAGE)
        if (!saved.messageExplained) update(saved.copy(messageExplained = true))
        speaker.stop()
        draft = null
        screen = Screen.Home
        return true
    }

    fun cancelDraft() {
        speaker.stop()
        listener.stop()
        dictating = false
        draft = null
        screen = Screen.Home
    }

    // ---- Is this safe? ----

    fun openCheck(text: String) {
        checkImage = null
        checkText = text
        verdict = if (text.isBlank()) null else checkMessage(text)
        if (text.isNotBlank()) record(Event.SAFETY_CHECK)
        screen = Screen.Safety
        verdict?.let { speakVerdict(it) }
    }

    fun openCheckImage(uri: Uri) {
        screen = Screen.Safety
        checking = true
        verdict = null
        checkText = ""
        viewModelScope.launch {
            val app = getApplication<Application>()
            val file = withContext(Dispatchers.IO) { copyToCache(app, uri) }
            checkImage = file
            val text = file?.let { readImageText(app, Uri.fromFile(it)) }.orEmpty()
            Log.i("NexusSafety", "ocr chars=${text.length}")
            checkText = text
            verdict = if (text.isBlank()) null else checkMessage(text)
            checking = false
            record(Event.SAFETY_CHECK)
            verdict?.let { speakVerdict(it) }
        }
    }

    fun clearCheck() {
        checkText = ""
        checkImage = null
        verdict = null
    }

    private fun speakVerdict(v: Verdict) {
        val ctx = getApplication<Application>().inLanguage(language)
        val first = v.shown.firstOrNull()?.let { ctx.getString(reasonText(it)) }.orEmpty()
        speaker.speak(language, listOf(ctx.getString(verdictTitle(v.level)), first).filter { it.isNotBlank() }.joinToString(". "))
    }

    fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        if (type.startsWith("image/")) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let(::openCheckImage)
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (text.isNotEmpty()) openCheck(text)
        }
    }

    companion object {
        const val MAX_PEOPLE = 4
    }
}

class MainActivity : AppCompatActivity() {
    private val model: NexusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) consume(intent)
        setContent { NexusApp(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        intent ?: return
        if (BuildConfig.DEBUG && intent.hasExtra("debug_days")) model.dayOffset = intent.getLongExtra("debug_days", 0)
        model.handleShare(intent)
    }
}
