package dev.pankajkhannade.nexus.ui

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import dev.pankajkhannade.nexus.Event
import dev.pankajkhannade.nexus.MessageScreen
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/** The dictated-message confirm screen: what the parent sees, edits, hears, and what opens. */
@RunWith(RobolectricTestRunner::class)
class MessageUiTest {
    @get:Rule val compose = createComposeRule()

    private val pankajSaid = "पंकचला वाटसब कर आणि सांग की तू कधी येणार आहेस घरी. आणी येताना एक साबून घेऊन ये."
    private val pankajText = "तू घरी कधी येणार आहेस? आणि येताना एक साबून घेऊन ये."

    private fun dictate(said: String, people: Int = 4): NexusViewModel {
        val model = newModel(people = people)
        model.understood(said, typed = true)
        compose.setParent { MessageScreen(model) }
        return model
    }

    private fun textParam(intent: Intent): String? = Uri.parse(intent.dataString).getQueryParameter("text")

    @Test fun pankajsSentenceShowsWhoAndTheMessage() {
        fakeInstall("com.whatsapp")
        val model = dictate(pankajSaid)
        assertEquals(Screen.Message, model.screen)
        compose.onNodeWithText("To: Pankaj").assertExistsCompat()
        compose.onNodeWithText(pankajText).assertExistsCompat()
        compose.onNodeWithText("WhatsApp will open with this message written", substring = true).assertExistsCompat()
    }

    @Test fun sendOnWhatsAppOpensTheChatWithTheTextTypedIn() {
        fakeInstall("com.whatsapp")
        val model = dictate(pankajSaid)
        compose.onNodeWithText("Send on WhatsApp").performScrollTo().performClick()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertTrue(intent.dataString!!, intent.dataString!!.startsWith("https://wa.me/919876543210?text=%E0%A4%A4%E0%A5%82%20"))
        assertFalse("spaces are %20, not +", intent.dataString!!.contains("+"))
        assertEquals(pankajText, textParam(intent))
        assertEquals(1, model.usage.total(Event.MESSAGE))
        assertEquals(Screen.Home, model.screen)
        assertTrue("the how-to line is shown only until the first send", model.saved.messageExplained)
    }

    @Test fun tappingTheMessageEditsItBeforeSending() {
        fakeInstall("com.whatsapp")
        dictate(pankajSaid)
        compose.onNodeWithText(pankajText).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("येताना दूध पण आण.")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("येताना दूध पण आण.").assertExistsCompat()
        compose.onNodeWithText("Send on WhatsApp").performScrollTo().performClick()

        assertEquals("येताना दूध पण आण.", textParam(nextStartedIntent()!!))
    }

    @Test fun withoutWhatsAppSmsIsTheOnlyWay() {
        val model = dictate(pankajSaid)
        compose.onAllNodesWithText("Send on WhatsApp").assertCountEquals(0)
        compose.onNodeWithText("Messages will open with this message written", substring = true).assertExistsCompat()
        compose.onNodeWithText("Send as SMS").performScrollTo().performClick()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:+919876543210", intent.dataString)
        assertEquals(pankajText, intent.getStringExtra("sms_body"))
        assertEquals(1, model.usage.total(Event.MESSAGE))
    }

    @Test fun smsAskedForMeansSmsFirstButWhatsAppStillOffered() {
        fakeInstall("com.whatsapp")
        dictate("पंकजला मेसेज कर की मी उशिरा येईन")
        compose.onNodeWithText("Send on WhatsApp").assertExistsCompat()
        compose.onNodeWithText("Send as SMS").performScrollTo().performClick()
        assertEquals(Intent.ACTION_SENDTO, nextStartedIntent()!!.action)
    }

    @Test fun whatsAppBusinessIsUsedWhenItIsTheOnlyWhatsApp() {
        fakeInstall("com.whatsapp.w4b")
        dictate("pankaj ko whatsapp karo ki main late aaungi")
        compose.onNodeWithText("Send on WhatsApp").performScrollTo().performClick()
        val intent = nextStartedIntent()!!
        assertEquals("com.whatsapp.w4b", intent.`package`)
        assertEquals("Main late aaungi.", textParam(intent))
    }

    @Test fun unknownNameAsksWhoAndAFaceFixesIt() {
        fakeInstall("com.whatsapp")
        dictate("Suresh la whatsapp kar ki mi yete")
        compose.onNodeWithText("Who should get this message?").assertExistsCompat()
        compose.onNodeWithText("Suresh is not saved", substring = true).assertExistsCompat()
        compose.onAllNodesWithText("Send on WhatsApp").assertCountEquals(0)
        compose.onNodeWithText("Priya").performScrollTo().performClick()
        compose.onNodeWithText("To: Priya").assertExistsCompat()
        compose.onNodeWithText("Send on WhatsApp").performScrollTo().performClick()
        assertTrue(nextStartedIntent()!!.dataString!!.startsWith("https://wa.me/919123456789?text=Mi%20yete."))
    }

    @Test fun emptyMessageAsksWhatToSay() {
        fakeInstall("com.whatsapp")
        dictate("Pankaj la whatsapp kar")
        compose.onNodeWithText("What should I say?").assertExistsCompat()
    }

    @Test fun dontSendGoesHomeAndOpensNothing() {
        fakeInstall("com.whatsapp")
        val model = dictate(pankajSaid)
        compose.onNodeWithText("Don't send").performScrollTo().performClick()
        assertEquals(Screen.Home, model.screen)
        assertNull(model.draft)
        assertNull(nextStartedIntent())
        assertEquals(0, model.usage.total(Event.MESSAGE))
    }

    @Test fun speakingAgainReplacesTheMessage() {
        fakeInstall("com.whatsapp")
        val model = dictate(pankajSaid)
        model.redictate("येताना भाजी पण आण")
        compose.onNodeWithText("येताना भाजी पण आण.").assertExistsCompat()
        assertEquals("Pankaj", model.draft?.person?.name)
    }

    @Test fun theMessageIsReadBackInTheParentsLanguage() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 1)
        ShadowTextToSpeech.getLastTextToSpeechInstance()?.let { shadowOf(it).onInitListener.onInit(TextToSpeech.SUCCESS) }
        model.understood(pankajSaid, typed = true)
        compose.setParent { MessageScreen(model) }
        compose.waitForIdle()
        idleMain()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()!!
        assertEquals("Pankaj ला हा मेसेज पाठवू? $pankajText", shadowOf(tts).lastSpokenText)
    }

    @Test fun aCallCommandStillGoesToCalling() {
        val model = newModel(people = 1)
        model.understood("Pankaj ko phone lagao", typed = true)
        assertTrue(model.screen.toString(), model.screen is Screen.Calling || model.screen is Screen.Confirm)
        assertNull(model.draft)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = performScrollToIfPossible()

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToIfPossible() {
    assertExists()
    runCatching { performScrollTo() }
}
