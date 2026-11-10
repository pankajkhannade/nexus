package dev.pankajkhannade.nexus.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.FakeContacts
import dev.pankajkhannade.nexus.MessageScreen
import dev.pankajkhannade.nexus.ChooseContactScreen
import dev.pankajkhannade.nexus.ContactNotFoundScreen
import dev.pankajkhannade.nexus.ContactsPermissionScreen
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.Screen
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Calling and messaging anyone in the phone book by name, with a fake contacts provider. */
@RunWith(RobolectricTestRunner::class)
class ContactsUiTest {
    @get:Rule val compose = createAndroidComposeRule()

    private val book = FakeContacts()

    private fun model(): NexusViewModel = newModel(people = 1, home = "Baner, Pune").also {
        it.contacts = book
        it.io = Dispatchers.Unconfined
    }

    private fun createAndroidComposeRule() = createComposeRule()

    @Test fun aClearNameGoesStraightToTheCountdown() {
        val model = model()
        model.understood("सुनीलला फोन लाव", typed = true)
        val s = model.screen
        assertTrue(s.toString(), s is Screen.Calling && s.person.name == "Sunil Patil" && s.person.phone == "+919822011111")
    }

    @Test fun anUnsureNameAsksFirstNeverDials() {
        val model = model()
        model.understood("Patil la phone lav", typed = true)
        assertTrue(model.screen.toString(), model.screen is Screen.Confirm)
    }

    @Test fun twoRahulsShowWhichOneWithNumberEndings() {
        val model = model()
        model.understood("Rahul la phone lav", typed = true)
        val choose = model.screen as Screen.ChooseContact
        compose.setParent { ChooseContactScreen(model, choose) }
        compose.onNodeWithText("Which one?").assertIsDisplayed()
        compose.onNodeWithText("Rahul Joshi").assertIsDisplayed()
        compose.onNodeWithText("Number ending 4442").assertIsDisplayed()
        compose.onNodeWithText("Rahul Kulkarni").performScrollTo().performClick()
        val s = model.screen
        assertTrue(s.toString(), s is Screen.Calling && s.person.phone == "+919822044442")
    }

    @Test fun noMatchSaysSoAndOffersTheDialPad() {
        val model = model()
        model.understood("Suresh la phone lav", typed = true)
        assertEquals(Screen.ContactNotFound("Suresh"), model.screen)
        compose.setParent { ContactNotFoundScreen(model, "Suresh", onSpeak = {}) }
        compose.onNodeWithText("I couldn’t find “Suresh” in your phone book.").assertIsDisplayed()
        compose.onNodeWithText("📞  Open the dial pad").performScrollTo().performClick()
        assertEquals(Intent.ACTION_DIAL, nextStartedIntent()!!.action)
        assertTrue(model.screen == Screen.Home)
    }

    @Test fun withoutPermissionItExplainsFirstAndNotNowEndsAtNotFound() {
        book.granted = false
        val model = model()
        model.understood("Sunil la phone lav", typed = true)
        val ask = model.screen as Screen.ContactsPermission
        compose.setParent { ContactsPermissionScreen(model, ask, onAllow = {}) }
        compose.onNodeWithText("To call or message “Sunil”, Nexus needs to look in your phone book.").assertIsDisplayed()
        compose.onNodeWithText("Your contacts stay on this phone. Nexus never uploads or saves them.").assertIsDisplayed()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        assertTrue(model.screen.toString(), model.screen == Screen.ContactNotFound("Sunil"))
        assertEquals(0, book.loads)
    }

    @Test fun allowingThePhoneBookCarriesOnWithTheCall() {
        book.granted = false
        val model = model()
        model.understood("Sunil la phone lav", typed = true)
        book.granted = true
        model.contactsAnswered(true)
        assertTrue(model.screen.toString(), model.screen is Screen.Calling)
    }

    @Test fun theIndexIsReadOnceAndKeptInMemory() {
        val model = model()
        model.understood("Sunil la phone lav", typed = true)
        model.understood("Rahul Kulkarni la phone lav", typed = true)
        assertEquals(1, book.loads)
    }

    @Test fun aDictatedMessageFindsItsRecipientInThePhoneBook() {
        val model = model()
        model.understood("Sunil la whatsapp kar ani sang ki udya sakali ye", typed = true)
        assertEquals("Sunil Patil", model.draft?.person?.name)
        assertEquals("+919822011111", model.draft?.person?.phone)
    }

    @Test fun anAmbiguousMessageRecipientIsPickedOnTheMessageScreen() {
        val model = model()
        model.understood("Rahul la sang ki udya ye", typed = true)
        compose.setParent { MessageScreen(model) }
        compose.onNodeWithText("Rahul Kulkarni").performScrollTo().performClick()
        assertEquals("+919822044442", model.draft?.person?.phone)
    }

    @Test fun whatsAppToAPhoneBookNameAsksWhatToSay() {
        val model = model()
        model.understood("Sunil la whatsapp kar", typed = true)
        assertTrue(model.screen == Screen.Message)
        assertEquals("Sunil Patil", model.draft?.person?.name)
        assertEquals("", model.draft?.text)
    }
}
