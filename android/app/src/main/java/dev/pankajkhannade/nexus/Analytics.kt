package dev.pankajkhannade.nexus

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Privacy-first usage analytics (docs/metrics.md). Every field of [UsageEvent] is an enum, a number or a
 * boolean: the schema has no free-text field, so audio, message text, contact names, phone numbers and
 * destinations cannot be logged. UsageEventSchemaTest guards this.
 */
enum class EventType(val key: String) {
    APP_OPEN("app_open"),
    /** The app came to the front again (not after an action Nexus opened). */
    FOREGROUND("foreground"),
    CRASH("crash"),
    SETUP_START("setup_start"),
    SETUP_STEP("setup_step"),
    PERMISSION("permission"),
    SETUP_DONE("setup_done"),
    VOICE_START("voice_start"),
    VOICE_END("voice_end"),
    RECOGNIZER_ERROR("recognizer_error"),
    /** What a voice session (or a tile tap) was understood as. [UsageEvent.ok] = understood. */
    COMMAND("command"),
    /** The "which one?" picker (two Rahuls). */
    PICKER_SHOWN("picker_shown"),
    /** Stop, No, Back or Cancel before anything opened. */
    CANCEL("cancel"),
    ACTION_OPENED("action_opened"),
    /** Nexus came back after an action; [UsageEvent.ms] = time away. Under 5 s counts as a wrong action. */
    ACTION_RETURN("action_return"),
    NEAR_TILE("near_tile"),
    PLAY_REDIRECT("play_redirect");

    companion object {
        fun from(key: String): EventType? = entries.firstOrNull { it.key == key }
    }
}

/** The recogniser language of a session (the parent's chosen language). Script is not used to guess. */
enum class Lang(val key: String) {
    MR("mr"), HI("hi"), EN("en"), OTHER("other");

    companion object {
        fun of(code: String): Lang = entries.firstOrNull { it.key == code } ?: OTHER
        fun from(key: String): Lang? = entries.firstOrNull { it.key == key }
    }
}

enum class CommandType(val key: String) {
    CALL("call"), MESSAGE("message"), WHATSAPP("whatsapp"), RIDE_HOME("ride_home"), RIDE_TO("ride_to"),
    NEAR_HOME("near_home"), FIND_CONTACT("find_contact"), SAFETY("safety"),
    /** Reserved for roadmap N2 (video calls by voice). */
    VIDEO_CALL("video_call"),
    UNKNOWN("unknown");

    companion object {
        fun from(key: String): CommandType? = entries.firstOrNull { it.key == key }
        fun of(command: Command): CommandType = when (command) {
            is Command.Message -> MESSAGE
            Command.RideHome -> RIDE_HOME
            is Command.RideTo -> RIDE_TO
            is Command.Call -> CALL
            is Command.WhatsApp -> WHATSAPP
            is Command.FindContact -> FIND_CONTACT
            is Command.OpenNeed -> NEAR_HOME
            Command.Unknown -> UNKNOWN
        }
    }
}

/** How the parent asked: by voice, by a tile tap, or typed in Family settings (test command, not counted as voice). */
enum class Source(val key: String) {
    VOICE("voice"), TILE("tile"), TYPED("typed");

    companion object {
        fun from(key: String): Source? = entries.firstOrNull { it.key == key }
    }
}

/** Why a voice session ended. CUT_OFF = the recogniser failed after words were heard. */
enum class EndReason(val key: String) {
    USER_DONE("user_done"), COMPLETE("complete"), SILENCE("silence"), NO_SPEECH("no_speech"),
    MAX_TIME("max_time"), ERROR("error"), CUT_OFF("cut_off"), CANCELLED("cancelled");

    companion object {
        fun from(key: String): EndReason? = entries.firstOrNull { it.key == key }
    }
}

enum class Perm(val key: String) {
    MIC("mic"), CONTACTS("contacts"), CALL("call");

    companion object {
        fun from(key: String): Perm? = entries.firstOrNull { it.key == key }
    }
}

/** The kind of app an action opened. Never a package name of a person's choice, never a contact or a place. */
enum class AppCategory(val key: String) {
    DIALER("dialer"), WHATSAPP("whatsapp"), SMS("sms"), CAB("cab"), MAPS("maps"), GROCERY("grocery"),
    PHARMACY("pharmacy"), DOCTOR("doctor"), HOME_SERVICE("home_service"), PLAY_STORE("play_store"),
    EMERGENCY("emergency"), VIDEO_CALL("video_call");

    companion object {
        fun from(key: String): AppCategory? = entries.firstOrNull { it.key == key }
        fun of(need: Need): AppCategory = when (need) {
            Need.GROCERY -> GROCERY
            Need.MEDICINE -> PHARMACY
            Need.DOCTOR -> DOCTOR
            Need.RIDE -> CAB
            Need.HOME_SERVICE -> HOME_SERVICE
            Need.EMERGENCY -> EMERGENCY
        }
    }
}

/** Who the tester family is, set by the child in How Nexus is doing. Tags every number against bias. */
enum class TesterSource(val key: String) {
    UNSET("unset"), FAMILY("family"), FRIEND("friend"), STRANGER("stranger");

    companion object {
        fun from(key: String): TesterSource = entries.firstOrNull { it.key == key } ?: UNSET
    }
}

/**
 * One usage event. [t] is epoch milliseconds. Optional fields are null when they don't apply.
 * [ms] is a duration (session length, time to action, time away, setup time); [chars] an utterance length;
 * [code] an Android recogniser error code; [step] a setup step number.
 */
data class UsageEvent(
    val t: Long,
    val type: EventType,
    val lang: Lang? = null,
    val command: CommandType? = null,
    val source: Source? = null,
    val end: EndReason? = null,
    val perm: Perm? = null,
    val category: AppCategory? = null,
    val ok: Boolean? = null,
    val ms: Long? = null,
    val chars: Int? = null,
    val code: Int? = null,
    val step: Int? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("t", t); put("e", type.key)
        lang?.let { put("lang", it.key) }
        command?.let { put("cmd", it.key) }
        source?.let { put("src", it.key) }
        end?.let { put("end", it.key) }
        perm?.let { put("perm", it.key) }
        category?.let { put("cat", it.key) }
        ok?.let { put("ok", it) }
        ms?.let { put("ms", it) }
        chars?.let { put("chars", it) }
        code?.let { put("code", it) }
        step?.let { put("step", it) }
    }.toString()

    companion object {
        /** Reads one stored line; unknown keys and values are dropped (an older or newer build wrote it). */
        fun parse(line: String): UsageEvent? = runCatching {
            val j = JSONObject(line)
            UsageEvent(
                t = j.getLong("t"),
                type = EventType.from(j.getString("e")) ?: return null,
                lang = j.optString("lang").let(Lang::from),
                command = j.optString("cmd").let(CommandType::from),
                source = j.optString("src").let(Source::from),
                end = j.optString("end").let(EndReason::from),
                perm = j.optString("perm").let(Perm::from),
                category = j.optString("cat").let(AppCategory::from),
                ok = if (j.has("ok")) j.getBoolean("ok") else null,
                ms = if (j.has("ms")) j.getLong("ms") else null,
                chars = if (j.has("chars")) j.getInt("chars") else null,
                code = if (j.has("code")) j.getInt("code") else null,
                step = if (j.has("step")) j.getInt("step") else null,
            )
        }.getOrNull()
    }
}

const val RETENTION_DAYS = 90
private const val DAY_MS = 86_400_000L
/** A hard cap so a stuck loop can't fill the phone: about 10x a heavy user's 90 days. */
const val MAX_EVENTS = 50_000

/**
 * The on-device event log: one JSON line per event in files/analytics/events.jsonl. Not backed up
 * (allowBackup=false), never uploaded as-is (only [dailyCounts] aggregates, and only with consent).
 */
class EventLog(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    private val file = File(dir, "events.jsonl")
    private var cache: MutableList<UsageEvent>? = null

    @Synchronized
    fun all(): List<UsageEvent> = load().toList()

    @Synchronized
    fun append(event: UsageEvent) {
        val list = load()
        list += event
        dir.mkdirs()
        if (list.size > MAX_EVENTS || list.first().t < now() - RETENTION_DAYS * DAY_MS) {
            prune(list)
            rewrite(list)
        } else {
            file.appendText(event.toJson() + "\n")
        }
    }

    /** Delete all usage data: the log file goes, and memory with it. */
    @Synchronized
    fun clear() {
        cache = mutableListOf()
        file.delete()
    }

    private fun load(): MutableList<UsageEvent> {
        cache?.let { return it }
        val read = if (file.exists()) file.readLines().mapNotNull { UsageEvent.parse(it) }.toMutableList() else mutableListOf()
        val before = read.size
        prune(read)
        if (read.size != before) rewrite(read)
        cache = read
        return read
    }

    private fun prune(list: MutableList<UsageEvent>) {
        val cutoff = now() - RETENTION_DAYS * DAY_MS
        list.removeAll { it.t < cutoff }
        while (list.size > MAX_EVENTS) list.removeAt(0)
    }

    private fun rewrite(list: List<UsageEvent>) {
        dir.mkdirs()
        val tmp = File(dir, "events.tmp")
        tmp.writeText(list.joinToString("") { it.toJson() + "\n" })
        tmp.renameTo(file)
    }

    companion object {
        fun dirFor(context: Context) = File(context.filesDir, "analytics")

        @Volatile private var shared: EventLog? = null

        /** One log per process, so the ViewModel and the crash handler don't race on the file. */
        fun get(context: Context): EventLog = shared ?: synchronized(this) {
            shared ?: EventLog(dirFor(context.applicationContext)).also { shared = it }
        }
    }
}

/**
 * Crash counting: the default handler appends one CRASH event (a count only, no stack trace, no message)
 * and then hands over to Android's handler, so the app still crashes normally.
 */
fun installCrashCounter(context: Context) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    if (previous is CrashCounter) return
    Thread.setDefaultUncaughtExceptionHandler(CrashCounter(EventLog.get(context), previous))
}

class CrashCounter(private val log: EventLog, private val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching { log.append(UsageEvent(System.currentTimeMillis(), EventType.CRASH)) }
        next?.uncaughtException(t, e)
    }
}
