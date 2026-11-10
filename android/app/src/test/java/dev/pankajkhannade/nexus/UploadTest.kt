package dev.pankajkhannade.nexus

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import android.app.Application
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

/** The opt-in upload: nothing leaves the phone without consent; with it, aggregates once per finished day. */
@RunWith(RobolectricTestRunner::class)
class UploadTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 10)
    private val posts = mutableListOf<Pair<String, String>>()
    private var status = 200
    private val transport = MetricsTransport { url, body -> posts += url to body; status }
    private lateinit var prefs: AnalyticsPrefs
    private lateinit var log: EventLog

    @Before fun setUp() {
        prefs = AnalyticsPrefs(app).also { it.clear() }
        log = EventLog(tmp.root, now = { today.toEpochDay() * 86_400_000L })
        listOf(7L, 8L, 9L, 10L).forEach { d -> log.append(UsageEvent(today.minusDays(10 - d).toEpochDay() * 86_400_000L + 3_600_000, EventType.APP_OPEN)) }
    }

    private fun run() = runUpload(prefs, log, transport, "https://example.workers.dev/v1/daily", "1.0.0", "mr", today, ZoneOffset.UTC)

    @Test fun uploadIsOffByDefaultAndSendsNothing() {
        assertFalse(prefs.uploadOn)
        assertNull(prefs.installId)
        assertEquals(UploadOutcome.NO_CONSENT, run())
        assertTrue(posts.isEmpty())
    }

    @Test fun withConsentFinishedDaysAreSentOnceUnderARandomId() {
        prefs.setUpload(true)
        val id = prefs.installId!!
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id))
        assertEquals(UploadOutcome.SENT, run())
        val body = JSONObject(posts.single().second)
        assertEquals(id, body.getString("install_id"))
        val days = (0 until body.getJSONArray("days").length()).map { body.getJSONArray("days").getJSONObject(it).getString("day") }
        // Today (Nov 10) isn't finished yet, so it waits
        assertEquals(listOf("2026-11-07", "2026-11-08", "2026-11-09"), days)
        assertEquals(UploadOutcome.NOTHING_TO_SEND, run())
        assertEquals(1, posts.size)
    }

    @Test fun serverErrorsRetryAndBadRequestsDoNot() {
        prefs.setUpload(true)
        status = 503
        assertEquals(UploadOutcome.RETRY, run())
        status = -1
        assertEquals(UploadOutcome.RETRY, run())
        status = 429
        assertEquals(UploadOutcome.RETRY, run())
        status = 400
        assertEquals(UploadOutcome.REJECTED, run())
        assertEquals(UploadOutcome.NOTHING_TO_SEND, run())
    }

    @Test fun turningConsentOffStopsUploads() {
        prefs.setUpload(true)
        prefs.setUpload(false)
        assertEquals(UploadOutcome.NO_CONSENT, run())
        assertTrue(posts.isEmpty())
    }

    @Test fun clearingGivesANewInstallId() {
        prefs.setUpload(true)
        val first = prefs.installId
        prefs.clear()
        assertNull(prefs.installId)
        assertFalse(prefs.uploadOn)
        prefs.setUpload(true)
        assertNotEquals(first, prefs.installId)
    }

    @Test fun theWorkerWithoutConsentSucceedsWithoutTouchingTheNetwork() = runBlocking {
        UploadWorker.transport = transport
        try {
            val worker = TestListenableWorkerBuilder<UploadWorker>(app).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            assertTrue(posts.isEmpty())
        } finally {
            UploadWorker.transport = HttpTransport
        }
    }

    @Test fun consentSchedulesTheDailyWorkAndRevokingCancelsIt() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val wm = WorkManager.getInstance(app)
        MetricsUpload.schedule(app)
        assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfosForUniqueWork(MetricsUpload.WORK).get().single().state)
        MetricsUpload.cancel(app)
        assertEquals(WorkInfo.State.CANCELLED, wm.getWorkInfosForUniqueWork(MetricsUpload.WORK).get().single().state)
    }
}
