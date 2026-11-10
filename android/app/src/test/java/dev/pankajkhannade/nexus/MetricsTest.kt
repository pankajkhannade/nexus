package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Every metric in docs/metrics.md, on hand-built event logs. Pure JVM, no Android. */
class MetricsTest {
    private val utc = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 11, 1)
    private fun at(day: Long, sec: Long = 36_000) = (day0.toEpochDay() + day) * 86_400_000L + sec * 1000

    private fun voice(t: Long, ok: Boolean, lang: Lang = Lang.MR, cmd: CommandType = CommandType.CALL, ms: Long = 3_000) =
        UsageEvent(t, EventType.COMMAND, lang = lang, command = if (ok) cmd else CommandType.UNKNOWN, source = Source.VOICE, ok = ok, ms = ms)
    private fun start(t: Long) = UsageEvent(t, EventType.VOICE_START, lang = Lang.MR)
    private fun tile(t: Long, cmd: CommandType) = UsageEvent(t, EventType.COMMAND, command = cmd, source = Source.TILE, ok = true)
    private fun open(t: Long) = UsageEvent(t, EventType.APP_OPEN)
    private fun done(t: Long, ms: Long = 240_000) = UsageEvent(t, EventType.SETUP_DONE, ms = ms)
    private fun opened(t: Long, cat: AppCategory = AppCategory.DIALER) = UsageEvent(t, EventType.ACTION_OPENED, category = cat)
    private fun back(t: Long, ms: Long, cat: AppCategory = AppCategory.DIALER) = UsageEvent(t, EventType.ACTION_RETURN, category = cat, ms = ms)

    private fun report(vararg e: UsageEvent, now: Long = at(10)) = computeMetrics(e.toList(), now, utc)

    @Test fun ratioKeepsRawCountsAndHandlesZero() {
        assertEquals("7/9 (78%)", Ratio(7, 9).toString())
        assertNull(Ratio(0, 0).percent)
        assertEquals("0/0", Ratio(0, 0).toString())
        assertEquals(100, Ratio(3, 3).percent)
    }

    @Test fun firstTrySuccessCountsOnlyCleanFirstAttempts() {
        val r = report(
            // 1: understood, nothing went wrong -> success
            start(at(1, 0)), voice(at(1, 3), ok = true),
            // 2: not understood, then a retry 10 s later that works -> first attempt failed, retry not counted as first
            start(at(1, 100)), voice(at(1, 103), ok = false),
            start(at(1, 113)), voice(at(1, 116), ok = true),
            // 3: understood but the parent pressed Stop -> failed
            start(at(1, 500)), voice(at(1, 503), ok = true), UsageEvent(at(1, 504), EventType.CANCEL, command = CommandType.CALL),
            // 4: understood, dialer opened, back within 2 s -> wrong action
            start(at(1, 900)), voice(at(1, 903), ok = true), opened(at(1, 906)), back(at(1, 908), 2_000),
        )
        assertEquals(5, r.voiceAttempts)
        assertEquals(Ratio(1, 4), r.firstTry)
        assertEquals(Ratio(1, 5), r.retries)
        assertEquals(Ratio(1, 5), r.notUnderstood)
        assertEquals(Ratio(1, 4), r.cancels)
        assertEquals(Ratio(1, 1), r.wrongActions)
    }

    @Test fun aRetryMustFollowAFailureWithin30Seconds() {
        val late = report(voice(at(1, 0), ok = false), voice(at(1, 40), ok = true, ms = 3_000))
        assertEquals(Ratio(0, 2), late.retries)
        val soon = report(voice(at(1, 0), ok = false), voice(at(1, 32), ok = true, ms = 3_000))
        assertEquals(Ratio(1, 2), soon.retries)
        val afterSuccess = report(voice(at(1, 0), ok = true), voice(at(1, 5), ok = true, ms = 1_000))
        assertEquals(Ratio(0, 2), afterSuccess.retries)
    }

    @Test fun firstTryIsSplitByLanguageAndCommand() {
        val r = report(
            voice(at(1, 0), ok = true, lang = Lang.MR, cmd = CommandType.CALL),
            voice(at(1, 100), ok = false, lang = Lang.MR),
            voice(at(1, 200), ok = true, lang = Lang.HI, cmd = CommandType.MESSAGE),
            voice(at(1, 300), ok = true, lang = Lang.EN, cmd = CommandType.RIDE_HOME),
            voice(at(1, 400), ok = true, lang = Lang.MR, cmd = CommandType.CALL),
        )
        assertEquals(Ratio(2, 3), r.firstTryByLang[Lang.MR])
        assertEquals(Ratio(1, 1), r.firstTryByLang[Lang.HI])
        assertEquals(Ratio(1, 1), r.firstTryByLang[Lang.EN])
        assertEquals(Ratio(2, 2), r.firstTryByCommand[CommandType.CALL])
        assertEquals(Ratio(1, 1), r.firstTryByCommand[CommandType.MESSAGE])
        assertEquals(Ratio(4, 5), r.firstTry)
    }

    @Test fun daysUsedLast7AndPerWeek() {
        val r = report(done(at(0)), open(at(0)), open(at(1)), open(at(1, 50_000)), open(at(3)), open(at(8)), open(at(10)), now = at(13))
        assertEquals(6 - 1, r.activeDays)
        // Last 7 days = day 7..13: days 8 and 10
        assertEquals(2, r.daysUsedLast7)
        // Full weeks since setup on day 0: days 0-6 (0, 1, 3) and 7-13 (8, 10)
        assertEquals(listOf(3, 2), r.daysPerWeek)
    }

    @Test fun retentionIsNullUntilTheDayArrives() {
        val r = report(done(at(0)), open(at(1)), now = at(5))
        assertEquals(true, r.d1)
        assertNull(r.d7)
        assertNull(r.d30)
        val later = report(done(at(0)), open(at(1)), open(at(7)), now = at(31))
        assertEquals(true, later.d7)
        assertEquals(false, later.d30)
    }

    @Test fun activationIsAVoiceSuccessWithin24HoursOfSetup() {
        assertEquals(true, report(done(at(0)), voice(at(0, 72_000), ok = true)).activated)
        assertEquals(false, report(done(at(0)), voice(at(1, 50_000), ok = true)).activated)
        // A failed command doesn't activate
        assertEquals(false, report(done(at(0)), voice(at(0, 40_000), ok = false)).activated)
        // Still inside the first 24 h with no success: not yet decided
        assertNull(report(done(at(0)), now = at(0, 50_000)).activated)
        // No setup: no activation either way
        assertNull(report(voice(at(0), ok = true)).activated)
    }

    @Test fun timeToFirstValueUsesVoiceOrTile() {
        val r = report(done(at(0, 0)), tile(at(0, 120), CommandType.CALL), voice(at(0, 600), ok = true))
        assertEquals(120_000L, r.timeToFirstValueMs)
        assertEquals(600_000L, r.timeToFirstVoiceSuccessMs)
    }

    @Test fun setupCompletionTimeDropOffAndPermissions() {
        val finished = report(
            UsageEvent(at(0, 0), EventType.SETUP_START),
            UsageEvent(at(0, 30), EventType.SETUP_STEP, step = 1),
            UsageEvent(at(0, 60), EventType.PERMISSION, perm = Perm.MIC, ok = true),
            UsageEvent(at(0, 61), EventType.PERMISSION, perm = Perm.CALL, ok = false),
            done(at(0, 270), ms = 270_000),
        )
        assertTrue(finished.setup.finished)
        assertEquals(270_000L, finished.setup.durationMs)
        assertNull(finished.setup.dropOffStep)
        assertEquals(mapOf(Perm.MIC to true, Perm.CALL to false), finished.setup.permissions)

        val dropped = report(
            UsageEvent(at(0, 0), EventType.SETUP_START),
            UsageEvent(at(0, 30), EventType.SETUP_STEP, step = 1),
            UsageEvent(at(0, 90), EventType.SETUP_STEP, step = 2),
        )
        assertTrue(dropped.setup.started)
        assertFalse(dropped.setup.finished)
        assertEquals(2, dropped.setup.dropOffStep)
    }

    @Test fun voiceVersusTilePerCommand() {
        val r = report(
            voice(at(1, 0), ok = true, cmd = CommandType.CALL),
            voice(at(1, 100), ok = false),
            tile(at(1, 200), CommandType.CALL), tile(at(1, 300), CommandType.CALL), tile(at(1, 400), CommandType.RIDE_HOME),
        )
        assertEquals(1 to 2, r.voiceVsTile[CommandType.CALL])
        assertEquals(0 to 1, r.voiceVsTile[CommandType.RIDE_HOME])
        assertEquals(1, r.voiceTotal)
        assertEquals(3, r.tileTotal)
    }

    @Test fun cutOffsPickersErrorsAndMedians() {
        val r = report(
            UsageEvent(at(1, 0), EventType.VOICE_END, end = EndReason.CUT_OFF, chars = 40, ms = 9_000),
            UsageEvent(at(1, 10), EventType.VOICE_END, end = EndReason.SILENCE, chars = 20, ms = 4_000),
            UsageEvent(at(1, 20), EventType.VOICE_END, end = EndReason.COMPLETE, chars = 10, ms = 2_000),
            UsageEvent(at(1, 1), EventType.RECOGNIZER_ERROR, code = 7),
            UsageEvent(at(1, 2), EventType.RECOGNIZER_ERROR, code = 7),
            UsageEvent(at(1, 3), EventType.RECOGNIZER_ERROR, code = 11),
            voice(at(1, 30), ok = true, cmd = CommandType.FIND_CONTACT, ms = 5_000),
            UsageEvent(at(1, 31), EventType.PICKER_SHOWN),
        )
        assertEquals(Ratio(1, 3), r.cutOffs)
        assertEquals(mapOf(7 to 2, 11 to 1), r.recognizerErrors)
        assertEquals(20, r.medianUtteranceChars)
        assertEquals(5_000L, r.medianTimeToActionMs)
        assertEquals(Ratio(1, 1), r.pickers)
    }

    @Test fun actionsCallProxyAndLongAway() {
        val r = report(
            opened(at(1, 0)), back(at(1, 60), 60_000),
            opened(at(1, 100)), back(at(1, 104), 4_000),
            opened(at(1, 200), AppCategory.CAB), back(at(1, 300), 100_000, AppCategory.CAB),
            UsageEvent(at(1, 400), EventType.NEAR_TILE, category = AppCategory.GROCERY),
            UsageEvent(at(1, 401), EventType.PLAY_REDIRECT, category = AppCategory.GROCERY),
        )
        assertEquals(mapOf(AppCategory.DIALER to 2, AppCategory.CAB to 1), r.actionsOpened)
        assertEquals(Ratio(1, 2), r.callsLikelyConnected)
        assertEquals(Ratio(2, 3), r.longAway)
        assertEquals(Ratio(1, 3), r.wrongActions)
        assertEquals(1, r.nearTiles)
        assertEquals(1, r.playRedirects)
    }

    @Test fun crashFreeSessions() {
        val r = report(open(at(1)), open(at(2)), open(at(3)), open(at(4)), UsageEvent(at(4, 50), EventType.CRASH))
        assertEquals(4, r.sessions)
        assertEquals(1, r.crashes)
        assertEquals(Ratio(3, 4), r.crashFree)
    }

    @Test fun dailyCountsAreIntegersUnderSafeKeys() {
        val events = listOf(
            UsageEvent(at(0, 0), EventType.SETUP_START), done(at(0, 300), ms = 300_000), open(at(0, 301)),
            start(at(0, 400)), voice(at(0, 403), ok = true, cmd = CommandType.MESSAGE),
            voice(at(1, 0), ok = false), voice(at(1, 10), ok = true, ms = 1_000), tile(at(1, 50), CommandType.CALL),
            UsageEvent(at(1, 60), EventType.RECOGNIZER_ERROR, code = 7),
        )
        val d0 = dailyCounts(events, day0, utc)
        assertEquals(1, d0["setup_done"]); assertEquals(300, d0["setup_seconds"]); assertEquals(1, d0["activated"])
        assertEquals(1, d0["first_try_mr"]); assertEquals(1, d0["first_try_ok_mr"]); assertEquals(0, d0["age_days"])
        val d1 = dailyCounts(events, day0.plusDays(1), utc)
        assertEquals(2, d1["voice_attempts"]); assertEquals(1, d1["retry"]); assertEquals(1, d1["not_understood"])
        assertEquals(1, d1["first_try_mr"]); assertNull(d1["first_try_ok_mr"]); assertEquals(1, d1["tile_call"])
        assertEquals(1, d1["recognizer_error_7"]); assertEquals(1, d1["age_days"]); assertNull(d1["activated"])
        (d0.keys + d1.keys).forEach { assertTrue(it, COUNT_KEY.matches(it)) }
    }

    @Test fun pendingDaysAreFinishedDaysNotYetSent() {
        val events = listOf(open(at(0)), open(at(1)), open(at(3)), open(at(5)))
        val today = day0.plusDays(5)
        assertEquals(listOf(day0, day0.plusDays(1), day0.plusDays(3)), pendingUploadDays(events, Long.MIN_VALUE, today, utc))
        assertEquals(listOf(day0.plusDays(3)), pendingUploadDays(events, day0.plusDays(1).toEpochDay(), today, utc))
        // Never more than 30 days back
        assertEquals(emptyList<LocalDate>(), pendingUploadDays(events, Long.MIN_VALUE, day0.plusDays(40), utc))
    }
}
