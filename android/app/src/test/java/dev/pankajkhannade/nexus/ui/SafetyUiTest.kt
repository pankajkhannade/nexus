package dev.pankajkhannade.nexus.ui

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.SafetyScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S8/S9 "Is this safe?": the verdict a parent sees, and the hand-off to family. */
@RunWith(RobolectricTestRunner::class)
class SafetyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun otpScamShowsDangerAndTheNeverShareWarning() {
        val model = newModel(people = 1)
        model.openCheck("Your SBI account is blocked. Share the OTP sent to your phone to unblock it now.")
        compose.setParent { SafetyScreen(model) }

        compose.onNodeWithText("Stop! This could be a scam").assertExists()
        compose.onNodeWithText("Never share OTP or PIN with anyone. Banks never ask for it.").performScrollTo()
    }

    @Test fun harmlessMessageNeverSaysSafe() {
        val model = newModel(people = 1)
        model.openCheck("Lunch at 1 tomorrow? Bring the photos.")
        compose.setParent { SafetyScreen(model) }

        compose.onNodeWithText("No warning signs found").assertExists()
        compose.onNodeWithText("This does not mean it is safe", substring = true).assertExists()
        compose.onAllNodesWithText("Safe", ignoreCase = false).assertCountEquals(0)
    }

    @Test fun askFamilyOpensWhatsAppWithTheMessageQuoted() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 1)
        model.openCheck("Your KYC expires today. Click bit.ly/kyc-upd8 and enter your PIN.")
        compose.setParent { SafetyScreen(model) }

        // Pinned above the bottom bar: on screen without scrolling.
        compose.onNodeWithText("Ask Pankaj", substring = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("OK").assertIsDisplayed().performClick()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        val link = intent.dataString!!
        assertTrue(link, link.startsWith("https://wa.me/919876543210?text="))
        assertTrue("message is quoted for family", link.contains("kyc-upd8"))
    }
}
