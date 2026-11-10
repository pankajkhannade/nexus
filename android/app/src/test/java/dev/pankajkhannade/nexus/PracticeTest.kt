package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phrases on the leave-behind check must be the actions they claim to be. */
class PracticeTest {
    private val pankaj = Person("1", "Pankaj", "+919876543210", askFamily = true, alias = "पंकज")

    private fun saved(home: Boolean) = Saved(
        people = listOf(pankaj),
        home = if (home) Place("MG Road, Pune") else Place(),
    )

    @Test fun rideCheckExistsOnlyWhenHomeIsSaved() {
        assertEquals(listOf(PracticeTask.CALL, PracticeTask.MESSAGE), practiceTasks(saved(false)))
        assertEquals(listOf(PracticeTask.CALL, PracticeTask.MESSAGE, PracticeTask.RIDE), practiceTasks(saved(true)))
    }

    @Test fun theSentencesOnScreenPassOnlyTheirOwnCheck() {
        val home = saved(true)
        val cases = listOf(
            "Call Pankaj" to PracticeTask.CALL,
            "Pankaj को फ़ोन लगाओ" to PracticeTask.CALL,
            "पंकजला फोन लाव" to PracticeTask.CALL,
            "Tell Pankaj that I am home" to PracticeTask.MESSAGE,
            "पंकज को बता दो कि मैं घर पर हूँ" to PracticeTask.MESSAGE,
            "पंकजला सांग की मी घरी आहे" to PracticeTask.MESSAGE,
            "Take me home" to PracticeTask.RIDE,
            "मुझे घर जाना है" to PracticeTask.RIDE,
            "घरी जायचं आहे" to PracticeTask.RIDE,
        )
        cases.forEach { (said, task) ->
            val command = RuleIntentParser.parse(said, home)
            assertTrue("'$said' should pass $task but was $command", practicePasses(task, command))
            PracticeTask.entries.filter { it != task }.forEach { other ->
                assertFalse("'$said' must not pass $other", practicePasses(other, command))
            }
        }
    }

    @Test fun aDifferentRequestDoesNotPassTheCallCheck() {
        val command = RuleIntentParser.parse("Take me home", saved(true))
        assertEquals(Command.RideHome, command)
        assertFalse(practicePasses(PracticeTask.CALL, command))
        assertFalse(practicePasses(PracticeTask.CALL, Command.Unknown))
    }
}
