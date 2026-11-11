package dev.pankajkhannade.nexus.ui

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import dev.pankajkhannade.nexus.Event
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.Screen
import dev.pankajkhannade.nexus.SettingsScreen
import dev.pankajkhannade.nexus.UiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S11 Family settings (child scale). */
@RunWith(RobolectricTestRunner::class)
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(model: dev.pankajkhannade.nexus.NexusViewModel) =
        compose.setContent { NexusTheme(mode = UiMode.Child) { SettingsScreen(model) } }

    @Test fun removingAPersonCanBeUndone() {
        val model = newModel(people = 2)
        show(model)

        compose.onAllNodesWithText("Remove", substring = true).onFirst().performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("Priya"), model.saved.people.map { it.name })

        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Pankaj", "Priya"), model.saved.people.map { it.name })
    }

    @Test fun changingHomeCanBeUndone() {
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        show(model)

        compose.onNode(hasSetTextAction() and hasText("Shivaji Nagar, Pune")).performScrollTo().performTextReplacement("Kothrud, Pune")
        compose.onAllNodesWithText("Save").onFirst().performScrollTo().performClick()
        assertEquals("Kothrud, Pune", model.saved.home.address)

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals("Shivaji Nagar, Pune", model.saved.home.address)
    }

    @Test fun usageCountsShowAndExportPreviewsThenShares() {
        val model = newModel(people = 1)
        val calls = model.usage.total(Event.CALL)
        model.record(Event.CALL)
        model.record(Event.RIDE)
        show(model)

        compose.onNodeWithText("Calls: ${calls + 1}", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export my usage").performScrollTo().performClick()
        compose.onNodeWithText("Nexus usage (stays on the phone unless you share it)", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No names, numbers or messages", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share").performScrollTo().performClick()

        val chooser = nextStartedIntent()!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        val text = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text.contains("calls="))
        assertTrue(text.contains("rides="))
        assertFalse("no phone numbers in the export", text.contains("+91"))
        assertFalse("no names in the export", text.contains("Pankaj"))
    }

    @Test fun typedTestCommandDoesNotCountAsVoice() {
        val model = newModel(people = 1)
        val voice = model.usage.total(Event.VOICE)
        model.understood("Pankaj ko phone lagao", typed = true)
        assertEquals(voice, model.usage.total(Event.VOICE))
        assertTrue(model.screen is Screen.Calling)
    }

    @Test fun privacyIsReachable() {
        val model = newModel(people = 1)
        show(model)
        compose.onNodeWithText("Privacy").performScrollTo().performClick()
        assertTrue(model.screen == Screen.Privacy)
    }
}
