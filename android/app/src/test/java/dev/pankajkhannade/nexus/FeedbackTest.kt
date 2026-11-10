package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackTest {
    private val install = 1000L

    @Test fun nothingBeforeDayThree() {
        assertNull(Feedback().due(install, install))
        assertNull(Feedback().due(install, install + 2))
    }

    @Test fun dayThreeCardAppears() {
        assertEquals(3, Feedback().due(install, install + 3))
        assertEquals(3, Feedback().due(install, install + 5))
    }

    @Test fun answeringQ1KeepsTheCardOpenForQ2() {
        val f = Feedback().answer(3, Helped.YES)
        assertEquals(3, f.due(install, install + 3))
        assertFalse(f.isClosed(3))
    }

    @Test fun savingQ2HidesDayThreeUntilDaySeven() {
        val f = Feedback().answer(3, Helped.YES).easier(3, "bigger buttons")
        assertNull(f.due(install, install + 4))
        assertEquals(7, f.due(install, install + 7))
    }

    @Test fun skippingQ2ClosesTheRound() {
        val f = Feedback().answer(3, Helped.NO).finish(3)
        assertNull(f.due(install, install + 3))
        assertEquals(Helped.NO, f.answers[3]?.helped)
    }

    @Test fun oldAnswersAndClosedRoundsSurviveARestart() {
        val f = Feedback().answer(7, Helped.YES).finish(7).skip(3).skip(3)
        val back = Feedback.parse(f.serialize())
        assertEquals(f, back)
        assertNull(back.due(install, install + 9))
    }

    @Test fun exportIncludesBothRounds() {
        val f = Feedback().answer(3, Helped.LITTLE).easier(3, "louder voice").answer(7, Helped.YES).finish(7)
        val text = exportText(Usage(), f, installDay = install, today = install + 8, language = "hi")
        assertTrue(text.contains("Day 3 feedback: helped=a_little; easier: \"louder voice\""))
        assertTrue(text.contains("Day 7 feedback: helped=yes"))
    }

    @Test fun twoSkipsCloseARound() {
        val once = Feedback().skip(3)
        assertEquals(3, once.due(install, install + 3))
        assertNull(once.skip(3).due(install, install + 3))
    }

    @Test fun daySevenWinsOverAnUnansweredDayThree() {
        assertEquals(7, Feedback().due(install, install + 8))
    }

    @Test fun allDoneMeansNoCard() {
        val f = Feedback().answer(3, Helped.NO).finish(3).answer(7, Helped.LITTLE).finish(7)
        assertNull(f.due(install, install + 30))
    }

    @Test fun noInstallDayMeansNoCard() {
        assertNull(Feedback().due(0, 500))
    }

    @Test fun serializeRoundTrips() {
        val f = Feedback().answer(3, Helped.LITTLE).easier(3, "line one\nline two").skip(7)
        assertEquals(f, Feedback.parse(f.serialize()))
    }
}
