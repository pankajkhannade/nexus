package dev.pankajkhannade.nexus.ui

import android.Manifest
import android.speech.SpeechRecognizer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.FakeSpeechEngine
import dev.pankajkhannade.nexus.ListeningScreen
import dev.pankajkhannade.nexus.MessageScreen
import dev.pankajkhannade.nexus.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** S6 Listening with a fake recogniser: live words, restarts after early cut-offs, Done. */
@RunWith(RobolectricTestRunner::class)
class ListeningUiTest {
    @get:Rule val compose = createComposeRule()

    private val fake = FakeSpeechEngine()

    private fun model() = newModel(people = 1).also { it.useSpeechEngine(fake) { fake.clock } }

    @Test fun liveWordsJoinAcrossCutOffsAndDoneOpensTheMessage() {
        val model = model()
        model.startListening()
        compose.setParent { ListeningScreen(model) }
        compose.onNodeWithText("Speak now. Take your time, then tap Done.").assertIsDisplayed()

        fake.partial("Pankaj la whatsapp kar")
        fake.endOfSpeech()
        fake.error(SpeechRecognizer.ERROR_NO_MATCH)
        fake.partial("ani sang ki tu kadhi yenar")
        compose.onNodeWithText("“Pankaj la whatsapp kar ani sang ki tu kadhi yenar”").assertIsDisplayed()
        fake.final("ani sang ki tu kadhi yenar ahes")
        assertEquals("still listening after a message pause", 3, fake.starts)
        fake.partial("ani yetana sabun ghevun ye")

        compose.onNodeWithText("✓  Done").performScrollTo().performClick()

        assertTrue(model.screen.toString(), model.screen == Screen.Message)
        assertEquals("Pankaj", model.draft?.person?.name)
        assertTrue(model.draft!!.text, model.draft!!.text.contains("sabun ghevun ye"))
        assertTrue(model.draft!!.text, model.draft!!.text.contains("kadhi yenar ahes"))
    }

    @Test fun anEarlyTimeoutRestartsAndAWholeCallNeedsNoDone() {
        val model = model()
        model.startListening()
        fake.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals(2, fake.starts)
        assertTrue(model.screen == Screen.Listening)
        fake.final("Pankaj la")
        assertTrue("half a command keeps listening", model.screen == Screen.Listening)
        fake.final("phone lav")
        assertTrue(model.screen.toString(), model.screen is Screen.Calling)
    }

    @Test fun stopReturnsHomeWithoutActing() {
        val model = model()
        model.startListening()
        compose.setParent { ListeningScreen(model) }
        fake.partial("Pankaj la phone")
        compose.onNodeWithText("Stop").performScrollTo().performClick()
        assertTrue(model.screen == Screen.Home)
    }

    @Test fun speakAgainOnTheMessageScreenListensThroughPausesUntilDone() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val model = model()
        model.understood("whatsapp Pankaj", typed = true)
        compose.setParent { MessageScreen(model) }
        compose.onNodeWithText("🎤  Speak again").performScrollTo().performClick()

        fake.partial("I will be late")
        fake.error(SpeechRecognizer.ERROR_NO_MATCH)
        fake.partial("keep dinner")
        compose.onNodeWithText("“I will be late keep dinner”").assertIsDisplayed()
        compose.onNodeWithText("✓  Done").performScrollTo().performClick()

        compose.onNodeWithText("I will be late keep dinner.").assertIsDisplayed()
        assertEquals("I will be late keep dinner.", model.draft?.text)
    }
}
