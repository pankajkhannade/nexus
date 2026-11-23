package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DoseTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val bp = Dose("bp", "BP tablet", 21, 0)
    private val sugar = Dose("sg", "Sugar", 8, 0)

    @Test fun nameAndTimeAreChecked() {
        assertNull(cleanDoseName(" "))
        assertNull(cleanDoseName("A"))
        assertEquals("BP tablet", cleanDoseName("  BP   tablet "))
        assertNull(cleanDoseTime(24, 0))
        assertEquals(21 to 5, cleanDoseTime(21, 5))
    }

    @Test fun aTimeThatHasPassedSpeaksTomorrow() {
        val now = ZonedDateTime.of(2026, 11, 23, 21, 30, 0, 0, zone)
        assertEquals(24, nextDoseAt(21, 0, now).dayOfMonth)
        assertEquals(23, nextDoseAt(22, 0, now).dayOfMonth)
    }

    @Test fun onlyAnUnansweredDoseThatIsDueShows() {
        val now = ZonedDateTime.of(2026, 11, 23, 21, 10, 0, 0, zone)
        assertEquals(listOf(bp), dueDoses(listOf(bp, sugar), emptyList(), now))
        val taken = listOf(DoseMark("bp", "2026-11-23", true))
        assertTrue(dueDoses(listOf(bp), taken, now).isEmpty())
        val morning = now.withHour(7)
        assertTrue(dueDoses(listOf(sugar), emptyList(), morning).isEmpty())
    }

    @Test fun theSameDayReplacesAndOldDaysDrop() {
        val today = LocalDate.of(2026, 11, 23)
        val first = recordMark(emptyList(), DoseMark("bp", "2026-11-23", false), listOf(bp), today)
        val second = recordMark(first, DoseMark("bp", "2026-11-23", true), listOf(bp), today)
        assertEquals(listOf(true), second.map { it.taken })
        val old = DoseMark("bp", "2026-10-01", true)
        assertTrue(pruneMarks(listOf(old), listOf(bp), today).isEmpty())
        assertTrue(pruneMarks(second, emptyList(), today).isEmpty())
    }

    @Test fun theShareLineIsOnlyToday() {
        val marks = listOf(DoseMark("bp", "2026-11-23", true), DoseMark("bp", "2026-11-22", false))
        val lines = doseShareLines(listOf(bp), marks, LocalDate.of(2026, 11, 23)) { mark ->
            if (mark?.taken == true) "taken" else "not yet"
        }
        assertEquals(listOf("BP tablet 21:00: taken"), lines)
    }
}
