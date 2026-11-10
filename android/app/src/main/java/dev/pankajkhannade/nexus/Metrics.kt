package dev.pankajkhannade.nexus

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** A share with its raw counts: with 5-10 testers a percentage alone lies (docs/metrics.md). */
data class Ratio(val num: Int, val den: Int) {
    val percent: Int? get() = if (den == 0) null else (100.0 * num / den).roundToInt()
    override fun toString(): String = percent?.let { "$num/$den ($it%)" } ?: "$num/$den"
}

/** One voice attempt, classified. A retry is a new attempt within 30 s of a failed one. */
data class Attempt(
    val t: Long,
    val start: Long,
    val lang: Lang,
    val command: CommandType,
    val understood: Boolean,
    val cancelled: Boolean,
    val wrongAction: Boolean,
    val retry: Boolean,
) {
    val failed: Boolean get() = !understood || cancelled || wrongAction
    val success: Boolean get() = !failed
}

const val RETRY_WINDOW_MS = 30_000L
const val WRONG_ACTION_MS = 5_000L
const val LONG_AWAY_MS = 60_000L
const val CALL_CONNECTED_PROXY_MS = 10_000L
private const val HOUR_MS = 3_600_000L

/**
 * Splits voice COMMAND events into attempts. A CANCEL or a return to Nexus within [WRONG_ACTION_MS] of an
 * action, before the next command, marks the attempt as failed.
 */
fun classifyAttempts(events: List<UsageEvent>): List<Attempt> {
    val sorted = events.sortedBy { it.t }
    val out = mutableListOf<Attempt>()
    val commands = sorted.withIndex().filter { it.value.type == EventType.COMMAND }
    commands.forEachIndexed { n, (i, e) ->
        if (e.source != Source.VOICE) return@forEachIndexed
        val until = commands.getOrNull(n + 1)?.index ?: sorted.size
        // Only events before the next command or voice session count against this attempt.
        val window = sorted.subList(i + 1, until).takeWhile { it.type != EventType.VOICE_START }
        val cancelled = window.any { it.type == EventType.CANCEL }
        val wrong = window.any { it.type == EventType.ACTION_RETURN && (it.ms ?: Long.MAX_VALUE) < WRONG_ACTION_MS }
        val start = e.t - (e.ms ?: 0)
        val prev = out.lastOrNull()
        val retry = prev != null && prev.failed && start - prev.t <= RETRY_WINDOW_MS
        out += Attempt(e.t, start, e.lang ?: Lang.OTHER, e.command ?: CommandType.UNKNOWN, e.ok == true, cancelled, wrong, retry)
    }
    return out
}

data class SetupMetrics(
    val started: Boolean,
    val finished: Boolean,
    val durationMs: Long?,
    /** Furthest step reached (0-based); for an unfinished setup this is the drop-off step. */
    val furthestStep: Int?,
    val dropOffStep: Int?,
    /** Last answer per permission: true = allowed. */
    val permissions: Map<Perm, Boolean>,
)

data class MetricsReport(
    val today: LocalDate,
    val events: Int,
    val setup: SetupMetrics,
    val installDay: LocalDate?,
    val activeDays: Int,
    val daysUsedLast7: Int,
    /** Days used in each full 7-day week since setup, oldest first. */
    val daysPerWeek: List<Int>,
    val voiceAttempts: Int,
    val firstTry: Ratio,
    val firstTryByLang: Map<Lang, Ratio>,
    val firstTryByCommand: Map<CommandType, Ratio>,
    val notUnderstood: Ratio,
    val retries: Ratio,
    val cancels: Ratio,
    val wrongActions: Ratio,
    val pickers: Ratio,
    val cutOffs: Ratio,
    val recognizerErrors: Map<Int, Int>,
    val medianUtteranceChars: Int?,
    val medianTimeToActionMs: Long?,
    /** First voice success within 24 h of setup. null = setup not finished, or the 24 h are still running. */
    val activated: Boolean?,
    val timeToFirstValueMs: Long?,
    val timeToFirstVoiceSuccessMs: Long?,
    /** Per command type: understood voice commands vs tile taps. */
    val voiceVsTile: Map<CommandType, Pair<Int, Int>>,
    val voiceTotal: Int,
    val tileTotal: Int,
    val actionsOpened: Map<AppCategory, Int>,
    /** Returns after a call of 10 s or more: a proxy for a connected call (no phone-state permission). */
    val callsLikelyConnected: Ratio,
    /** Returns after 60 s or more away in the opened app. */
    val longAway: Ratio,
    val nearTiles: Int,
    val playRedirects: Int,
    /** Active on day 1 / 7 / 30 after setup; null until that day arrives. */
    val d1: Boolean?,
    val d7: Boolean?,
    val d30: Boolean?,
    val sessions: Int,
    val crashes: Int,
    val crashFree: Ratio,
)

private val ACTIVE_TYPES = setOf(EventType.APP_OPEN, EventType.FOREGROUND, EventType.COMMAND, EventType.VOICE_START, EventType.ACTION_OPENED)

fun dayOf(t: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

private fun <T : Comparable<T>> List<T>.median(): T? = if (isEmpty()) null else sorted()[size / 2]

fun computeMetrics(events: List<UsageEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault()): MetricsReport {
    val sorted = events.sortedBy { it.t }
    val today = dayOf(now, zone)
    fun of(type: EventType) = sorted.filter { it.type == type }

    // Setup
    val setupStart = of(EventType.SETUP_START).firstOrNull()
    val setupDone = of(EventType.SETUP_DONE).firstOrNull()
    val steps = of(EventType.SETUP_STEP).mapNotNull { it.step }
    val furthest = (steps + listOfNotNull(setupStart?.let { 0 })).maxOrNull()
    val setup = SetupMetrics(
        started = setupStart != null || setupDone != null,
        finished = setupDone != null,
        durationMs = setupDone?.ms ?: if (setupDone != null && setupStart != null) setupDone.t - setupStart.t else null,
        furthestStep = furthest,
        dropOffStep = if (setupDone == null) furthest else null,
        permissions = of(EventType.PERMISSION).filter { it.perm != null && it.ok != null }.associate { it.perm!! to it.ok!! },
    )
    val installDay = setupDone?.let { dayOf(it.t, zone) }

    // Activity
    val activeSet = sorted.filter { it.type in ACTIVE_TYPES }.map { dayOf(it.t, zone) }.toSet()
    val daysUsedLast7 = (0L..6L).count { today.minusDays(it) in activeSet }
    val daysPerWeek = installDay?.let { start ->
        val fullWeeks = ((today.toEpochDay() - start.toEpochDay() + 1) / 7).toInt()
        (0 until fullWeeks).map { w -> (0L..6L).count { start.plusDays(w * 7L + it) in activeSet } }
    }.orEmpty()
    fun activeOn(n: Long): Boolean? = installDay?.plusDays(n)?.let { d -> if (d.isAfter(today)) null else d in activeSet }

    // Voice
    val attempts = classifyAttempts(sorted)
    val firsts = attempts.filterNot { it.retry }
    fun ratioOf(list: List<Attempt>) = Ratio(list.count { it.success }, list.size)
    val voiceEnds = of(EventType.VOICE_END)
    val understood = attempts.filter { it.understood }

    // Activation and first value
    val doneAt = setupDone?.t
    val firstVoiceOk = doneAt?.let { s -> attempts.firstOrNull { it.success && it.t >= s } }
    val firstValue = doneAt?.let { s ->
        listOfNotNull(
            firstVoiceOk?.t,
            sorted.firstOrNull { it.t >= s && it.type == EventType.COMMAND && it.source == Source.TILE }?.t,
        ).minOrNull()
    }
    val activated = doneAt?.let { s ->
        when {
            firstVoiceOk != null && firstVoiceOk.t - s <= 24 * HOUR_MS -> true
            now - s < 24 * HOUR_MS -> null
            else -> false
        }
    }

    // Voice vs tile
    val commandEvents = of(EventType.COMMAND).filter { it.command != null && it.command != CommandType.UNKNOWN }
    val voiceVsTile = CommandType.entries.mapNotNull { c ->
        val v = commandEvents.count { it.command == c && it.source == Source.VOICE && it.ok == true }
        val tile = commandEvents.count { it.command == c && it.source == Source.TILE }
        if (v + tile == 0) null else c to (v to tile)
    }.toMap()

    // Actions
    val opened = of(EventType.ACTION_OPENED)
    val returns = of(EventType.ACTION_RETURN)
    val callReturns = returns.filter { it.category == AppCategory.DIALER }

    val sessions = of(EventType.APP_OPEN).size
    val crashes = of(EventType.CRASH).size

    return MetricsReport(
        today = today,
        events = sorted.size,
        setup = setup,
        installDay = installDay,
        activeDays = activeSet.size,
        daysUsedLast7 = daysUsedLast7,
        daysPerWeek = daysPerWeek,
        voiceAttempts = attempts.size,
        firstTry = ratioOf(firsts),
        firstTryByLang = firsts.groupBy { it.lang }.mapValues { ratioOf(it.value) },
        firstTryByCommand = firsts.filter { it.understood }.groupBy { it.command }.mapValues { ratioOf(it.value) },
        notUnderstood = Ratio(attempts.count { !it.understood }, attempts.size),
        retries = Ratio(attempts.count { it.retry }, attempts.size),
        cancels = Ratio(understood.count { it.cancelled }, understood.size),
        wrongActions = Ratio(returns.count { (it.ms ?: Long.MAX_VALUE) < WRONG_ACTION_MS }, opened.size),
        pickers = Ratio(of(EventType.PICKER_SHOWN).size, attempts.size),
        cutOffs = Ratio(voiceEnds.count { it.end == EndReason.CUT_OFF }, voiceEnds.size),
        recognizerErrors = of(EventType.RECOGNIZER_ERROR).mapNotNull { it.code }.groupingBy { it }.eachCount().toSortedMap(),
        medianUtteranceChars = voiceEnds.mapNotNull { it.chars }.filter { it > 0 }.median(),
        medianTimeToActionMs = of(EventType.COMMAND).filter { it.source == Source.VOICE && it.ok == true }.mapNotNull { it.ms }.median(),
        activated = activated,
        timeToFirstValueMs = if (doneAt != null && firstValue != null) firstValue - doneAt else null,
        timeToFirstVoiceSuccessMs = if (doneAt != null && firstVoiceOk != null) firstVoiceOk.t - doneAt else null,
        voiceVsTile = voiceVsTile,
        voiceTotal = voiceVsTile.values.sumOf { it.first },
        tileTotal = voiceVsTile.values.sumOf { it.second },
        actionsOpened = opened.mapNotNull { it.category }.groupingBy { it }.eachCount(),
        callsLikelyConnected = Ratio(callReturns.count { (it.ms ?: 0) >= CALL_CONNECTED_PROXY_MS }, callReturns.size),
        longAway = Ratio(returns.count { (it.ms ?: 0) >= LONG_AWAY_MS }, returns.size),
        nearTiles = of(EventType.NEAR_TILE).size,
        playRedirects = of(EventType.PLAY_REDIRECT).size,
        d1 = activeOn(1), d7 = activeOn(7), d30 = activeOn(30),
        sessions = sessions,
        crashes = crashes,
        crashFree = Ratio((sessions - crashes).coerceAtLeast(0), sessions),
    )
}

/**
 * The day's counts for the opt-in upload: integers under fixed keys built from enums, nothing else.
 * Attempts are classified over the whole log first, so a retry across midnight is still a retry.
 */
fun dailyCounts(events: List<UsageEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<String, Int> {
    val sorted = events.sortedBy { it.t }
    val counts = sortedMapOf<String, Int>()
    fun add(key: String, n: Int = 1) { if (n != 0) counts[key] = (counts[key] ?: 0) + n }
    val onDay = sorted.filter { dayOf(it.t, zone) == day }
    if (onDay.any { it.type in ACTIVE_TYPES }) add("active")
    onDay.forEach { e ->
        when (e.type) {
            EventType.APP_OPEN -> add("app_open")
            EventType.CRASH -> add("crash")
            EventType.SETUP_START -> add("setup_start")
            EventType.SETUP_DONE -> { add("setup_done"); e.ms?.let { add("setup_seconds", (it / 1000).toInt()) } }
            EventType.SETUP_STEP -> e.step?.let { add("setup_step_$it") }
            EventType.PERMISSION -> if (e.perm != null && e.ok != null) add("perm_${e.perm.key}_${if (e.ok) "granted" else "denied"}")
            EventType.VOICE_START -> add("voice_sessions")
            EventType.VOICE_END -> e.end?.let { add("voice_end_${it.key}") }
            EventType.RECOGNIZER_ERROR -> e.code?.let { add("recognizer_error_${it.coerceIn(0, 99)}") }
            EventType.COMMAND -> if (e.source == Source.TILE) e.command?.let { add("tile_${it.key}") }
            EventType.PICKER_SHOWN -> add("picker_shown")
            EventType.CANCEL -> add("cancel")
            EventType.ACTION_OPENED -> e.category?.let { add("opened_${it.key}") }
            EventType.ACTION_RETURN -> {
                val ms = e.ms ?: 0
                if (ms < WRONG_ACTION_MS) add("wrong_action")
                if (ms >= LONG_AWAY_MS) add("long_away")
                if (e.category == AppCategory.DIALER && ms >= CALL_CONNECTED_PROXY_MS) add("call_10s_proxy")
            }
            EventType.NEAR_TILE -> add("near_tile")
            EventType.PLAY_REDIRECT -> add("play_redirect")
            EventType.FOREGROUND -> add("foreground")
        }
    }
    classifyAttempts(sorted).filter { dayOf(it.t, zone) == day }.forEach { a ->
        add("voice_attempts")
        if (a.understood) add("voice_${a.command.key}") else add("not_understood")
        if (a.retry) add("retry")
        if (!a.retry) {
            add("first_try_${a.lang.key}")
            if (a.success) add("first_try_ok_${a.lang.key}")
        }
    }
    val doneAt = sorted.firstOrNull { it.type == EventType.SETUP_DONE }?.t
    if (doneAt != null) {
        val age = (day.toEpochDay() - dayOf(doneAt, zone).toEpochDay()).toInt()
        if (age >= 0) counts["age_days"] = age
        // Activation lands on the day of the first voice success, if it was within 24 h of setup.
        classifyAttempts(sorted).firstOrNull { it.success && it.t >= doneAt }?.let {
            if (it.t - doneAt <= 24 * HOUR_MS && dayOf(it.t, zone) == day) counts["activated"] = 1
        }
    }
    return counts
}

/** Only these characters can appear in an upload key: enum-built names, never words someone said. */
val COUNT_KEY = Regex("^[a-z0-9_]{1,48}$")

/** The shareable test summary: counts only, plus the tester source tag. */
fun summaryJson(report: MetricsReport, tester: TesterSource, appVersion: String, language: String): JSONObject {
    fun ratio(r: Ratio) = JSONObject().put("n", r.num).put("of", r.den).put("pct", r.percent ?: JSONObject.NULL)
    fun ratios(m: Map<out Enum<*>, Ratio>, key: (Enum<*>) -> String) = JSONObject().apply { m.forEach { (k, v) -> put(key(k), ratio(v)) } }
    return JSONObject().apply {
        put("nexus_summary", 1)
        put("app_version", appVersion)
        put("tester_source", tester.key)
        put("parent_language", language.ifBlank { "en" })
        put("today", report.today.toString())
        put("days_since_setup", report.installDay?.let { report.today.toEpochDay() - it.toEpochDay() } ?: JSONObject.NULL)
        put("setup", JSONObject().apply {
            put("finished", report.setup.finished)
            put("seconds", report.setup.durationMs?.div(1000) ?: JSONObject.NULL)
            put("drop_off_step", report.setup.dropOffStep ?: JSONObject.NULL)
            put("permissions", JSONObject().apply { report.setup.permissions.forEach { (p, ok) -> put(p.key, if (ok) "granted" else "denied") } })
        })
        put("active_days", report.activeDays)
        put("days_used_last_7", report.daysUsedLast7)
        put("days_per_week", org.json.JSONArray(report.daysPerWeek))
        put("voice_attempts", report.voiceAttempts)
        put("first_try_success", ratio(report.firstTry))
        put("first_try_by_language", ratios(report.firstTryByLang) { (it as Lang).key })
        put("first_try_by_command", ratios(report.firstTryByCommand) { (it as CommandType).key })
        put("not_understood", ratio(report.notUnderstood))
        put("retries", ratio(report.retries))
        put("cancels", ratio(report.cancels))
        put("wrong_actions", ratio(report.wrongActions))
        put("which_one_picker", ratio(report.pickers))
        put("cut_offs", ratio(report.cutOffs))
        put("recognizer_errors", JSONObject().apply { report.recognizerErrors.forEach { (c, n) -> put(c.toString(), n) } })
        put("median_utterance_chars", report.medianUtteranceChars ?: JSONObject.NULL)
        put("median_ms_to_action", report.medianTimeToActionMs ?: JSONObject.NULL)
        put("activated_24h", report.activated ?: JSONObject.NULL)
        put("minutes_to_first_value", report.timeToFirstValueMs?.div(60_000) ?: JSONObject.NULL)
        put("voice_vs_tile", JSONObject().apply { report.voiceVsTile.forEach { (c, p) -> put(c.key, JSONObject().put("voice", p.first).put("tile", p.second)) } })
        put("actions_opened", JSONObject().apply { report.actionsOpened.forEach { (c, n) -> put(c.key, n) } })
        put("calls_10s_proxy", ratio(report.callsLikelyConnected))
        put("away_60s", ratio(report.longAway))
        put("near_home_tiles", report.nearTiles)
        put("play_store_redirects", report.playRedirects)
        put("d1", report.d1 ?: JSONObject.NULL)
        put("d7", report.d7 ?: JSONObject.NULL)
        put("d30", report.d30 ?: JSONObject.NULL)
        put("sessions", report.sessions)
        put("crashes", report.crashes)
        put("crash_free", ratio(report.crashFree))
    }
}
