package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phrases the Try-it step teaches, plus aliases and relation words. */
class ParentCommandTest {
    private val rahul = Person("1", "Rahul", "+919876543210", Relation.SON, askFamily = true, alias = "Bunty")
    private val priya = Person("2", "Priya", "+919812345678", Relation.DAUGHTER)
    private val saved = Saved(setupDone = true, people = listOf(rahul, priya))

    @Test fun tryPhrasesInAllThreeLanguages() {
        for (said in listOf("Call Rahul", "राहुल को फ़ोन लगाओ", "राहुलला फोन लाव", "rahul ko phone lagao", "Rahul la phone lav")) {
            val r = readMessage(said, saved.copy(people = listOf(rahul.copy(name = if (said.any { it in '\u0900'..'\u097F' }) "राहुल" else "Rahul"), priya)))
            assertEquals(said, "1", r.person?.id)
            assertEquals(said, Ask.CALL, r.ask)
        }
    }

    @Test fun aliasIsExact() {
        val r = readMessage("Bunty ko phone lagao", saved)
        assertEquals("1", r.person?.id)
        assertTrue(r.exact)
    }

    @Test fun relationWordFindsTheOnlySon() {
        val r = readMessage("बेटे को फ़ोन लगाओ", saved)
        assertEquals("1", r.person?.id)
        val m = readMessage("मुलीला फोन लाव", saved)
        assertEquals("2", m.person?.id)
    }

    @Test fun maidAndCaretakerAreCalledByTheWordTheParentSays() {
        val lakshmi = Person("3", "Lakshmi", "+919900000001", Relation.MAID)
        val tara = Person("4", "Tara", "+919900000002", Relation.CARETAKER)
        val home = saved.copy(people = saved.people + lakshmi + tara)
        for (said in listOf("बाईला फोन लाव", "maid ko phone lagao", "call the maid", "कामवालीला फोन लाव")) {
            assertEquals(said, lakshmi.id, readMessage(said, home).person?.id)
            assertEquals(said, lakshmi.id, (RuleIntentParser.parse(said, home) as Command.Call).person.id)
        }
        for (said in listOf("caretaker la phone lav", "नर्सला फोन लाव", "आया को फ़ोन लगाओ")) {
            assertEquals(said, tara.id, readMessage(said, home).person?.id)
        }
        val twoMaids = home.copy(people = home.people + lakshmi.copy(id = "5", name = "Shanta", phone = "+919900000003"))
        assertNull(readMessage("बाईला फोन लाव", twoMaids).person)
    }

    @Test fun fuzzyNameNeedsConfirmation() {
        val r = readMessage("call Raahul", saved)
        assertEquals("1", r.person?.id)
        assertFalse(r.exact)
    }

    @Test fun unknownNameIsNotUnderstood() {
        assertNull(readMessage("call Suresh", saved).person)
    }
}
