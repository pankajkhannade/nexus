package dev.pankajkhannade.nexus.ui

import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.CabApp
import dev.pankajkhannade.nexus.Event
import dev.pankajkhannade.nexus.RideScreen
import dev.pankajkhannade.nexus.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S10 Ride home. */
@RunWith(RobolectricTestRunner::class)
class RideUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lastUsedAppIsTheBigButtonAndOthersAreListedBelow() {
        fakeInstall(CabApp.UBER.pkg)
        fakeInstall(CabApp.OLA.pkg)
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        model.rememberCab(CabApp.OLA)
        compose.setParent { RideScreen(model) }

        compose.onNodeWithText("Open Ola").assertIsDisplayed()
        compose.onNodeWithText("Other app").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Uber").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Rapido").assertCountEquals(0)
        compose.onAllNodesWithText("Maps", substring = true).assertCountEquals(0)
        compose.onNodeWithText("The home address will be copied", substring = true).assertIsDisplayed()
    }

    @Test fun openingOlaCopiesHomeCountsTheRideAndReturnsHome() {
        fakeInstall(CabApp.OLA.pkg)
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        val before = model.usage.total(Event.RIDE)
        model.screen = Screen.Ride
        compose.setParent { RideScreen(model) }

        compose.onNodeWithText("Open Ola").performClick()

        val intent = nextStartedIntent()!!
        assertEquals(CabApp.OLA.pkg, intent.`package` ?: intent.component?.packageName)
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertEquals("Shivaji Nagar, Pune", clip)
        assertEquals(before + 1, model.usage.total(Event.RIDE))
        assertEquals("ola", model.saved.lastCab)
        assertTrue(model.screen == Screen.Home)
    }

    @Test fun noCabAppOffersToAskFamilyBySms() {
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        compose.setParent { RideScreen(model) }

        compose.onNodeWithText("No cab app is on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Ask Pankaj", substring = true).performScrollTo().performClick()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:+919876543210", intent.dataString)
        assertEquals("Please book me a ride home.", intent.getStringExtra("sms_body"))
    }
}
