package dev.pankajkhannade.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatchTest {
    @Test fun exactAndFirstName() {
        assertTrue(nameMatches("Pankaj", "pankaj"))
        assertTrue(nameMatches("Ravi Kumar", "Ravi"))
        assertTrue(nameMatches("Ravi Kumar", "ravi kumar"))
        assertFalse(nameMatches("Ravi Kumar", "Kumar"))
        assertFalse(nameMatches("Pankaj", "P"))
    }

    @Test fun relationWordsAcrossScripts() {
        assertTrue(nameMatches("Aai", "आई"))
        assertTrue(nameMatches("आई", "aai"))
        assertTrue(nameMatches("Baba", "बाबा"))
        assertTrue(nameMatches("Mummy", "mumma"))
        assertFalse(nameMatches("Aai", "Baba"))
    }
}
