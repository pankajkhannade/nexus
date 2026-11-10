package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTest {
    @Test fun countsPerDayAndTotal() {
        val u = Usage().record(Event.CALL, 100).record(Event.CALL, 100).record(Event.CALL, 101).record(Event.SAFETY_CHECK, 101)
        assertEquals(2, u.count(Event.CALL, 100))
        assertEquals(3, u.total(Event.CALL))
        assertEquals(1, u.total(Event.SAFETY_CHECK))
        assertEquals(0, u.total(Event.RIDE))
        assertEquals(2, u.activeDays())
    }

    @Test fun serializeRoundTrips() {
        val u = Usage().record(Event.VOICE, 5).record(Event.VOICE_FAIL, 5).record(Event.OPEN, 9)
        assertEquals(u, Usage.parse(u.serialize()))
    }

    @Test fun parseIgnoresJunk() {
        val u = Usage.parse("12 calls 3\nbad line\n13 unknown_event 4\n14 opens x")
        assertEquals(3, u.total(Event.CALL))
        assertEquals(1, u.activeDays())
    }

    @Test fun exportHasCountsAndNoPersonalData() {
        val u = Usage().record(Event.CALL, 20).record(Event.ASK_FAMILY, 21)
        val f = Feedback().answer(3, Helped.LITTLE).easier(3, "bigger text")
        val text = exportText(u, f, installDay = 18, today = 25, language = "mr")
        assertTrue(text.contains("calls"))
        assertTrue(text.contains("a_little"))
        assertTrue(text.contains("bigger text"))
        assertFalse(text.contains("+91"))
    }

    @Test fun everyEventTypeIsCountedAndSurvivesARestart() {
        var u = Usage()
        Event.entries.forEachIndexed { i, e -> repeat(i + 1) { u = u.record(e, 200) } }
        val back = Usage.parse(u.serialize())
        Event.entries.forEachIndexed { i, e -> assertEquals(e.name, i + 1, back.count(e, 200)) }
        assertEquals(1, back.activeDays())
    }

    @Test fun daysAreKeptApart() {
        val u = Usage().record(Event.RIDE, 300).record(Event.RIDE, 302).record(Event.WHATSAPP, 302)
        assertEquals(1, u.count(Event.RIDE, 300))
        assertEquals(0, u.count(Event.RIDE, 301))
        assertEquals(2, u.total(Event.RIDE))
        assertEquals(2, u.activeDays())
    }

    @Test fun exportListsEachDayInOrderWithSetupAge() {
        val install = java.time.LocalDate.of(2026, 11, 10).toEpochDay()
        val u = Usage().record(Event.SAFETY_CHECK, install + 2).record(Event.CALL, install).record(Event.CALL, install)
        val text = exportText(u, Feedback(), install, install + 3, "mr")
        assertTrue(text.contains("Set up: 2026-11-10 (day 3)"))
        assertTrue(text.contains("Active days: 2"))
        assertTrue(text.indexOf("2026-11-10: calls=2") < text.indexOf("2026-11-12: safety_checks=1"))
        assertFalse(text.contains("feedback"))
    }

    @Test fun exportShowsSkippedRounds() {
        val text = exportText(Usage(), Feedback().skip(3).skip(3), 1000, 1004, "en")
        assertTrue(text.contains("Day 3 feedback: helped=-; skipped 2"))
    }
}
