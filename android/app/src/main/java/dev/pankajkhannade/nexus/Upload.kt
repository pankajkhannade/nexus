package dev.pankajkhannade.nexus

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Analytics choices, on the phone. The upload is OFF until the child turns it on after reading the consent
 * screen. The install ID is a random UUID made at that moment (never the Android ID or advertising ID), and
 * a new one is made after "Delete all usage data".
 */
class AnalyticsPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nexus_analytics", Context.MODE_PRIVATE)

    val uploadOn: Boolean get() = prefs.getBoolean(KEY_CONSENT, false)
    val testerSource: TesterSource get() = TesterSource.from(prefs.getString(KEY_SOURCE, "").orEmpty())
    val lastUploadedDay: Long get() = prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE)
    val installId: String? get() = prefs.getString(KEY_ID, null)
    /** The parent's language code, so the background upload can tag the day without opening the main store. */
    var language: String
        get() = prefs.getString(KEY_LANG, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_LANG, value).apply() }

    fun setUpload(on: Boolean) {
        val edit = prefs.edit().putBoolean(KEY_CONSENT, on)
        if (on && installId == null) edit.putString(KEY_ID, UUID.randomUUID().toString())
        edit.commit()
    }

    fun setTesterSource(source: TesterSource) { prefs.edit().putString(KEY_SOURCE, source.key).commit() }

    fun markUploaded(day: Long) { prefs.edit().putLong(KEY_LAST_DAY, day).commit() }

    /** Delete all usage data: consent off, install ID and upload progress gone. The tester tag goes too. */
    fun clear() { prefs.edit().clear().commit() }

    private companion object {
        const val KEY_CONSENT = "upload_consent"
        const val KEY_SOURCE = "tester_source"
        const val KEY_LAST_DAY = "last_uploaded_day"
        const val KEY_ID = "install_id"
        const val KEY_LANG = "parent_language"
    }
}

/** Days ready to send: finished days (before today) after the last sent one, at most the last 30. */
fun pendingUploadDays(events: List<UsageEvent>, lastUploaded: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> =
    events.map { dayOf(it.t, zone) }.toSortedSet()
        .filter { it.isBefore(today) && it.toEpochDay() > lastUploaded && !it.isBefore(today.minusDays(30)) }

/** The request body: aggregate counts per day, under the random install ID. Nothing else leaves the phone. */
fun uploadPayload(events: List<UsageEvent>, days: List<LocalDate>, installId: String, tester: TesterSource, appVersion: String, language: String, zone: ZoneId = ZoneId.systemDefault()): JSONObject =
    JSONObject()
        .put("v", 1)
        .put("install_id", installId)
        .put("tester_source", tester.key)
        .put("app_version", appVersion)
        .put("lang", Lang.of(language).key)
        .put("days", JSONArray().apply {
            days.forEach { day ->
                val counts = dailyCounts(events, day, zone).filterKeys { COUNT_KEY.matches(it) }
                put(JSONObject().put("day", day.toString()).put("counts", JSONObject(counts as Map<*, *>)))
            }
        })

fun interface MetricsTransport {
    /** POSTs [body] as JSON and returns the HTTP status (or -1 when the network failed). */
    fun post(url: String, body: String): Int
}

object HttpTransport : MetricsTransport {
    override fun post(url: String, body: String): Int = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
        c.responseCode.also { c.disconnect() }
    } catch (e: Exception) {
        -1
    }
}

enum class UploadOutcome { NO_CONSENT, NOTHING_TO_SEND, SENT, RETRY, REJECTED }

/**
 * One upload run. Consent off = returns at once and touches nothing (no network). Kept apart from the Worker
 * so JVM tests can drive it with a fake [MetricsTransport].
 */
fun runUpload(
    prefs: AnalyticsPrefs,
    log: EventLog,
    transport: MetricsTransport,
    url: String,
    appVersion: String,
    language: String,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): UploadOutcome {
    if (!prefs.uploadOn) return UploadOutcome.NO_CONSENT
    val id = prefs.installId ?: return UploadOutcome.NO_CONSENT
    if (url.isBlank()) return UploadOutcome.NOTHING_TO_SEND
    val events = log.all()
    val days = pendingUploadDays(events, prefs.lastUploadedDay, today, zone)
    if (days.isEmpty()) return UploadOutcome.NOTHING_TO_SEND
    val status = transport.post(url, uploadPayload(events, days, id, prefs.testerSource, appVersion, language, zone).toString())
    return when (status) {
        in 200..299 -> { prefs.markUploaded(days.last().toEpochDay()); UploadOutcome.SENT }
        // A 4xx other than rate limiting won't get better by retrying the same body.
        in 400..499 -> if (status == 429) UploadOutcome.RETRY else { prefs.markUploaded(days.last().toEpochDay()); UploadOutcome.REJECTED }
        else -> UploadOutcome.RETRY
    }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = AnalyticsPrefs(ctx)
        return when (runUpload(prefs, EventLog.get(ctx), transport, BuildConfig.METRICS_URL, BuildConfig.VERSION_NAME, prefs.language)) {
            UploadOutcome.RETRY -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        /** Tests swap this for a fake. */
        @Volatile var transport: MetricsTransport = HttpTransport
    }
}

object MetricsUpload {
    const val WORK = "nexus-metrics-daily"

    /** Once a day on any network (unmetered not required), with exponential backoff on failure. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UploadWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request) }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    }
}
