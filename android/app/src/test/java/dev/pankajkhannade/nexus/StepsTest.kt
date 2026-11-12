package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-step clause splitting (Steps.kt), on the LFM3 test requests (orchestration-lfm3/cases.py and heldout.py)
 * plus the single requests that must not change.
 */
class StepsTest {
    private val pankaj = Person("1", "Pankaj", "+919876543210", Relation.SON, askFamily = true, localName = "पंकज")
    private val priya = Person("2", "Priya", "+919812345678", Relation.DAUGHTER, localName = "प्रिया")
    private val meena = Person("3", "Meena", "+919800000003", Relation.OTHER, localName = "मीना")
    private val doctor = Person("4", "Dr Joshi", "+919800000004", Relation.OTHER, alias = "doctor", localName = "डॉक्टर")
    private val saved = Saved(setupDone = true, people = listOf(pankaj, priya, meena, doctor), home = Place("Kothrud, Pune"), dial = "+91")

    private fun commands(said: String): List<Pair<Command, Boolean>> =
        splitCommand(said, saved).steps.map { RuleIntentParser.parse(it.text, saved) to it.onlyIfNotWorked }

    private fun assertMessageTo(person: Person, command: Command, words: String? = null) {
        val d = (command as Command.Message).dictation
        assertEquals(person.id, d.person?.id)
        assertTrue(d.sure)
        words?.let { assertTrue("${d.text} has $it", d.text.contains(it)) }
    }

    @Test fun en01RideThenTell() {
        val steps = commands("Book a cab to Dinanath hospital and tell Pankaj I've left")
        assertEquals(2, steps.size)
        assertEquals("Dinanath hospital", (steps[0].first as Command.RideTo).query)
        assertMessageTo(pankaj, steps[1].first, "left")
        assertFalse(steps[1].second)
    }

    @Test fun en02CallThenMessageOnlyIfNoAnswer() {
        val steps = commands("Call the doctor, and if he doesn't pick up, message him that I'm coming at 5")
        assertEquals(2, steps.size)
        assertEquals(doctor.id, (steps[0].first as Command.Call).person.id)
        assertMessageTo(doctor, steps[1].first, "coming at 5")
        assertTrue("the message waits on 'did it work?'", steps[1].second)
    }

    @Test fun mr01RideThenTell() {
        val steps = commands("दीनानाथ हॉस्पिटलला जायचं आहे आणि पंकजला सांग मी निघाले")
        assertEquals("दीनानाथ हॉस्पिटल", (steps[0].first as Command.RideTo).query)
        assertMessageTo(pankaj, steps[1].first, "निघाले")
    }

    @Test fun mr02ConditionalCarriesThePerson() {
        val steps = commands("डॉक्टरांना फोन लाव, नाही उचलला तर सांग मी पाच वाजता येते")
        assertEquals(2, steps.size)
        assertEquals(doctor.id, (steps[0].first as Command.Call).person.id)
        assertMessageTo(doctor, steps[1].first, "पाच वाजता")
        assertTrue(steps[1].second)
    }

    @Test fun mr04RideThenCall() {
        val steps = commands("स्वारगेटला ओला बुक कर आणि मीनाला फोन लाव")
        assertEquals("स्वारगेट", (steps[0].first as Command.RideTo).query)
        assertEquals(meena.id, (steps[1].first as Command.Call).person.id)
    }

    @Test fun hi01RideThenTell() {
        val steps = commands("दीनानाथ अस्पताल के लिए कैब बुक करो और पंकज को बताओ कि मैं निकल गई हूँ")
        assertEquals("दीनानाथ अस्पताल", (steps[0].first as Command.RideTo).query)
        assertMessageTo(pankaj, steps[1].first, "निकल गई")
    }

    @Test fun hi02ConditionalMessage() {
        val steps = commands("डॉक्टर को फोन लगाओ, अगर न उठाएं तो मैसेज करो कि मैं 5 बजे आ रही हूँ")
        assertEquals(doctor.id, (steps[0].first as Command.Call).person.id)
        assertMessageTo(doctor, steps[1].first, "5 बजे")
        assertTrue(steps[1].second)
    }

    @Test fun ho05PronounAfterConditional() {
        val steps = commands("बेटी को फोन करो, नहीं लगे तो उसे लिखो कि मुझे दवाई चाहिए")
        assertEquals(priya.id, (steps[0].first as Command.Call).person.id)
        assertMessageTo(priya, steps[1].first, "दवाई")
        assertTrue(steps[1].second)
    }

    @Test fun thenAndPhirAndMagJoinSteps() {
        assertEquals(2, commands("Call Pankaj and then WhatsApp Priya").size)
        assertEquals(2, commands("पंकजला फोन लाव मग प्रियाला फोन लाव").size)
        assertEquals(2, commands("पंकजला फोन लाव नंतर मीनाला फोन लाव").size)
        val hi = commands("पंकज को फ़ोन लगाओ फिर मीना को फ़ोन लगाओ")
        assertEquals(listOf(pankaj.id, meena.id), hi.map { (it.first as Command.Call).person.id })
    }

    @Test fun andInsideAMessageStaysInTheMessage() {
        for (said in listOf("पंकजला सांग की दूध आणि साबण आण", "Tell Pankaj to buy milk and bread", "पंकज को बताओ कि दूध और ब्रेड लाना")) {
            assertFalse(said, splitCommand(said, saved).isPlan)
        }
    }

    @Test fun singleRequestsAreNotPlans() {
        for (said in listOf("Call Pankaj", "घरी जायचं आहे", "पंकजला फोन लाव", "Priya ko WhatsApp karo", "दूध मागव", "take me to Pune airport", "Pankaj ko phone karke bolo ki main aa rahi hoon")) {
            assertFalse(said, splitCommand(said, saved).isPlan)
        }
    }

    @Test fun locationIsNeverAStep() {
        val r = splitCommand("Tell Pankaj I reached and share my location", saved)
        assertTrue(r.locationDropped)
        assertFalse(r.isPlan)
        val plan = splitCommand("स्वारगेटला ओला बुक कर आणि पंकजला लोकेशन पाठव आणि मीनाला फोन लाव", saved)
        assertTrue(plan.locationDropped)
        assertEquals(2, plan.steps.size)
        assertTrue(plan.steps.none { it.text.contains("लोकेशन") })
    }

    @Test fun aConditionalNeverStartsAPlan() {
        val r = splitCommand("if he doesn't pick up, call Pankaj and then call Priya", saved)
        r.steps.firstOrNull()?.let { assertFalse(it.onlyIfNotWorked) }
    }

    @Test fun joinersLiveInEachLanguagesGrammar() {
        val codes = CLAUSE_GRAMMARS.map { it.code }
        assertEquals(PARENT_LANGUAGES.map { it.code }.sorted(), codes.sorted())
        CLAUSE_GRAMMARS.forEach { assertTrue(it.code, it.sequence.isNotEmpty() && it.ifNotWorked.isNotEmpty()) }
        assertTrue("आणि" in CLAUSE_GRAMMARS.first { it.code == "mr" }.sequence)
        assertTrue("फिर" in CLAUSE_GRAMMARS.first { it.code == "hi" }.sequence)
        assertTrue("then" in CLAUSE_GRAMMARS.first { it.code == "en" }.sequence)
    }

    @Test fun planExpiresAfterHalfAnHour() {
        val plan = PlanQueue(listOf(PlannedStep("Call Pankaj"), PlannedStep("Call Priya")), 1, touchedAt = 1_000_000L)
        assertFalse(plan.expired(1_000_000L + 29 * 60_000L))
        assertTrue(plan.expired(1_000_000L + 31 * 60_000L))
        assertEquals("Call Priya", plan.pending?.text)
        assertNull(plan.copy(next = 2).pending)
    }

    @Test fun splittingIsFast() {
        val said = "डॉक्टरांना फोन लाव, नाही उचलला तर सांग मी पाच वाजता येते"
        repeat(20) { splitCommand(said, saved) }
        val start = System.nanoTime()
        repeat(100) { splitCommand(said, saved) }
        val perCallMs = (System.nanoTime() - start) / 100 / 1_000_000.0
        assertTrue("$perCallMs ms", perCallMs < 50)
    }
}
