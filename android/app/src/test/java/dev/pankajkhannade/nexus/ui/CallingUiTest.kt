package dev.pankajkhannade.nexus.ui

import android.Manifest
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pankajkhannade.nexus.CallingScreen
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/** S7: the countdown (5 s by default; 3 / 8 s in Family settings). Virtual time (mainClock) means these run in milliseconds, not seconds. */
@RunWith(RobolectricTestRunner::class)
class CallingUiTest {
    @get:Rule val compose = createComposeRule()

    private fun showCalling(model: NexusViewModel) {
        val aai = model.saved.people.first { it.name == "Pankaj" }
        model.startCall(aai)
        compose.mainClock.autoAdvance = false
        compose.setParent {
            when (model.screen) {
                is Screen.Calling -> CallingScreen(model, (model.screen as Screen.Calling).person)
                else -> Text("home")
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun stopDuringCountdownCancelsTheCall() {
        val model = newModel(people = 1)
        showCalling(model)
        compose.onNodeWithText("Calling Pankaj").assertExists()
        compose.onNodeWithText("5").assertExists()

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithText("✕  Cancel").performClick()
        compose.mainClock.advanceTimeBy(9_000)

        compose.onNodeWithText("home").assertExists()
        assertNull("Stop must not dial", nextStartedIntent())
    }

    @Test fun withoutCallPermissionTheDialerOpensWithTheNumber() {
        val model = newModel(people = 1)
        showCalling(model)
        compose.mainClock.advanceTimeBy(5_500)
        compose.waitForIdle()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:+919876543210", intent.dataString)
        assertEquals(Screen.Home, model.screen)
    }

    @Test fun withCallPermissionTheCallIsPlacedDirectly() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val model = newModel(people = 1)
        showCalling(model)
        compose.mainClock.advanceTimeBy(5_500)
        compose.waitForIdle()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_CALL, intent.action)
        assertEquals("tel:+919876543210", intent.dataString)
    }

    @Test fun theCountdownIsSpokenAloudInTheParentsLanguage() {
        val model = newModel(people = 1)
        // Robolectric's TextToSpeech fake: finish engine start-up, then read what was spoken.
        ShadowTextToSpeech.getLastTextToSpeechInstance()?.let { shadowOf(it).onInitListener.onInit(TextToSpeech.SUCCESS) }
        showCalling(model)
        compose.mainClock.advanceTimeByFrame()
        idleMain()

        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()!!
        // Spoken in the parent's language (Marathi by default), even though this test JVM is in English.
        assertEquals("Pankaj ला फोन लावत आहे", shadowOf(tts).lastSpokenText)
    }

    @Test fun theDefaultCountdownIsFiveSecondsAndDoesNotDialEarly() {
        val model = newModel(people = 1)
        assertEquals(5, model.saved.countdownSeconds)
        showCalling(model)
        compose.mainClock.advanceTimeBy(4_200)
        assertNull("nothing is dialled before 5 s", nextStartedIntent())
        compose.mainClock.advanceTimeBy(1_300)
        compose.waitForIdle()
        assertEquals(Intent.ACTION_DIAL, nextStartedIntent()!!.action)
    }

    @Test fun aThreeSecondSettingIsUsed() {
        val model = newModel(people = 1)
        model.setCountdown(3)
        idleMain()
        showCalling(model)
        compose.onNodeWithText("3").assertExists()
        compose.mainClock.advanceTimeBy(3_500)
        compose.waitForIdle()
        assertEquals(Intent.ACTION_DIAL, nextStartedIntent()!!.action)
    }

    @Test fun anEightSecondSettingWaitsEightSeconds() {
        val model = newModel(people = 1)
        model.setCountdown(8)
        idleMain()
        showCalling(model)
        compose.onNodeWithText("8").assertExists()
        compose.mainClock.advanceTimeBy(7_200)
        assertNull(nextStartedIntent())
        compose.mainClock.advanceTimeBy(1_300)
        compose.waitForIdle()
        assertEquals(Intent.ACTION_DIAL, nextStartedIntent()!!.action)
    }

    @Test fun anOddValueFallsBackToFive() {
        val model = newModel(people = 1)
        model.setCountdown(42)
        assertEquals(5, model.saved.countdownSeconds)
    }
}
