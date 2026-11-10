package dev.pankajkhannade.nexus

import android.speech.SpeechRecognizer.ERROR_CLIENT
import android.speech.SpeechRecognizer.ERROR_NETWORK
import android.speech.SpeechRecognizer.ERROR_NO_MATCH
import android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY
import android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Continuous dictation across the recogniser's own cut-offs (Pankaj: "the recorder disconnects midway"). */
class ContinuousListenerTest {
    private val fake = FakeSpeechEngine()
    private val listener = ContinuousListener(fake, now = { fake.clock }, log = {})
    private var live = ""
    private var result: String? = "not finished"
    private var finished = 0

    private fun start(isComplete: (List<String>) -> Boolean = { false }) =
        listener.start("mr", VoiceMode.ONLINE, longForm = true, isComplete = isComplete, onText = { live = it }) {
            result = it; finished++
        }

    @Test fun partialEndOfSpeechMorePartialsFinalJoinUpWithNothingLost() {
        start()
        fake.partial("पंकजला व्हॉट्सॲप कर")
        fake.endOfSpeech()
        fake.error(ERROR_NO_MATCH) // Android often ends like this even after showing words
        assertEquals("restarted after the early end", 2, fake.starts)
        assertTrue(fake.requests[1].restart)
        fake.partial("आणि सांग की")
        fake.partial("आणि सांग की तू कधी येणार")
        assertEquals("पंकजला व्हॉट्सॲप कर आणि सांग की तू कधी येणार", live)
        fake.final("आणि सांग की तू कधी येणार आहेस")
        assertEquals(3, fake.starts)
        fake.partial("येताना साबण घेऊन ये")
        listener.done()
        assertEquals("पंकजला व्हॉट्सॲप कर आणि सांग की तू कधी येणार आहेस येताना साबण घेऊन ये", result)
        assertEquals(1, finished)
        assertFalse(listener.active)
        assertFalse("the recogniser is released", fake.listening)
    }

    @Test fun anEarlySpeechTimeoutRestartsInsteadOfEnding() {
        start()
        fake.error(ERROR_SPEECH_TIMEOUT)
        assertEquals(2, fake.starts)
        assertEquals("not finished", result)
        fake.final("Pankaj la phone lav")
        assertEquals(3, fake.starts)
        listener.done()
        assertEquals("Pankaj la phone lav", result)
    }

    @Test fun doneEndsItAndLaterRecogniserEventsAreIgnored() {
        start()
        fake.partial("hello")
        listener.done()
        assertEquals("hello", result)
        assertEquals(1, finished)
        assertEquals(1, fake.starts)
    }

    @Test fun finalWordsReplaceThatSessionsPartialNotAppend() {
        start()
        fake.partial("call pan")
        fake.final("call Pankaj")
        listener.done()
        assertEquals("call Pankaj", result)
    }

    @Test fun aWholeCommandStopsByItselfWithoutDone() {
        start(isComplete = { r -> r.any { it.endsWith("phone lav") } })
        fake.final("Pankaj la")
        assertEquals(2, fake.starts)
        fake.final("phone lav")
        assertEquals("Pankaj la phone lav", result)
        assertEquals(2, fake.starts)
    }

    @Test fun aQuietSpellAfterSpeakingFinishesWithTheWords() {
        start()
        fake.final("tu kadhi yenar")
        fake.advance(7_000)
        fake.error(ERROR_SPEECH_TIMEOUT)
        assertEquals("tu kadhi yenar", result)
    }

    @Test fun nothingSaidForTenSecondsFinishesEmpty() {
        start()
        fake.advance(5_000); fake.error(ERROR_SPEECH_TIMEOUT)
        assertEquals("not finished", result)
        fake.advance(6_000); fake.error(ERROR_NO_MATCH)
        assertNull(result)
    }

    @Test fun stopsAfterSixtySecondsKeepingEverything() {
        start()
        for (i in 0 until 20) {
            if (!listener.active) break
            fake.partial("word$i")
            fake.advance(5_500)
            fake.final("word$i")
        }
        assertFalse(listener.active)
        assertTrue(result!!.startsWith("word0 word1"))
        assertTrue(result!!.split(" ").size >= 10)
    }

    @Test fun busyOrClientErrorRecreatesTheRecogniserAndKeepsThePartial() {
        start()
        fake.partial("Sunil la")
        val before = fake.resets
        fake.error(ERROR_RECOGNIZER_BUSY)
        assertTrue("recogniser thrown away", fake.resets > before)
        assertEquals(2, fake.starts)
        fake.error(ERROR_CLIENT)
        assertEquals(3, fake.starts)
        fake.final("phone lav")
        listener.done()
        assertEquals("Sunil la phone lav", result)
    }

    @Test fun aNetworkErrorFinishesButNeverDropsWhatWasHeard() {
        start()
        fake.partial("Priya la sang ki")
        fake.error(ERROR_NETWORK)
        assertEquals("Priya la sang ki", result)
        assertEquals(1, fake.starts)
    }

    @Test fun cancelGivesNoResult() {
        start()
        fake.partial("hello")
        listener.cancel()
        assertEquals(0, finished)
        assertFalse(listener.active)
    }

    @Test fun neverStopsOnSilenceRightAfterAJoiner() {
        start()
        fake.final("पंकजला सांग की")
        fake.advance(9_000)
        fake.error(ERROR_SPEECH_TIMEOUT)
        assertTrue("a pause after की is thinking", listener.active)
        fake.final("तू कधी येणार आणि")
        fake.advance(9_000)
        fake.error(ERROR_NO_MATCH)
        assertTrue("a pause after आणि is thinking", listener.active)
        fake.final("साबण घेऊन ये")
        fake.advance(4_500)
        fake.error(ERROR_SPEECH_TIMEOUT)
        assertEquals("पंकजला सांग की तू कधी येणार आणि साबण घेऊन ये", result)
    }

    @Test fun aCommandEndingInAJoinerIsNotTakenAsComplete() {
        start(isComplete = { true })
        fake.final("Pankaj la sang ki")
        assertTrue(listener.active)
    }

    @Test fun shortCommandsStopAfterThreeAndAHalfSecondsDictationAfterFour() {
        listener.start("en", VoiceMode.ONLINE, longForm = false, onText = {}) { result = it }
        fake.final("hello")
        fake.advance(3_600)
        fake.error(ERROR_SPEECH_TIMEOUT)
        assertEquals("hello", result)

        result = "not finished"
        listener.start("en", VoiceMode.ONLINE, longForm = true, onText = {}) { result = it }
        fake.final("hello")
        fake.advance(3_600)
        fake.error(ERROR_SPEECH_TIMEOUT)
        assertEquals("not finished", result)
        fake.advance(500)
        fake.error(ERROR_SPEECH_TIMEOUT)
        assertEquals("hello", result)
    }

    @Test fun nBestReadingsAreOfferedWholeBestFirst() {
        start()
        fake.final("पंकजला")
        fake.final("Punk aaj la phone lav", "Pankaj la phone lav")
        assertEquals(listOf("पंकजला Punk aaj la phone lav", "पंकजला Pankaj la phone lav"), listener.alternatives)
    }

    @Test fun theRequestAsksForNamesBias() {
        listener.start("mr", VoiceMode.ONLINE, longForm = false, biasing = listOf("Pankaj", "Aai"), onText = {}) {}
        assertEquals(listOf("Pankaj", "Aai"), fake.requests.first().biasing)
        assertEquals("mr", fake.requests.first().language)
    }
}
