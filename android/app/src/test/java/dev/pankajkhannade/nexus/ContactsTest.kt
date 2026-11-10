package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phone-book matching (Pankaj: "show me the contact whose name I am saying, not just the ones I saved"). */
class ContactsTest {
    private val pankaj = Person("1", "Pankaj", "+919876543210", Relation.SON, askFamily = true)
    private val saved = Saved(setupDone = true, dial = "+91", people = listOf(pankaj))

    /** What the parser asks the phone book for, then what the phone book answers. */
    private fun lookup(said: String): Pair<Command, ContactLookup?> {
        val command = RuleIntentParser.parse(said, saved)
        val name = (command as? Command.FindContact)?.name ?: (command as? Command.Message)?.dictation?.askedName
        return command to name?.let { findContact(it, PHONE_BOOK, "+91") }
    }

    private fun clear(said: String): ContactLookup.Clear {
        val (command, found) = lookup(said)
        assertTrue("$said -> $command / $found", found is ContactLookup.Clear)
        return found as ContactLookup.Clear
    }

    @Test fun marathiDevanagariFindsALatinContact() {
        val c = clear("सुनीलला फोन लाव")
        assertEquals("Sunil Patil", c.match.person.name)
        assertEquals("+919822011111", c.match.person.phone)
        assertTrue("first name as said", c.exact)
    }

    @Test fun hindiAndEnglishPhrasings() {
        assertEquals("Sunil Patil", clear("Sunil ko phone lagao").match.person.name)
        assertEquals("Sunil Patil", clear("call Sunil Patil").match.person.name)
        assertEquals("Sunil Patil", clear("सुनील पाटील को फ़ोन लगाओ").match.person.name)
    }

    @Test fun surnameAloneFindsButIsNotExact() {
        val c = clear("Patil la phone lav")
        assertEquals("Sunil Patil", c.match.person.name)
        assertFalse(c.exact)
    }

    @Test fun familyTitlePicksRameshKakaOverRameshJoshi() {
        assertEquals("Ramesh Kaka", clear("Ramesh kaka la phone lav").match.person.name)
        assertEquals("Ramesh Kaka", clear("रमेश काकांना फोन लाव".replace("काकांना", "काका ला")).match.person.name)
    }

    @Test fun plainRameshIsAmbiguous() {
        val found = findContact("Ramesh", PHONE_BOOK, "+91")
        assertTrue(found is ContactLookup.Choose)
        assertEquals(setOf("Ramesh Kaka", "Ramesh Joshi"), (found as ContactLookup.Choose).options.map { it.person.name }.toSet())
    }

    @Test fun twoRahulsAskWhichOne() {
        val (_, found) = lookup("Rahul la phone lav")
        val options = (found as ContactLookup.Choose).options
        assertEquals(listOf("Rahul Joshi", "Rahul Kulkarni"), options.map { it.person.name }.sorted())
        assertEquals(setOf("4441", "4442"), options.map { numberEnding(it.person.phone) }.toSet())
    }

    @Test fun fullNamePicksOneRahul() {
        assertEquals("Rahul Kulkarni", clear("Rahul Kulkarni la phone lav").match.person.name)
        assertEquals("+919822044442", clear("Rahul Kulkarni la phone lav").match.person.phone)
    }

    @Test fun sharmaJiInHindi() {
        val c = clear("sharma ji ko call karo")
        assertEquals("Anil Sharma", c.match.person.name)
        assertEquals("Anil Sharma", (findContact("शर्माजी", PHONE_BOOK, "+91") as ContactLookup.Clear).match.person.name)
    }

    @Test fun latinSpeechFindsADevanagariContact() {
        assertEquals("राजेश देशमुख", clear("Rajesh la phone lav").match.person.name)
        assertEquals("राजेश देशमुख", clear("call Rajesh Deshmukh").match.person.name)
    }

    @Test fun aMisheardNameStillFindsButNeverCountsAsExact() {
        val found = findContact("Sanjey", PHONE_BOOK, "+91")
        val person = when (found) {
            is ContactLookup.Clear -> { assertFalse(found.exact); found.match.person }
            is ContactLookup.Choose -> found.options.first().person
            else -> null
        }
        assertEquals("Sanjay Bhosale", person?.name)
        val bhosle = findContact("Sanjay Bhosle", PHONE_BOOK, "+91")
        assertTrue(bhosle !is ContactLookup.NotFound)
    }

    @Test fun titleWordsInTheContactNameAreSetAside() {
        assertEquals("Dr Kelkar", clear("Kelkar la phone lav").match.person.name)
        assertEquals("Mahesh Dada", clear("Mahesh dada la phone lav").match.person.name)
        assertEquals("Mahesh Dada", clear("महेश ला फोन लाव").match.person.name)
    }

    @Test fun noMatchIsNotFound() {
        val (command, found) = lookup("Suresh la phone lav")
        assertEquals(Command.FindContact("Suresh", Ask.CALL), command)
        assertEquals(ContactLookup.NotFound, found)
        assertEquals(ContactLookup.NotFound, findContact("Rohit", PHONE_BOOK, "+91"))
    }

    @Test fun aSavedPersonComesBeforeThePhoneBook() {
        assertEquals(Command.Call(pankaj, exact = true), RuleIntentParser.parse("Pankaj la phone lav", saved))
    }

    @Test fun aContactWithTwoNumbersOffersBoth() {
        val found = findContact("Meena mavshi", PHONE_BOOK, "+91") as ContactLookup.Choose
        assertEquals(listOf("+919822012121", "02024445555"), found.options.map { it.person.phone })
    }

    @Test fun landlinesAreKeptForCalls() {
        assertEquals("02025431234", contactNumber("020 2543 1234", "+91"))
        assertEquals("+919822011111", contactNumber("98220 11111", "+91"))
        assertNull(contactNumber("12", "+91"))
        assertNull(contactNumber("*121#", "+91"))
    }

    @Test fun aNumberThatCantBeDialledIsDropped() {
        assertEquals(ContactLookup.NotFound, findContact("Vikas Gym", PHONE_BOOK, "+91"))
    }

    @Test fun theSameNumberSavedTwiceIsOneChoice() {
        val c = findContact("Kavita", PHONE_BOOK, "+91")
        assertTrue(c.toString(), c is ContactLookup.Clear)
    }

    @Test fun starredComesFirstWhenTied() {
        val found = findContact("Priya", PHONE_BOOK, "+91") as ContactLookup.Choose
        assertEquals("Priya Shah", found.options.first().person.name)
    }

    @Test fun messagesLookUpTheirRecipientToo() {
        val (command, found) = lookup("Sunil la whatsapp kar ani sang ki udya ye")
        assertTrue(command is Command.Message)
        assertNull((command as Command.Message).dictation.person)
        assertEquals("Sunil Patil", (found as ContactLookup.Clear).match.person.name)
        val (tell, _) = lookup("सुनीलला सांग की उद्या ये")
        assertEquals("सुनील", (tell as Command.Message).dictation.askedName)
    }

    @Test fun pronounsAreNotNames() {
        assertTrue(RuleIntentParser.parse("त्याला सांग की उद्या ये", saved) !is Command.Message ||
            (RuleIntentParser.parse("त्याला सांग की उद्या ये", saved) as Command.Message).dictation.askedName == null)
    }

    @Test fun jaroWinklerBehaves() {
        assertEquals(0.961, jaroWinkler("martha", "marhta"), 0.001)
        assertEquals(1.0, jaroWinkler("sunil", "sunil"), 0.0)
        assertTrue(jaroWinkler("rohit", "rahul") < CONTACT_MIN)
    }

    @Test fun devanagariAndLatinNamesScoreTheSame() {
        assertEquals(1.0, nameScore("सुनील", "Sunil Patil"), 0.001)
        assertEquals(1.0, nameScore("Pankaj", "पंकज"), 0.001)
        assertTrue(nameScore("Rahul", "Rohit Sharma") < CONTACT_MIN)
    }
}
