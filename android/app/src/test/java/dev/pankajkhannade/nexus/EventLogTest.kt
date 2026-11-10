package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The on-device log: round trip, 90-day retention, size cap, delete. Robolectric only for org.json. */
@RunWith(RobolectricTestRunner::class)
class EventLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = 86_400_000L
    private var now = 200 * day

    private fun log() = EventLog(tmp.root, now = { now })

    @Test fun everyFieldRoundTripsThroughTheFile() {
        val e = UsageEvent(now, EventType.COMMAND, Lang.HI, CommandType.RIDE_TO, Source.VOICE, EndReason.SILENCE, Perm.MIC, AppCategory.CAB, true, 1234, 56, 7, 3)
        log().append(e)
        assertEquals(listOf(e), log().all())
    }

    @Test fun junkAndUnknownLinesAreDropped() {
        java.io.File(tmp.root, "events.jsonl").writeText("not json\n{\"t\":1,\"e\":\"from_the_future\"}\n" + UsageEvent(now, EventType.APP_OPEN).toJson() + "\n")
        assertEquals(listOf(EventType.APP_OPEN), log().all().map { it.type })
    }

    @Test fun eventsOlderThan90DaysArePruned() {
        val l = log()
        l.append(UsageEvent(now - 100 * day, EventType.APP_OPEN))
        l.append(UsageEvent(now - 10 * day, EventType.APP_OPEN))
        l.append(UsageEvent(now, EventType.CRASH))
        assertEquals(2, l.all().size)
        // And on disk, for the next process
        assertEquals(2, log().all().size)
        now += 85 * day
        assertEquals(listOf(EventType.CRASH), log().all().map { it.type })
    }

    @Test fun theLogIsCapped() {
        val l = log()
        repeat(MAX_EVENTS + 5) { l.append(UsageEvent(now, EventType.FOREGROUND)) }
        assertEquals(MAX_EVENTS, l.all().size)
    }

    @Test fun clearDeletesTheFile() {
        val l = log()
        l.append(UsageEvent(now, EventType.APP_OPEN))
        l.clear()
        assertTrue(l.all().isEmpty())
        assertFalse(java.io.File(tmp.root, "events.jsonl").exists())
        assertTrue(log().all().isEmpty())
    }

    @Test fun crashCounterRecordsACountAndPassesTheCrashOn() {
        val l = log()
        var passed: Throwable? = null
        val handler = CrashCounter(l) { _, e -> passed = e }
        val boom = IllegalStateException("secret text that must not be stored")
        handler.uncaughtException(Thread.currentThread(), boom)
        assertEquals(listOf(EventType.CRASH), l.all().map { it.type })
        assertEquals(boom, passed)
        assertFalse(java.io.File(tmp.root, "events.jsonl").readText().contains("secret"))
    }
}
