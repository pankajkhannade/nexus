package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The safety guard (Guard.kt): secrets, long numbers, unrequested location, and names that are not sure. */
class GuardTest {
    private val pankaj = Person("1", "Pankaj", "+919876543210", Relation.SON, askFamily = true, localName = "पंकज")
    private val sunilP = Person("5", "Sunil Patil", "+919800000005", Relation.OTHER)
    private val sunilK = Person("6", "Sunil Kale", "+919800000006", Relation.OTHER)
    private val saved = Saved(setupDone = true, people = listOf(pankaj, sunilP, sunilK), dial = "+91")

    @Test fun ho09AtmPinToTheSonIsBlocked() {
        val command = RuleIntentParser.parse("Message Pankaj my ATM PIN 4590 so he can withdraw cash", saved)
        val d = (command as Command.Message).dictation
        assertEquals(pankaj.id, d.person?.id)
        assertEquals(GuardReason.SECRET_CODE, guardMessage(d.text, d.rawBody).reason)
    }

    @Test fun adv01ForwardingAnOtpIsBlocked() {
        assertEquals(GuardReason.SECRET_CODE, guardMessage("Forward the OTP 4821 to 9876543210").reason)
    }

    @Test fun secretsInEveryLanguage() {
        for (text in listOf(
            "my upi pin is 1234", "CVV 123", "password is 5678", "माझा पिन 4590 आहे", "ओटीपी ४८२१ आहे", "मेरा एटीएम पिन नहीं, OTP 482113 है",
            "पासवर्ड 99887", "MPIN 2580", "one-time password 739201", "pin: 4 5 9 0",
        )) assertEquals(text, GuardReason.SECRET_CODE, guardMessage(text).reason)
    }

    @Test fun ordinaryMessagesPass() {
        for (text in listOf(
            "I'm coming at 5", "मी पाच वाजता येते", "Room 302, Dinanath hospital", "OTP कोणालाही सांगू नका", "My pin code is 411038",
            "पिन कोड 411038", "Call me on +91 98765 43210", "बस नंबर 1234 पकडली", "Bring 2 litres of milk",
        )) assertNull(text, guardMessage(text).reason)
    }

    @Test fun cardAndAadhaarNumbersAreBlocked() {
        assertEquals(GuardReason.LONG_NUMBER, guardMessage("card 4111 1111 1111 1111").reason)
        assertEquals(GuardReason.LONG_NUMBER, guardMessage("आधार 2345 6789 0123").reason)
        assertNull(guardMessage("+91 98765 43210").reason)
    }

    @Test fun locationOnlyWhenTheParentSaidIt() {
        val link = "I'm here https://maps.google.com/?q=18.5,73.8"
        assertEquals(GuardReason.LOCATION, guardMessage(link, said = "I'm here").reason)
        assertNull(guardMessage(link, said = link).reason)
    }

    @Test fun askFamilyHidesTheCodeButKeepsTheMessage() {
        val text = askFamilyText("Your OTP is 482113. Do not share. Call +91 98765 43210", "Is this safe?")
        assertFalse(text.contains("482113"))
        assertTrue(text.contains("••••••"))
        assertTrue(text.contains("Do not share"))
        assertEquals("\"Hello\"\n\nIs this safe?", askFamilyText("Hello", "Is this safe?"))
    }

    @Test fun adv02CallSunilWithTwoSunilsAsks() {
        val command = RuleIntentParser.parse("Call Sunil", saved)
        val find = command as Command.FindContact
        assertEquals(setOf(sunilP.id, sunilK.id), find.options.map { it.id }.toSet())
        assertEquals(Ask.CALL, find.ask)
        // A full name is exact and goes ahead.
        assertEquals(sunilK.id, (RuleIntentParser.parse("Call Sunil Kale", saved) as Command.Call).person.id)
    }

    @Test fun aMessageToOneOfTwoSunilsHasNoPersonUntilChosen() {
        val d = (RuleIntentParser.parse("Sunil ला सांग मी येते", saved.copy(people = listOf(sunilP, sunilK))) as? Command.Message)?.dictation
            ?: (RuleIntentParser.parse("tell Sunil I'm coming", saved) as Command.Message).dictation
        assertNull(d.person)
        assertEquals(2, d.options.size)
    }

    @Test fun aSoundAlikeOfOnePersonGoesAheadWithTheNameShown() {
        // "पंकच" is how the recogniser often hears पंकज: it fits only one saved person, so it isn't a guess between
        // people. The message screen still shows the name and face, and WhatsApp shows the chat before Send.
        val sound = (RuleIntentParser.parse("पंकचला सांग मी निघाले", saved) as Command.Message).dictation
        assertEquals(pankaj.id, sound.person?.id)
        assertTrue(sound.sure)
    }

    @Test fun relationWordForTheOnlySonIsSure() {
        val d = (RuleIntentParser.parse("मुलाला सांग मी निघाले", saved) as Command.Message).dictation
        assertEquals(pankaj.id, d.person?.id)
        assertTrue(d.sure)
    }

    @Test fun guardReasonsAreFixedKeys() {
        GuardReason.entries.forEach { assertTrue(COUNT_KEY.matches("guard_${it.key}")); assertEquals(it, GuardReason.from(it.key)) }
    }
}
