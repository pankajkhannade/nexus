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
    /** How Nexus is doing: usage metrics, tester tag, export, delete (child scale, inside Family settings). */
    data object Metrics : Screen
    /** The opt-in upload's consent page. Nothing is sent until the child says yes here. */
    data object UploadConsent : Screen
    /** A dictated message waiting for the parent's OK ([NexusViewModel.draft]). */
    data object Message : Screen
    /** S15 Near home: up to 6 need tiles. */
    data object NearHome : Screen
    /** S15: an app for the need plus the nearby maps search (Medicines, Doctor). */
    data class NeedHelp(val need: Need) : Screen
    /** S15: the picked app is no longer on the phone. */
    data class Install(val need: Need, val app: ServiceApp) : Screen
    /** S15: 108, family, nearest hospital. */
    data object Emergency : Screen
    /** A spoken or typed destination: up to 3 places to confirm ([NexusViewModel.destHits]). */
    data object Destination : Screen
    /** Phone book: why Nexus wants to look in the contacts, before Android asks. */
    data class ContactsPermission(val name: String, val ask: Ask) : Screen
    /** Phone book: two Rahuls, or one unsure match. Up to 3 big choices. */
    data class ChooseContact(val name: String, val ask: Ask, val options: List<Person>) : Screen
    /** Phone book: nobody by that name. Offers the dial pad. */
    data class ContactNotFound(val name: String) : Screen
    /** UX sprint 2: help, always one tap from home: call or message the child. */
    data object Help : Screen
    /** UX sprint 2 (roadmap N3): money already gone. 1930, the bank, cybercrime.gov.in, tell the child. */
    data object MoneyGone : Screen
    /** Guard (2026-11-12): a message Nexus will not prepare (an OTP / PIN, a card number, a location it wasn't asked for). */
    data class Blocked(val reason: GuardReason) : Screen
}

/** Handoffs that get a "did it work?" question when the parent comes back to Nexus. */
val CHECKED_HANDOFFS = setOf(AppCategory.DIALER, AppCategory.CAB, AppCategory.WHATSAPP, AppCategory.SMS, AppCategory.MAPS)

class NexusViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NexusStore(app)
    val speaker = Speaker(app)
    private var speech: SpeechEngine = AndroidSpeechEngine(app)
    private var voice = ContinuousListener(speech)
    /** The phone's contacts. A seam so JVM tests can use fixtures. Never stored: see [phoneBook]. */
    @androidx.annotation.VisibleForTesting
    internal var contacts: ContactsSource = AndroidContactsSource(app)
    /** Where slow lookups (contacts, Geocoder) run. Tests make it immediate. */
    @androidx.annotation.VisibleForTesting
    internal var io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    private var contactIndex: List<PhoneContact>? = null
    private var contactIndexAt = 0L
    /** Place words to real places (Android Geocoder). A seam so JVM tests can fake it. */
    @androidx.annotation.VisibleForTesting
    internal var placeFinder: PlaceFinder = AndroidPlaceFinder(app)
    /** Words to actions. A seam so a smarter parser can replace the rules later. */
    var parser: IntentParser = RuleIntentParser

    var saved by mutableStateOf(Saved())
        private set
    var usage by mutableStateOf(Usage())
        private set
    var feedback by mutableStateOf(Feedback())
        private set
    var loaded by mutableStateOf(false)
        private set

    /** Address -> coordinates and postal code, on the phone's Geocoder. A seam so JVM tests can fake it. */
    @androidx.annotation.VisibleForTesting
    internal var lookupHome: (String) -> Pair<Place, String?> = { geocodeHome(app, it) }
    var screen by mutableStateOf<Screen>(Screen.Home)
    var notice by mutableStateOf("")

    /** Debug builds can pretend days have passed (adb extra "debug_days") to test the feedback card. */
    var dayOffset by mutableStateOf(0L)
    val today: Long get() = LocalDate.now().toEpochDay() + dayOffset

    // ---- Usage analytics (Analytics.kt, Metrics.kt, docs/metrics.md). Counts and timings only. ----
    /** The on-device event log. A seam so tests can use a temp folder. */
    @androidx.annotation.VisibleForTesting
    internal var events: EventLog = EventLog.get(app)
    val analyticsPrefs = AnalyticsPrefs(app)
    /** Bumped on every logged event, so the How Nexus is doing screen recomputes. */
    var metricsVersion by mutableStateOf(0)
        private set
    var uploadOn by mutableStateOf(analyticsPrefs.uploadOn)
        private set
    var testerSource by mutableStateOf(analyticsPrefs.testerSource)
        private set
    /** Wall clock in ms, shifted by [dayOffset] like [today]. */
    val nowMs: Long get() = System.currentTimeMillis() + dayOffset * 86_400_000L
    private var voiceStartedAt = 0L
    /** How the current request started (voice or a tile), for the action it opens. */
    private var currentSource: Source = Source.TILE
    private var awayCategory: AppCategory? = null
    private var awayAt = 0L
    private var setupStartedAt = 0L

    fun track(event: UsageEvent) {
        runCatching { events.append(event) }
        metricsVersion += 1
    }

    private fun track(
        type: EventType, lang: Lang? = null, command: CommandType? = null, source: Source? = null, end: EndReason? = null,
        perm: Perm? = null, category: AppCategory? = null, ok: Boolean? = null, ms: Long? = null, chars: Int? = null,
        code: Int? = null, step: Int? = null, guard: GuardReason? = null,
    ) = track(UsageEvent(nowMs, type, lang, command, source, end, perm, category, ok, ms, chars, code, step, guard))

    fun metrics(): MetricsReport = computeMetrics(events.all(), nowMs)

    /** A parent tile (Call, WhatsApp, Go home, Near home, Is this safe?). */
    fun tile(command: CommandType) {
        currentSource = Source.TILE
        track(EventType.COMMAND, lang = Lang.of(language), command = command, source = Source.TILE, ok = true)
    }

    /** Nexus handed the parent to another app. The return time (see [cameBack]) tells a wrong action from a real one. */
    fun actionOpened(category: AppCategory) {
        track(EventType.ACTION_OPENED, category = category, source = currentSource)
        runningStep?.let { track(EventType.STEP_DONE, step = it + 1, category = category); runningStep = null }
        awayCategory = category
        awayAt = nowMs
    }

    /** Stop / No / Back / Cancel before an action. */
    fun cancelled(command: CommandType) = track(EventType.CANCEL, command = command, source = currentSource)

    fun nearTile(need: Need) {
        tile(CommandType.NEAR_HOME)
        track(EventType.NEAR_TILE, category = AppCategory.of(need))
    }

    fun playRedirect(category: AppCategory) {
        track(EventType.PLAY_REDIRECT, category = category)
        actionOpened(AppCategory.PLAY_STORE)
    }

    fun permissionAnswered(perm: Perm, granted: Boolean) = track(EventType.PERMISSION, perm = perm, ok = granted)

    /** The activity is visible again (onStart): after an action, or a plain return to the app. */
    fun cameBack() {
        val cat = awayCategory
        if (cat != null) {
            track(EventType.ACTION_RETURN, category = cat, ms = (nowMs - awayAt).coerceAtLeast(0))
            awayCategory = null
            // "Done": the last step of heard -> said back -> doing -> done. After a call, ride, message or map,
            // home asks "did it work?" (UX sprint 2); anything else gets the plain welcome-back line.
            if (saved.setupDone && screen == Screen.Home) {
                if (cat in CHECKED_HANDOFFS) {
                    notice = ""
                    returnCheck = cat
                    speak(returnQuestion(cat))
                } else {
                    notice = getApplication<Application>().getString(R.string.welcome_back)
                    if (pendingStep() != null) offerNext()
                }
            }
        } else if (startedOnce) {
            track(EventType.FOREGROUND)
        }
        startedOnce = true
    }
    private var startedOnce = false

    /** The handoff home is asking about ("did it work?"), or null. In memory only; the answer is logged as yes/no. */
    var returnCheck by mutableStateOf<AppCategory?>(null)

    fun returnQuestion(cat: AppCategory): Int = when (cat) {
        AppCategory.DIALER -> R.string.check_call
        AppCategory.WHATSAPP, AppCategory.SMS -> R.string.check_message_sent
        AppCategory.CAB -> R.string.check_ride
        else -> R.string.check_generic
    }

    /** The parent's answer to "did it work?": a fixed yes/no with the app category, nothing else. No opens Help. */
    fun answerReturnCheck(worked: Boolean) {
        val cat = returnCheck ?: return
        returnCheck = null
        track(EventType.RETURN_CHECK, category = cat, ok = worked)
        if (pendingStep() != null) {
            // Multi-step: "did it work?" decides the "if he doesn't pick up" steps, then the next step is offered.
            if (worked) skipConditionalSteps()
            val next = pendingStep()
            when {
                next == null -> { notice = getApplication<Application>().inLanguage(language).getString(R.string.plan_done); speak(R.string.plan_done) }
                worked || next.onlyIfNotWorked -> offerNext()
                else -> openHelp() // A ride or message that failed: help first; the next step waits on home.
            }
            return
        }
        if (worked) {
            notice = getApplication<Application>().inLanguage(language).getString(R.string.check_glad)
            speak(R.string.check_glad)
        } else {
            openHelp()
        }
    }

    // ---- Help and money gone (UX sprint 2) ----

    /** Who help goes to: the Ask-family people, else everyone. */
    fun helpers(): List<Person> = saved.people.filter { it.askFamily }.ifEmpty { saved.people }

    fun openHelp() {
        returnCheck = null
        screen = Screen.Help
        track(EventType.HELP_OPENED)
        helpers().firstOrNull()?.let { speak(R.string.help_tts, nameOf(it)) }
    }

    fun openMoneyGone() {
        screen = Screen.MoneyGone
        track(EventType.MONEY_GONE)
        speak(R.string.money_gone_tts)
    }

    /** Back from the money-gone steps: to the verdict it came from, else home. */
    fun leaveMoneyGone() {
        screen = if (verdict != null) Screen.Safety else Screen.Home
    }

    /** The person's name as the parent reads it (Devanagari in mr / hi when the child typed one). */
    fun nameOf(person: Person): String = person.shownIn(language)

    /** Family settings: seconds before a call is placed (3, 5 or 8). */
    fun setCountdown(seconds: Int) = update(saved.copy(countdownSeconds = cleanCountdown(seconds)))

    fun setUploadConsent(on: Boolean) {
        analyticsPrefs.setUpload(on)
        analyticsPrefs.language = language
        uploadOn = on
        if (on) MetricsUpload.schedule(getApplication()) else MetricsUpload.cancel(getApplication())
    }

    fun chooseTesterSource(source: TesterSource) {
        analyticsPrefs.setTesterSource(source)
        testerSource = source
    }

    /** Counts-only summary for the share sheet (a tester's child WhatsApps it to Pankaj). */
    fun summary(): String = summaryJson(metrics(), testerSource, BuildConfig.VERSION_NAME, saved.language).toString(2)

    /** Delete all usage data: the event log, the daily counters, feedback answers, the upload choice and install ID. */
    fun deleteUsageData() {
        events.clear()
        analyticsPrefs.clear()
        MetricsUpload.cancel(getApplication())
        uploadOn = false
        testerSource = TesterSource.UNSET
        usage = Usage()
        feedback = Feedback()
        viewModelScope.launch { store.writeUsage(Usage()); store.writeFeedback(Feedback()) }
        metricsVersion += 1
    }

    // ---- Setup draft (kept here so it survives the language switch and rotation) ----
    private var furthestSetupStep = 0
    private var setupStepState by mutableStateOf(0)
    var setupStep: Int
        get() = setupStepState
        set(value) {
            setupStepState = value
            if (value > furthestSetupStep) {
                furthestSetupStep = value
                // Logged as the page's fixed number (SetupPage.ordinal), so skipped permission pages don't shift it.
                track(EventType.SETUP_STEP, step = setupPlan.getOrNull(value)?.ordinal ?: value)
            }
        }
    /**
     * The setup pages for this phone, decided once (UX sprint 2, honest steps): permissions already granted
     * are left out, so "Step x of N" is true and never changes under the child. See [setupPlan].
     */
    var setupPlan: List<SetupPage> = emptyList()
        private set

    fun planSetup(micGranted: Boolean, callGranted: Boolean) {
        if (setupPlan.isEmpty()) setupPlan = setupPages(micGranted, callGranted)
    }
    var setupLanguage by mutableStateOf(DEFAULT_LANGUAGE)
    var setupPeople by mutableStateOf<List<Person>>(emptyList())
    var setupHome by mutableStateOf("")
    /** S15 setup step: need key -> picked package ("" = none), and the home PIN. */
    var setupServices by mutableStateOf<Map<String, String>>(emptyMap())
    var setupPincode by mutableStateOf("")

    // ---- Voice ----
    var voiceMode by mutableStateOf(VoiceMode.ONLINE)
        private set
    var heard by mutableStateOf("")
        private set
    var voiceFails by mutableStateOf(0)
        private set
    /** True while the setup "Try it" step is running, so success finishes setup. */
    var tryingInSetup by mutableStateOf(false)

    /** Index into [practiceTasks], or -1 when the leave-behind check is not on screen. Size means the result card. */
    var practiceIndex by mutableStateOf(-1)
    var practiceResults by mutableStateOf<List<Boolean>>(emptyList())
    var practiceHint by mutableStateOf(PRACTICE_NONE)

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
        @androidx.annotation.VisibleForTesting internal set
    var verdict by mutableStateOf<Verdict?>(null)
        private set

    init {
        viewModelScope.launch {
            // A screenshot from an earlier check is not kept between sessions.
            withContext(Dispatchers.IO) { File(app.cacheDir, "shared/check.jpg").delete() }
            // A plan older than 30 minutes is dropped on load (in memory; the next save writes it out).
            saved = store.read().let { s -> if (s.plan?.expired(nowMs) == true) s.copy(plan = null) else s }
            usage = store.readUsage()
            feedback = store.readFeedback()
            loaded = true
            track(EventType.APP_OPEN)
            if (!saved.setupDone) startSetupClock()
            if (uploadOn) { analyticsPrefs.language = language; MetricsUpload.schedule(app) }
            if (saved.setupDone) record(Event.OPEN)
            if (saved.setupDone && !saved.practiceDone) startPractice()
            if (saved.home.isSet && !saved.home.hasCoordinates) locate(saved.home.address)
            refreshVoiceMode()
            // Warm the in-memory phone-book index so names can bias the recogniser.
            if (contacts.hasPermission()) runCatching { phoneBook() }
        }
    }

    /** Tests: a fake recogniser and clock drive [ContinuousListener] without a microphone. */
    @androidx.annotation.VisibleForTesting
    internal fun useSpeechEngine(engine: SpeechEngine, clock: () -> Long = System::currentTimeMillis) {
        voice.cancel()
        speech = engine
        voice = ContinuousListener(engine, clock)
        refreshVoiceMode()
    }

    override fun onCleared() {
        voice.cancel()
        speaker.shutdown()
    }

    val language: String get() = saved.language.ifBlank { setupLanguage }

    /** Dialling code: the saved one, or during setup the one guessed from the SIM / locale. */
    val dial: String get() = saved.dial.ifBlank { guessCountry(getApplication()).dial }

    private fun update(next: Saved) {
        saved = next
        viewModelScope.launch { store.write(next) }
    }

    // ---- Multi-step plans (Steps.kt, 2026-11-12) ----

    /** Index of the step whose action is open now, so its handoff is logged as step_done. In memory only. */
    private var runningStep: Int? = null

    /** The step waiting on the parent, or null (none, finished, or older than 30 minutes). */
    fun pendingStep(): PlannedStep? = saved.plan?.takeIf { !it.expired(nowMs) }?.pending

    /** "पुढचं (2/3): पंकजला मेसेज" for the card and the voice. */
    fun stepLabel(step: PlannedStep): String {
        val res = getApplication<Application>().inLanguage(language)
        return when (val command = parser.parse(step.text, saved)) {
            is Command.Call -> res.getString(R.string.step_call, nameOf(command.person))
            is Command.WhatsApp -> res.getString(R.string.step_whatsapp, nameOf(command.person))
            is Command.Message -> command.dictation.person?.let { res.getString(R.string.step_message, nameOf(it)) }
                ?: res.getString(R.string.step_generic, step.text)
            Command.RideHome -> res.getString(R.string.step_ride_home)
            is Command.RideTo -> res.getString(R.string.step_ride_to, command.place?.address?.substringBefore(',')?.trim() ?: command.query)
            is Command.FindContact -> res.getString(if (command.ask == Ask.CALL) R.string.step_call else R.string.step_message, command.name)
            else -> res.getString(R.string.step_generic, step.text)
        }
    }

    private fun savePlan(plan: PlanQueue?) = update(saved.copy(plan = plan?.takeIf { it.pending != null }))

    /** Starts a plan of two or more steps: says how many, then does the first (it has its own confirm screen). */
    private fun startPlan(split: SplitResult) {
        cancelPlan(silent = true)
        track(EventType.MULTI_STEP_STARTED, step = split.steps.size, source = currentSource)
        update(saved.copy(plan = PlanQueue(split.steps, 0, nowMs)))
        speak(R.string.plan_intro, split.steps.size)
        if (split.locationDropped) speak(R.string.location_note)
        runStep(0)
    }

    /** Does step [index]: the plan moves on first, so coming back after the handoff offers the one after it. */
    private fun runStep(index: Int) {
        val plan = saved.plan ?: return
        val step = plan.steps.getOrNull(index) ?: return
        val command = parser.parse(step.text, saved)
        savePlan(plan.copy(next = index + 1, touchedAt = nowMs))
        if (command == Command.Unknown) {
            track(EventType.STEP_SKIPPED, step = index + 1)
            if (pendingStep() != null) offerNext()
            return
        }
        runningStep = index
        act(command)
    }

    /** Home's "पुढचं" card and voice line. */
    fun offerNext() {
        val step = pendingStep() ?: return
        val plan = saved.plan ?: return
        speak(R.string.plan_next, plan.next + 1, plan.steps.size, stepLabel(step))
    }

    /** हो on the card: do the next step. */
    fun acceptNext() {
        val plan = saved.plan ?: return
        if (pendingStep() == null) { savePlan(null); return }
        returnCheck = null
        currentSource = Source.TILE
        runStep(plan.next)
    }

    /** वगळा: skip this step, offer the next one. */
    fun skipNext() {
        val plan = saved.plan ?: return
        track(EventType.STEP_SKIPPED, step = plan.next + 1, ok = true)
        savePlan(plan.copy(next = plan.next + 1, touchedAt = nowMs))
        if (pendingStep() != null) offerNext() else speak(R.string.plan_done)
    }

    /** सगळं रद्द करा: the rest of the plan is dropped (each logged as skipped, ok=false). */
    fun cancelPlan(silent: Boolean = false) {
        val plan = saved.plan ?: return
        if (!plan.expired(nowMs)) for (i in plan.next until plan.steps.size) track(EventType.STEP_SKIPPED, step = i + 1, ok = false)
        runningStep = null
        savePlan(null)
        if (!silent) { notice = ""; speak(R.string.plan_cancelled) }
    }

    /** After a "yes, it worked": the "if he doesn't pick up" steps right after are not needed. */
    private fun skipConditionalSteps() {
        var plan = saved.plan ?: return
        while (plan.pending?.onlyIfNotWorked == true) {
            track(EventType.STEP_SKIPPED, step = plan.next + 1, ok = true)
            plan = plan.copy(next = plan.next + 1)
        }
        savePlan(plan.copy(touchedAt = nowMs))
    }

    // ---- Guard (Guard.kt, 2026-11-12) ----

    /** A message Nexus won't prepare: say why, offer to call the Ask-family person instead. */
    private fun block(reason: GuardReason) {
        track(EventType.GUARD_BLOCKED, guard = reason, source = currentSource)
        runningStep = null
        draft = null
        screen = Screen.Blocked(reason)
        speak(
            when (reason) {
                GuardReason.LONG_NUMBER -> R.string.guard_card_tts
                GuardReason.LOCATION -> R.string.guard_location_tts
                else -> R.string.guard_secret_tts
            },
            helpers().firstOrNull()?.let(::nameOf) ?: getApplication<Application>().inLanguage(language).getString(R.string.guard_family),
        )
    }

    fun leaveBlocked() {
        screen = Screen.Home
        if (pendingStep() != null) offerNext()
    }

    /** Tests only: Robolectric shares one DataStore per JVM, so each test starts from empty counts, India, the default language and no one-time hints. */
    @androidx.annotation.VisibleForTesting
    internal fun resetLocalDataForTest() {
        events.clear()
        analyticsPrefs.clear()
        uploadOn = false
        testerSource = TesterSource.UNSET
        awayCategory = null
        usage = Usage()
        feedback = Feedback()
        update(saved.copy(installDay = 0, lastCab = "", language = "", messageExplained = false, country = "India", dial = "+91", countryCode = "IN", pincode = "", services = emptyMap(), countdownSeconds = DEFAULT_COUNTDOWN, practiceDone = true, plan = null))
        runningStep = null
        practiceIndex = -1
        returnCheck = null
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

    /** True while the feedback "Speak answer" is listening (its button turns into Done). */
    var feedbackListening by mutableStateOf(false)
        private set

    /** Q2 "Speak answer": recognised words only, kept on the phone. Keeps listening through pauses until Done. */
    fun speakFeedback(onText: (String) -> Unit) {
        if (!canListen()) return
        feedbackListening = true
        voice.start(language, voiceMode, longForm = true, onText = onText) { said ->
            feedbackListening = false
            said?.let(onText)
        }
    }

    fun doneFeedbackSpeaking() = voice.done()

    // ---- Setup ----

    private fun startSetupClock() {
        val earlier = events.all().firstOrNull { it.type == EventType.SETUP_START }
        setupStartedAt = earlier?.t ?: nowMs
        if (earlier == null) track(EventType.SETUP_START)
    }

    fun chooseLanguage(lang: String) {
        setupLanguage = lang
        refreshVoiceMode()
    }

    /** Adds or replaces a person in the setup draft. Returns false when the number can't be used. */
    fun putSetupPerson(
        original: Person?, name: String, rawPhone: String, relation: String, askFamily: Boolean, alias: String,
        photo: String = "", typed: Boolean = false, localName: String = "",
    ): Boolean {
        val phone = normalizePhone(rawPhone, dial) ?: return false
        val person = Person(phoneDigits(phone), name.trim(), phone, relation, askFamily, alias.trim(), photo, localName.trim())
        // Keep the old place in the list, so faces keep their colour when someone is edited.
        val same: (Person) -> Boolean = { it.id == original?.id || it.phone == phone }
        val at = setupPeople.indexOfFirst(same)
        val rest = setupPeople.filterNot(same).toMutableList()
        if (at >= 0) rest.add(at.coerceAtMost(rest.size), person) else rest += person
        setupPeople = rest.take(MAX_PEOPLE)
        if (original != null && original.id != person.id) setupTyped = setupTyped - original.id
        if (typed && original == null) setupTyped = setupTyped + person.id
        return true
    }

    /**
     * People typed in by number during setup (not picked from the phone book). The family step offers to
     * save them into the phone's contacts, so the dialer shows a name instead of a bare number.
     */
    var setupTyped by mutableStateOf<Set<String>>(emptySet())

    fun removeSetupPerson(person: Person) {
        setupPeople = setupPeople.filterNot { it.id == person.id }
    }

    /** Saves setup. Country is inferred (SIM, then locale, else India) and can be changed in Family settings. */
    fun finishSetup(country: Country) {
        val picks = setupServices.ifEmpty { defaultPicks { isInstalled(getApplication(), it) } }
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
                pincode = cleanPincode(setupPincode) ?: pincodeIn(setupHome).orEmpty(),
                // Near home apps are no longer a setup page (UX sprint 2): the first installed app per need is
                // picked here, and the child can change it in Family settings.
                services = picks - Need.RIDE.key,
                lastCab = CabApp.entries.firstOrNull { it.pkg == picks[Need.RIDE.key] }?.id ?: saved.lastCab,
                practiceDone = false,
            ),
        )
        locate(setupHome)
        record(Event.OPEN)
        if (events.all().none { it.type == EventType.SETUP_DONE }) {
            track(EventType.SETUP_DONE, lang = Lang.of(setupLanguage), ms = if (setupStartedAt > 0) nowMs - setupStartedAt else null)
        }
        applyLanguage(setupLanguage)
    }

    /** The child stays for a call, a message, and (if home is set) the ride home. Nothing is opened during it. */
    fun startPractice() {
        val tasks = practiceTasks(saved)
        practiceResults = List(tasks.size) { false }
        practiceHint = PRACTICE_NONE
        practiceIndex = 0
        screen = Screen.Home
    }

    fun nextPractice() {
        practiceHint = PRACTICE_NONE
        val last = practiceTasks(saved).lastIndex
        practiceIndex = if (practiceIndex >= last) practiceTasks(saved).size else practiceIndex + 1
    }

    fun skipPracticeTask() {
        practiceHint = PRACTICE_NONE
        nextPractice()
    }

    fun retryPractice() {
        practiceResults = List(practiceTasks(saved).size) { false }
        practiceHint = PRACTICE_NONE
        practiceIndex = 0
        screen = Screen.Home
    }

    fun finishPractice() {
        practiceIndex = -1
        practiceHint = PRACTICE_NONE
        if (!saved.practiceDone) update(saved.copy(practiceDone = true))
        screen = Screen.Home
    }

    fun applyLanguage(lang: String) {
        if (saved.setupDone && saved.language != lang) update(saved.copy(language = lang))
        analyticsPrefs.language = lang
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        refreshVoiceMode()
    }

    fun markMicAsked() = update(saved.copy(micAsked = true))
    fun markCallAsked() = update(saved.copy(callAsked = true))
    fun rememberCab(which: CabApp) = update(saved.copy(lastCab = which.id))

    // ---- People and places (Family settings) ----

    fun savePerson(original: Person?, name: String, rawPhone: String, relation: String, askFamily: Boolean, alias: String, photo: String = "", localName: String = ""): Boolean {
        val phone = normalizePhone(rawPhone, dial) ?: return false
        val person = Person(phoneDigits(phone), name.trim(), phone, relation, askFamily, alias.trim(), photo, localName.trim())
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
        update(saved.copy(home = Place(trimmed), pincode = pincodeIn(trimmed).orEmpty()))
        locate(trimmed)
        return { update(saved.copy(home = before)) }
    }

    fun setCountry(country: Country) = update(saved.copy(country = country.name, dial = country.dial, countryCode = country.code))

    /** Adds coordinates with the on-device Geocoder in the background; the text address works meanwhile. */
    private fun locate(address: String) {
        val text = address.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val (place, postal) = withContext(Dispatchers.IO) { lookupHome(text) }
            Log.i("NexusPlace", "HOME geocoded=${place.hasCoordinates} pin=${postal != null} geocoderPresent=${android.location.Geocoder.isPresent()}")
            if (saved.home.address != text) return@launch
            val pin = saved.pincode.ifBlank { homePincode(text, postal) }
            if (!place.hasCoordinates && pin == saved.pincode) return@launch
            update(saved.copy(home = if (place.hasCoordinates) place else saved.home, pincode = pin))
        }
    }

    /** The home PIN from the setup address: typed in it, else the phone's Geocoder. Empty when neither has one. */
    suspend fun findPincode(address: String): String {
        pincodeIn(address)?.let { return it }
        val (_, postal) = withContext(Dispatchers.IO) { lookupHome(address) }
        return homePincode(address, postal)
    }

    // ---- S15 Near home ----

    /** Family settings: the app for [need] ("" = none). Ride is the cab app the Ride screen opens first. */
    fun pickService(need: Need, pkg: String) {
        if (need == Need.RIDE) {
            CabApp.entries.firstOrNull { it.pkg == pkg }?.let { update(saved.copy(lastCab = it.id)) }
            return
        }
        update(saved.copy(services = saved.services + (need.key to pkg)))
    }

    /** The saved app for [need], or "" when none. */
    fun pickedPackage(need: Need): String =
        if (need == Need.RIDE) CabApp.from(saved.lastCab)?.pkg.orEmpty() else saved.services[need.key].orEmpty()

    fun setPincode(raw: String): Boolean {
        val pin = cleanPincode(raw) ?: return false
        update(saved.copy(pincode = pin))
        return true
    }

    /**
     * A Near home tile or a spoken need. Groceries / home services open the picked app (or its Install
     * screen); Medicines and Doctor offer the app and maps; Ride and Emergency open their screens.
     */
    fun openNeed(need: Need) {
        record(need.event)
        val app = getApplication<Application>()
        val picked = serviceApp(need, pickedPackage(need))
        when (need) {
            Need.RIDE -> rideHome()
            Need.EMERGENCY -> screen = Screen.Emergency
            Need.MEDICINE, Need.DOCTOR -> {
                if (picked != null) {
                    screen = Screen.NeedHelp(need)
                } else {
                    screen = Screen.NearHome
                    openNearby(app, mapsQuery(need), saved.home, saved.pincode)
                    actionOpened(AppCategory.MAPS)
                }
            }
            Need.GROCERY, Need.HOME_SERVICE -> when {
                picked == null -> screen = Screen.NearHome
                isInstalled(app, picked.pkg) && speak(R.string.coach_app, picked.label).let { launchApp(app, picked.pkg) } -> {
                    screen = Screen.Home; actionOpened(AppCategory.of(need))
                }
                else -> screen = Screen.Install(need, picked)
            }
        }
    }

    /** "Order on Tata 1mg" inside Medicines / Doctor: the app, or its Install screen when it was removed. */
    fun openNeedApp(need: Need, picked: ServiceApp) {
        val opened = launchApp(getApplication(), picked.pkg)
        if (opened) actionOpened(AppCategory.of(need))
        screen = if (opened) Screen.Home else Screen.Install(need, picked)
    }

    fun openNeedMaps(need: Need) {
        openNearby(getApplication(), mapsQuery(need), saved.home, saved.pincode)
        actionOpened(AppCategory.MAPS)
        screen = Screen.Home
    }

    // ---- Voice ----

    fun refreshVoiceMode() = speech.checkMode(language) { voiceMode = it }

    private fun canListen(): Boolean = voiceMode != VoiceMode.UNAVAILABLE && speech.available()

    /**
     * The Speak button. Keeps listening through pauses (the recogniser's own cut-offs restart quietly) and
     * stops by itself once the words are a whole call, ride or need, or after a quiet spell, or on Done.
     */
    fun startListening() {
        heard = ""
        screen = Screen.Listening
        currentSource = Source.VOICE
        voiceStartedAt = nowMs
        track(EventType.VOICE_START, lang = Lang.of(language))
        if (!canListen()) {
            track(EventType.VOICE_END, lang = Lang.of(language), end = EndReason.ERROR, ms = 0, chars = 0)
            Log.i("NexusVoice", "no recognizer on this phone")
            understood(null)
            return
        }
        voice.start(
            language, voiceMode, longForm = false,
            isComplete = { readings -> readings.any { isCompleteCommand(parser, it, saved) } },
            isDictation = { parser.parse(it, saved) is Command.Message },
            biasing = biasingNames(),
            onText = { heard = it },
        ) { said ->
            trackVoiceEnd(voice.endReason, said)
            understood(said, alternatives = voice.finalAlternatives)
        }
    }

    private fun trackVoiceEnd(reason: EndReason, said: String?) {
        voice.errorCodes.forEach { track(EventType.RECOGNIZER_ERROR, code = it) }
        track(EventType.VOICE_END, lang = Lang.of(language), end = reason, ms = nowMs - voiceStartedAt, chars = said?.trim()?.length ?: 0)
    }

    /** Done on the listening screen: act on everything heard so far. */
    fun doneListening() {
        if (voice.active) voice.done() else understood(heard)
    }

    fun cancelListening() {
        if (voice.active) {
            voice.cancel()
            trackVoiceEnd(EndReason.CANCELLED, heard)
            cancelled(CommandType.UNKNOWN)
        }
        screen = Screen.Home
    }

    /** Names the recogniser should expect: saved people and their aliases. */
    private fun biasingNames(): List<String> =
        (saved.people.flatMap { it.names } + contactIndex.orEmpty().sortedByDescending { it.starred }.map { it.name })
            .filter { it.isNotBlank() }.distinct().take(200)

    /**
     * Handles what was heard (or typed in Family settings for testing). [alternatives] are the recogniser's
     * other readings, best first: the first one that means something wins ("पंकच" vs "पंकज").
     */
    fun understood(said: String?, typed: Boolean = false, alternatives: List<String> = emptyList()) {
        voice.cancel()
        val readings = (listOfNotNull(said?.trim()) + alternatives.map { it.trim() }).filter { it.isNotEmpty() }.distinct()
        // Multi-step (2026-11-12): "…आणि पंकजला सांग…" becomes a plan; anything with one step works as before.
        val split = if (practiceIndex < 0) readings.asSequence().map { splitCommand(it, saved, parser) }.firstOrNull { it.isPlan } else null
        if (split != null) {
            voiceFails = 0
            tryingInSetup = false
            heard = readings.first()
            currentSource = if (typed) Source.TYPED else Source.VOICE
            val first = parser.parse(split.steps.first().text, saved)
            track(
                EventType.COMMAND, lang = Lang.of(language), command = CommandType.of(first), source = currentSource,
                ok = true, ms = if (typed || voiceStartedAt == 0L) null else nowMs - voiceStartedAt,
            )
            if (!typed) record(Event.VOICE)
            startPlan(split)
            return
        }
        val (text, command) = readings.asSequence().map { it to parser.parse(it, saved) }.firstOrNull { it.second != Command.Unknown }
            ?: (said.orEmpty().trim() to Command.Unknown)
        heard = text
        currentSource = if (typed) Source.TYPED else Source.VOICE
        track(
            EventType.COMMAND, lang = Lang.of(language), command = CommandType.of(command), source = currentSource,
            ok = command != Command.Unknown, ms = if (typed || voiceStartedAt == 0L) null else nowMs - voiceStartedAt,
        )
        if (text.isNotEmpty() && !typed) record(Event.VOICE)
        val practiceTask = practiceTasks(saved).getOrNull(practiceIndex)
        if (practiceTask != null) {
            when {
                command == Command.Unknown -> {
                    voiceFails += 1
                    if (!typed) record(Event.VOICE_FAIL)
                    practiceHint = PRACTICE_MISSED
                    speak(R.string.practice_missed)
                }
                practicePasses(practiceTask, command) -> {
                    voiceFails = 0
                    practiceResults = practiceResults.toMutableList().also { if (practiceIndex in it.indices) it[practiceIndex] = true }
                    practiceHint = PRACTICE_YES
                    speak(R.string.practice_yes)
                }
                else -> {
                    voiceFails = 0
                    practiceHint = PRACTICE_WRONG
                    speak(R.string.practice_wrong)
                }
            }
            screen = Screen.Home
            return
        }
        if (command == Command.Unknown) {
            voiceFails += 1
            if (!typed) record(Event.VOICE_FAIL)
            // Say back what was heard and one thing to try, in one short line.
            if (text.isBlank()) speak(R.string.not_understood_tts_empty, examplePhrase())
            else speak(R.string.not_understood_tts, text, examplePhrase())
            screen = Screen.NotUnderstood
            return
        }
        voiceFails = 0
        tryingInSetup = false
        // A new single request replaces a plan still waiting from before.
        if (saved.plan != null) cancelPlan(silent = true)
        act(command)
    }

    /** Does one understood command (a step of a plan, or a whole request). */
    private fun act(command: Command) {
        when (command) {
            is Command.Message -> openDraft(command.dictation)
            Command.RideHome -> { rideHome(); speak(R.string.ride_readback_home) }
            is Command.RideTo -> if (command.place != null) {
                chooseDestination(command.place)
                speak(R.string.ride_readback, command.place.address.substringBefore(',').trim())
            } else {
                findDestination(command.query)
            }
            is Command.OpenNeed -> openNeed(command.need)
            is Command.WhatsApp -> {
                screen = Screen.Home
                openWhatsAppFor(command.person)
            }
            // An exact name or alias goes straight to Calling, which still has the countdown with Cancel.
            is Command.Call -> screen = if (command.exact) Screen.Calling(command.person) else Screen.Confirm(command.person)
            is Command.FindContact -> if (command.options.size > 1) {
                // Guard: two saved people fit the name ("Call Sunil"): ask, never guess.
                track(EventType.GUARD_BLOCKED, guard = GuardReason.NAME_UNSURE, source = currentSource)
                track(EventType.PICKER_SHOWN, source = currentSource)
                screen = Screen.ChooseContact(command.name, command.ask, command.options)
                speak(R.string.which_one)
            } else {
                lookUpContact(command.name, command.ask)
            }
            Command.Unknown -> Unit
        }
    }

    // ---- Rides ----

    /** Where the Ride screen goes: null = home. */
    var rideTo by mutableStateOf<Place?>(null)
        private set
    var destQuery by mutableStateOf("")
        private set
    /** Places found for [destQuery]; null while still looking. */
    var destHits by mutableStateOf<List<Place>?>(null)
        private set

    /** The Ride screen for home (with "Somewhere else" on it). */
    fun rideHome() {
        rideTo = null
        screen = Screen.Ride
    }

    /** Looks up [query] near home and shows up to 3 places to confirm, read back in the parent's language. */
    fun findDestination(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        destQuery = q
        destHits = null
        screen = Screen.Destination
        viewModelScope.launch {
            val hits = runCatching { withContext(io) { placeFinder.find(q, saved.home, language) } }.getOrDefault(emptyList())
            if (destQuery != q || screen != Screen.Destination) return@launch
            destHits = hits
            val first = hits.firstOrNull()
            if (first != null) speak(R.string.dest_confirm_tts, first.address) else speak(R.string.dest_not_found, q)
        }
    }

    /** "इथे जायचं": the Ride screen for this place. */
    fun chooseDestination(place: Place) {
        speaker.stop()
        rideTo = place
        screen = Screen.Ride
    }

    /** Nothing found on the map: hand the words to the cab app to search. */
    fun useSpokenDestination() = chooseDestination(Place(destQuery))

    fun leaveRide() {
        rideTo = null
        screen = Screen.Home
    }

    // ---- Phone book ----

    /**
     * The phone's contacts, read on [io] and kept in memory only (refreshed after 5 minutes). Empty without
     * READ_CONTACTS. Nothing is written to disk or sent anywhere.
     */
    suspend fun phoneBook(): List<PhoneContact> {
        if (!contacts.hasPermission()) return emptyList()
        val now = System.currentTimeMillis()
        contactIndex?.takeIf { now - contactIndexAt < 5 * 60_000 }?.let { return it }
        val fresh = withContext(io) { contacts.load() }
        contactIndex = fresh
        contactIndexAt = now
        return fresh
    }

    /** "सुनीलला फोन लाव" for someone not saved in Nexus: ask for the phone book first if needed. */
    fun lookUpContact(name: String, ask: Ask) {
        if (!contacts.hasPermission()) {
            screen = Screen.ContactsPermission(name, ask)
            return
        }
        viewModelScope.launch {
            when (val found = findContact(name, phoneBook(), dial)) {
                is ContactLookup.Clear -> {
                    val person = found.match.person
                    if (ask == Ask.MESSAGE && !found.exact) {
                        // Guard: a phone-book name that only sounds like what was said is confirmed first.
                        track(EventType.PICKER_SHOWN, source = currentSource)
                        screen = Screen.ChooseContact(name, ask, listOf(person))
                        speak(R.string.which_one)
                    } else if (ask == Ask.MESSAGE) {
                        screen = Screen.Home
                        openWhatsAppFor(person)
                    } else {
                        // Only an exact name skips the question; the countdown still has Stop.
                        screen = if (found.exact) Screen.Calling(person) else Screen.Confirm(person)
                    }
                }
                is ContactLookup.Choose -> {
                    track(EventType.PICKER_SHOWN, source = currentSource)
                    screen = Screen.ChooseContact(name, ask, found.options.map { it.person })
                    speak(R.string.which_one)
                }
                ContactLookup.NotFound -> {
                    screen = Screen.ContactNotFound(name)
                    speak(R.string.contact_not_found, name)
                }
            }
        }
    }

    /** The answer to the phone-book permission card (or Android's dialog after it). */
    fun contactsAnswered(granted: Boolean) {
        permissionAnswered(Perm.CONTACTS, granted)
        val pending = screen as? Screen.ContactsPermission ?: return
        if (granted && contacts.hasPermission()) {
            contactIndex = null
            lookUpContact(pending.name, pending.ask)
        } else {
            screen = Screen.ContactNotFound(pending.name)
            speak(R.string.contact_not_found, pending.name)
        }
    }

    fun chooseContact(person: Person, ask: Ask) {
        if (ask == Ask.MESSAGE) {
            screen = Screen.Home
            openWhatsAppFor(person)
        } else {
            startCall(person)
        }
    }

    /** Phone-book matches for a dictated message's "who?" (empty when none or no permission). */
    var draftChoices by mutableStateOf<List<Person>>(emptyList())
        private set

    /** Finds a dictated message's unsaved recipient ("Sunil la sang ki ...") in the phone book. */
    fun resolveDraftRecipient() {
        val d = draft ?: return
        val asked = d.askedName ?: return
        if (d.person != null || d.options.isNotEmpty() || !contacts.hasPermission()) return
        viewModelScope.launch {
            when (val found = findContact(asked, phoneBook(), dial)) {
                is ContactLookup.Clear -> if (draft === d) {
                    setDraftPerson(found.match.person)
                    // Guard: a phone-book name that only sounds alike is confirmed before Send appears.
                    if (!found.exact) draft = draft?.copy(sure = false)
                }
                is ContactLookup.Choose -> draftChoices = found.options.map { it.person }
                ContactLookup.NotFound -> draftChoices = emptyList()
            }
        }
    }

    fun hasContactsPermission(): Boolean = contacts.hasPermission()

    fun speak(res: Int, vararg args: Any) {
        say(getApplication<Application>().inLanguage(language).getString(res, *args))
    }

    /** Everything Nexus says aloud goes through here, so tests can check what was said and when. */
    fun say(text: String) {
        spoken += text
        if (spoken.size > 20) spoken.removeAt(0)
        speaker.speak(language, text)
    }

    /** The last 20 spoken lines, in memory only (tests read it; nothing is stored or uploaded). */
    @androidx.annotation.VisibleForTesting
    internal val spoken = mutableListOf<String>()

    /** A short example of what to say, in the parent's language, with a real family name. */
    fun examplePhrase(): String =
        getApplication<Application>().inLanguage(language).getString(R.string.try_phrase, exampleName())

    /** The name used in examples: the Ask-family person's spoken alias or name, else a sample name. */
    fun exampleName(): String {
        val person = saved.people.firstOrNull { it.askFamily } ?: saved.people.firstOrNull()
        return person?.alias?.ifBlank { nameOf(person) } ?: getApplication<Application>().inLanguage(language).getString(R.string.sample_name)
    }

    /**
     * "Didn't understand" (UX sprint 1, fix #3): the people the words may have been about, best first, for
     * one-tap actions. A name or alias that appears in what was heard comes first, then everyone else.
     */
    fun candidatePeople(): List<Person> {
        val words = heard.lowercase().split(Regex("""[\s,.!?]+""")).filter { it.length >= 2 }
        fun mentioned(p: Person) = words.any { w ->
            val stem = w.removeSuffix("ला").removeSuffix("ना").removeSuffix("ko").removeSuffix("la")
            p.names.any { nameMatches(it, stem) || nameMatches(it, w) }
        }
        val (hit, rest) = saved.people.partition(::mentioned)
        return hit + rest
    }

    // ---- Calls and messages ----

    fun startCall(person: Person) {
        screen = Screen.Calling(person)
    }

    fun openWhatsAppFor(person: Person) {
        record(Event.WHATSAPP)
        // Coach before the handoff: Nexus's voice carries on while WhatsApp opens.
        if (isInstalled(getApplication(), "com.whatsapp")) speak(R.string.coach_whatsapp_chat, nameOf(person))
        if (openWhatsApp(getApplication(), person.phone, "")) actionOpened(AppCategory.WHATSAPP)
        else notice = getApplication<Application>().getString(R.string.whatsapp_missing)
    }

    // ---- Dictated messages ----

    fun openDraft(dictation: Dictation) {
        guardMessage(dictation.text, dictation.rawBody).reason?.let { block(it); return }
        if (dictation.options.isNotEmpty() || (dictation.person != null && !dictation.sure)) {
            track(EventType.GUARD_BLOCKED, guard = GuardReason.NAME_UNSURE, source = currentSource)
        }
        draft = dictation
        draftChoices = dictation.options
        draftVersion += 1
        screen = Screen.Message
        resolveDraftRecipient()
    }

    fun setDraftText(text: String) {
        draft = draft?.copy(text = text)
    }

    fun setDraftPerson(person: Person) {
        draftChoices = emptyList()
        // A face or contact the parent tapped is a confirmed name.
        draft = draft?.copy(person = person, askedName = null, sure = true, options = emptyList())
        draftVersion += 1
    }

    /** "✓ हो, पंकज": the roughly heard name is the right person. */
    fun confirmDraftPerson() {
        draft = draft?.copy(sure = true)
        draftVersion += 1
    }

    /** "दुसरं कोणी": back to the faces to pick who. */
    fun clearDraftPerson() {
        draft = draft?.copy(person = null, sure = true)
        draftVersion += 1
    }

    /** Live words while "Speak again" listens, shown big on the message screen. */
    var dictationLive by mutableStateOf("")
        private set

    /**
     * "Speak again" on the message screen: listens through pauses until Done (or a quiet spell, or 60 s),
     * then the new words replace the message (or the whole command is re-read).
     */
    fun dictateAgain() {
        if (!canListen()) return
        speaker.stop()
        dictating = true
        dictationLive = ""
        voice.start(language, voiceMode, longForm = true, onText = { dictationLive = it }) { said ->
            dictating = false
            said?.let(::redictate)
        }
    }

    fun doneDictating() {
        if (dictating) voice.done()
    }

    /** Stop while dictating: keep the message as it was. */
    fun stopDictating() {
        voice.cancel()
        dictating = false
        dictationLive = ""
    }

    fun redictate(said: String) {
        val current = draft ?: return
        val again = readDictation(said, saved)
        draft = if (again != null && again.text.isNotBlank()) {
            current.copy(
                person = again.person ?: current.person,
                askedName = if (again.person != null) null else current.askedName,
                sure = if (again.person != null) again.sure else current.sure,
                channel = if (again.channel != Channel.ANY) again.channel else current.channel,
                text = again.text,
                rawBody = again.rawBody,
            )
        } else {
            current.copy(text = polishMessage(said), rawBody = said)
        }
        draftVersion += 1
        resolveDraftRecipient()
    }

    /** Reads the draft back in the parent's language, or asks who / what. */
    fun speakDraft() {
        val d = draft ?: return
        val person = d.person
        when {
            person == null -> speak(R.string.msg_who)
            !d.sure -> speak(R.string.msg_confirm_name, nameOf(person))
            d.text.isBlank() -> speak(R.string.msg_what)
            else -> speak(R.string.msg_confirm_tts, nameOf(person), d.text)
        }
    }

    /** Opens WhatsApp or the SMS app with the message typed in. Returns false when nothing could open. */
    fun sendDraft(via: Channel): Boolean {
        val d = draft ?: return false
        val person = d.person ?: return false
        if (!d.sure) return false
        // The guard runs again at send: the words may have been edited or spoken again since.
        guardMessage(d.text, d.rawBody).reason?.let { block(it); return false }
        val app = getApplication<Application>()
        val waPkg = if (via == Channel.SMS) null else whatsAppPackage(app)
        // One coaching line before the handoff: where to press in the other app.
        if (via == Channel.SMS) speak(R.string.coach_sms_send) else if (waPkg != null) speak(R.string.coach_whatsapp_send)
        val opened = if (via == Channel.SMS) {
            openSmsDraft(app, person.phone, d.text)
        } else {
            waPkg?.let { openWhatsAppText(app, it, person.phone, d.text) } ?: false
        }
        if (!opened) {
            notice = app.getString(R.string.msg_not_opened)
            return false
        }
        record(Event.MESSAGE)
        actionOpened(if (via == Channel.SMS) AppCategory.SMS else AppCategory.WHATSAPP)
        if (!saved.messageExplained) update(saved.copy(messageExplained = true))
        draft = null
        screen = Screen.Home
        return true
    }

    fun cancelDraft() {
        if (draft != null) cancelled(CommandType.MESSAGE)
        speaker.stop()
        voice.cancel()
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
        const val MAX_PEOPLE = 6
    }
}

class MainActivity : AppCompatActivity() {
    private val model: NexusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installCrashCounter(applicationContext)
        if (savedInstanceState == null) consume(intent)
        setContent { NexusApp(model) }
    }

    override fun onStart() {
        super.onStart()
        model.cameBack()
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
