package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voice-dictated WhatsApp / SMS messages: who, which app, and what to say. Pure JVM. */
class DictationTest {
    private val pankaj = Person("1", "Pankaj", "+919876543210", Relation.SON)
    private val priya = Person("2", "Priya", "+919123456789", Relation.DAUGHTER)
    private val aai = Person("3", "Aai", "+919988776655", Relation.SPOUSE)
    private val raju = Person("4", "Raju", "+917000011122", Relation.OTHER, alias = "Bablu")
    private val saved = Saved(setupDone = true, language = "mr", dial = "+91", people = listOf(pankaj, priya, aai, raju))

    private fun read(said: String): Dictation = assertNotNull(said, readDictation(said, saved)).let { readDictation(said, saved)!! }

    private fun check(said: String, person: Person?, channel: Channel, text: String) {
        val d = read(said)
        assertEquals("person for: $said", person, d.person)
        assertEquals("channel for: $said", channel, d.channel)
        assertEquals("text for: $said", text, d.text)
    }

    // ---- Pankaj's own sentence, as the recogniser wrote it ----

    @Test fun pankajsExactSentence() {
        val d = read("पंकचला वाटसब कर आणि सांग की तू कधी येणार आहेस घरी. आणी येताना एक साबून घेऊन ये.")
        assertEquals(pankaj, d.person)
        assertEquals(Channel.WHATSAPP, d.channel)
        assertTrue(d.text, d.text.contains("तू घरी कधी येणार आहेस"))
        assertTrue(d.text, d.text.contains("साबण") || d.text.contains("साबून"))
        assertEquals("तू घरी कधी येणार आहेस? आणि येताना एक साबून घेऊन ये.", d.text)
    }

    @Test fun pankajsSentenceWithoutPunctuation() =
        check("पंकचला वाटसब कर आणि सांग की तू कधी येणार आहेस घरी आणि येताना एक साबण घेऊन ये", pankaj, Channel.WHATSAPP,
            "तू घरी कधी येणार आहेस? आणि येताना एक साबण घेऊन ये.")

    // ---- Marathi ----

    @Test fun marathiWhatsAppSpelling() =
        check("पंकजला व्हॉट्सॲप कर की मी पोहोचले", pankaj, Channel.WHATSAPP, "मी पोहोचले.")

    @Test fun marathiSms() =
        check("पंकजला मेसेज कर की मी उशिरा येईन", pankaj, Channel.SMS, "मी उशिरा येईन.")

    @Test fun marathiSmsWord() =
        check("पंकजला एसएमएस पाठव की जेवण तयार आहे", pankaj, Channel.SMS, "जेवण तयार आहे.")

    @Test fun marathiYesNoQuestionWithKa() =
        check("पंकजला व्हाट्सएप कर आणि विचार जेवलास का", pankaj, Channel.WHATSAPP, "विचार जेवलास का?")

    @Test fun marathiTellWithoutAppGoesToWhatsAppOrSms() =
        check("पंकजला सांग की दूध घेऊन ये", pankaj, Channel.ANY, "दूध घेऊन ये.")

    @Test fun marathiNoConnectorTakesEverythingAfterTheVerb() =
        check("पंकजला वाटसब कर मी बँकेत आहे", pankaj, Channel.WHATSAPP, "मी बँकेत आहे.")

    @Test fun marathiRelationWord() =
        check("मुलाला व्हॉट्सॲप कर की औषध संपले", pankaj, Channel.WHATSAPP, "औषध संपले.")

    @Test fun marathiFamilyWordAcrossScripts() =
        check("आईला मेसेज कर की मी येतोय", aai, Channel.SMS, "मी येतोय.")

    @Test fun romanizedMarathi() =
        check("Pankaj la whatsapp kar ani sang ki tu kadhi yenar aahes ghari", pankaj, Channel.WHATSAPP, "Tu ghari kadhi yenar aahes?")

    @Test fun romanizedMarathiAttachedLa() =
        check("Pankajla message kar ki mi ghari aale", pankaj, Channel.SMS, "Mi ghari aale.")

    // ---- Hindi and Hinglish ----

    @Test fun hinglishFromTheBrief() =
        check("pankaj ko whatsapp karo ki main late aaungi", pankaj, Channel.WHATSAPP, "Main late aaungi.")

    @Test fun hindiDevanagari() =
        check("पंकज को व्हाट्सएप करो कि मैं देर से आऊँगी", pankaj, Channel.WHATSAPP, "मैं देर से आऊँगी.")

    @Test fun hindiKehDo() =
        check("पंकज को मैसेज करके कह दो कि खाना बन गया", pankaj, Channel.SMS, "खाना बन गया.")

    @Test fun hindiQuestionMovesTheTrailingPlace() =
        check("प्रिया को व्हाट्सएप करो और पूछो तुम कब आओगी घर", priya, Channel.WHATSAPP, "पूछो तुम घर कब आओगी?")

    @Test fun hinglishBolo() =
        check("Priya ko bolo ki dawai le aana", priya, Channel.ANY, "Dawai le aana.")

    @Test fun hinglishBataDo() =
        check("Pankaj ko whatsapp pe bata do ki main mandir ja rahi hoon", pankaj, Channel.WHATSAPP, "Main mandir ja rahi hoon.")

    @Test fun hinglishMsgKar() =
        check("Raju ko msg kar do kal aana", raju, Channel.SMS, "Kal aana.")

    @Test fun aliasIsFound() =
        check("Bablu ko whatsapp karo ki gas cylinder aa gaya", raju, Channel.WHATSAPP, "Gas cylinder aa gaya.")

    @Test fun whatsAppSplitIntoTwoWords() =
        check("pankaj ko whats app karo ki main aa gayi", pankaj, Channel.WHATSAPP, "Main aa gayi.")

    // ---- English ----

    @Test fun englishTextThat() =
        check("text Pankaj that dinner is ready", pankaj, Channel.SMS, "Dinner is ready.")

    @Test fun englishWhatsAppSaying() =
        check("WhatsApp Priya saying i'll be late", priya, Channel.WHATSAPP, "I'll be late.")

    @Test fun englishTellHimThat() =
        check("tell Pankaj that the plumber is here", pankaj, Channel.ANY, "The plumber is here.")

    @Test fun englishQuestion() =
        check("message Pankaj are you coming for dinner", pankaj, Channel.SMS, "Are you coming for dinner?")

    @Test fun englishSendWhatsAppTo() =
        check("send a whatsapp to Priya that I reached home", priya, Channel.WHATSAPP, "I reached home.")

    @Test fun englishWhQuestion() =
        check("whatsapp Pankaj when will you come home", pankaj, Channel.WHATSAPP, "When will you come home?")

    // ---- Who? and what? ----

    @Test fun unknownRecipientAsksWho() {
        val d = read("Suresh la whatsapp kar ki mi yete")
        assertNull(d.person)
        assertEquals("Suresh", d.askedName)
        assertEquals("Mi yete.", d.text)
    }

    @Test fun unknownDevanagariRecipientAsksWho() {
        val d = read("सुरेशला मेसेज कर की मी येते")
        assertNull(d.person)
        assertEquals("सुरेश", d.askedName)
    }

    @Test fun noRecipientAtAllAsksWho() {
        val d = read("व्हॉट्सॲप कर की मी येते")
        assertNull(d.person)
        assertNull(d.askedName)
        assertEquals("मी येते.", d.text)
    }

    @Test fun emptyBodyAsksWhatToSay() {
        val d = read("Pankaj la whatsapp kar")
        assertEquals(pankaj, d.person)
        assertEquals("", d.text)
        assertEquals("", read("WhatsApp Pankaj").text)
        assertEquals("", read("आईला मेसेज कर").text)
    }

    // ---- Not a message ----

    @Test fun callsStillRouteToCall() {
        for (said in listOf("Pankaj ko phone lagao", "पंकजला फोन लाव", "call Pankaj", "Aai la phone lav", "Pankaj ko phone karke bolo ki aa jao")) {
            assertNull(said, readDictation(said, saved))
            assertEquals(said, Ask.CALL, readMessage(said, saved).ask)
        }
    }

    @Test fun ridesAreNotMessages() {
        for (said in listOf("ghar jana hai", "घरी जायचं आहे", "take me home")) assertNull(said, readDictation(said, saved))
    }

    @Test fun tellWithoutAKnownPersonIsNotAMessage() {
        assertNull(readDictation("मला सांग", saved))
        assertNull(readDictation("bolo", saved))
    }

    // ---- The rewrite on its own ----

    @Test fun rewriteKeepsWordsWhenNoRuleApplies() {
        assertEquals("औषध घेतले.", polishMessage("औषध घेतले"))
        assertEquals("Dinner is ready!", polishMessage("that dinner is ready!"))
        assertEquals("", polishMessage("  की  "))
    }

    @Test fun soundKeyBringsMisheardNamesTogether() {
        assertEquals(soundKey("Pankaj"), soundKey("पंकज"))
        assertEquals("pankac", soundKey("पंकच"))
        assertEquals("vaatasab", romanize("वाटसब"))
    }

    @Test fun whatsAppLinkEncodesDevanagari() {
        val link = whatsAppLink(pankaj.phone, "तू घरी कधी येणार आहेस? आणि साबण आण.")
        assertTrue(link, link.startsWith("https://wa.me/919876543210?text=%E0%A4%A4%E0%A5%82%20"))
        assertTrue(link, link.contains("%3F%20")) // "? "
    }
}
