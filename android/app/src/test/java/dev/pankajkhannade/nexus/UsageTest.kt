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
}
