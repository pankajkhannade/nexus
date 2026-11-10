package dev.pankajkhannade.nexus.ui

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParentHomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsAllFourPeopleWithACallButtonEach() {
        val model = newModel(people = 4)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }

        FAMILY.forEach { (name, _, _) ->
            compose.onNodeWithContentDescription("Call $name").performScrollTo().assertIsDisplayed()
        }
        compose.onAllNodesWithContentDescription("Call ", substring = true).assertCountEquals(4)
        // No WhatsApp on this phone: the button is hidden rather than leading to a dead end.
        compose.onAllNodesWithText("WhatsApp").assertCountEquals(0)
        // No home saved: no ride button.
        compose.onAllNodesWithText("Book a ride home", substring = true).assertCountEquals(0)
    }

    @Test fun tappingCallGoesToTheCountdownNotStraightToTheDialer() {
        val model = newModel(people = 2)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }

        compose.onNodeWithContentDescription("Call Priya").performScrollTo().performClick()

        assertEquals("Priya", (model.screen as Screen.Calling).person.name)
        assertNull("nothing is dialled before the 3-second countdown", nextStartedIntent())
    }

    @Test fun whatsAppButtonOpensWhatsAppChatForThatPerson() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 1)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }

        compose.onNodeWithContentDescription("WhatsApp Pankaj").performClick()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("https://wa.me/919876543210", intent.dataString)
    }

    @Test fun rideButtonShowsOnlyWhenHomeIsSaved() {
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }

        compose.onNodeWithText("Book a ride home", substring = true).performScrollTo().performClick()
        assertTrue(model.screen == Screen.Ride)
    }
}
