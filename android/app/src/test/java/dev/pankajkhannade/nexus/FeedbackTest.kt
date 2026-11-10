package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
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

    @Test fun answeringDayThreeHidesItUntilDaySeven() {
        val f = Feedback().answer(3, Helped.YES)
        assertNull(f.due(install, install + 4))
        assertEquals(7, f.due(install, install + 7))
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
        val f = Feedback().answer(3, Helped.NO).answer(7, Helped.LITTLE)
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
