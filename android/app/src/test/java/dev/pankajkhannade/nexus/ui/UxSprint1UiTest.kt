package dev.pankajkhannade.nexus.ui

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.CabApp
import dev.pankajkhannade.nexus.FaceKey
import dev.pankajkhannade.nexus.MessageScreen
import dev.pankajkhannade.nexus.NotUnderstoodScreen
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.Place
import dev.pankajkhannade.nexus.RideScreen
import dev.pankajkhannade.nexus.SafetyScreen
import dev.pankajkhannade.nexus.Screen
import dev.pankajkhannade.nexus.SetupFlow
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.UiMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * UX sprint 1 acceptance checks (docs/ux-critique, section 5), on a small phone: 360x640dp, the size of a
 * Rs 8-12k Android with default display size. "Displayed" means on screen without scrolling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class UxSprint1UiTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetFont() = RuntimeEnvironment.setFontScale(1f)

    private fun faceOf(name: String): String =
        compose.onNodeWithTag("face:$name", useUnmergedTree = true).fetchSemanticsNode().config[FaceKey]

    // ---- 1. Faces ----
    @Test fun twoPeopleWithTheSameInitialLookDifferent() {
        val model = newModel(people = 2) // Pankaj and Priya: both "P"
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        assertNotEquals(faceOf("Pankaj"), faceOf("Priya"))
        // The full first name is written under each face, not just a letter.
        compose.onNodeWithText("Pankaj").assertIsDisplayed()
        compose.onNodeWithText("Priya").assertIsDisplayed()
    }

    @Test fun aPhotoIsShownWhenThereIsOne() {
        val model = newModel(people = 2)
        val photo = writeTestFace()
        val p = model.saved.people[0]
        model.savePerson(p, p.name, p.phone, p.relation, p.askFamily, p.alias, photo)
        idleMain()
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        assertEquals("photo", faceOf("Pankaj"))
        assertEquals("color1", faceOf("Priya"))
    }

    // ---- 2. Home on one screen ----
    @Test fun homeFitsOneSmallScreenWithFourPeople() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        FAMILY.forEach { (name, _, _) ->
            compose.onNodeWithText(name).assertIsDisplayed()
            compose.onNodeWithContentDescription("Call $name").assertIsDisplayed()
        }
        compose.onNodeWithText("Is this message safe?", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Book a ride home", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Near home", substring = true).assertIsDisplayed()
        compose.onNodeWithText("WhatsApp", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("speak_bar").assertIsDisplayed()
        // The parent never sees the "for family" settings hint.
        compose.onAllNodesWithText("hold 2 seconds", substring = true).assertCountEquals(0)
    }

    @Test fun theFeedbackCardNeverPushesTheFamilyDown() {
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        setUpDaysAgo(model, 3)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        FAMILY.forEach { (name, _, _) -> compose.onNodeWithContentDescription("Call $name").assertIsDisplayed() }
        compose.onNodeWithText("Did Nexus help this week?").performScrollTo().assertIsDisplayed()
    }

    @Test fun atOnePointThreeTimesTextSpeakAndTheFirstPersonStayOnScreen() {
        RuntimeEnvironment.setFontScale(1.3f)
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithTag("speak_bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Call Pankaj").assertIsDisplayed()
    }

    @Test fun atOneAndAHalfTimesTextSpeakAndTheFirstPersonStayOnScreen() {
        RuntimeEnvironment.setFontScale(1.5f)
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithTag("speak_bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Call Pankaj").assertIsDisplayed()
    }

    // ---- 3. Didn't understand ----
    @Config(qualifiers = "+mr")
    @Test fun notUnderstoodShowsWhatWasHeardAndSaysItAloud() {
        val model = newModel(people = 4)
        model.understood("प्रिया आज पाऊस खूप आहे", typed = true)
        compose.setParent { NotUnderstoodScreen(model) {} }
        compose.onNodeWithText("ऐकलं: “प्रिया आज पाऊस खूप आहे”").assertIsDisplayed()
        compose.onNodeWithText("🎤  पुन्हा बोला").assertIsDisplayed()
        assertTrue(model.spoken.last(), model.spoken.last().startsWith("मी ऐकलं: प्रिया आज पाऊस खूप आहे."))
        // No reference to photos that don't exist.
        compose.onAllNodesWithText("फोटो", substring = true).assertCountEquals(0)
        // The bottom bar: Back and Speak in the same place as every parent screen.
        compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
    }

    @Test fun notUnderstoodOffersThePersonItHeardFirst() {
        val model = newModel(people = 4)
        model.understood("Aai something something", typed = true)
        assertEquals("Aai", model.candidatePeople().first().name)
        compose.setParent { NotUnderstoodScreen(model) {} }
        compose.onNodeWithText("Call Aai", substring = true).performScrollTo().performClick()
        assertEquals("Aai", (model.screen as Screen.Calling).person.name)
    }

    @Config(qualifiers = "+hi")
    @Test fun notUnderstoodSaysWhenItHeardNothing() {
        val model = newModel(people = 1)
        model.understood("", typed = true)
        compose.setParent { NotUnderstoodScreen(model) {} }
        compose.onNodeWithText("कुछ सुनाई नहीं दिया।").assertIsDisplayed()
        compose.onAllNodesWithText("फ़ोटो", substring = true).assertCountEquals(0)
    }

    // ---- 4. Scam verdict: the safe action is on screen ----
    @Config(qualifiers = "+mr")
    @Test fun askChildIsOnScreenAtLargeTextInMarathi() {
        RuntimeEnvironment.setFontScale(1.5f)
        val model = newModel(people = 1)
        model.openCheck("तुमचे बँक खाते बंद होईल. OTP सांगा.")
        compose.setParent { SafetyScreen(model) }
        compose.onNodeWithText("Pankaj ला विचारा", substring = true).assertIsDisplayed()
    }

    @Config(qualifiers = "+hi")
    @Test fun askChildIsOnScreenAtLargeTextInHindi() {
        RuntimeEnvironment.setFontScale(1.5f)
        val model = newModel(people = 1)
        model.openCheck("आपका बैंक खाता बंद हो जाएगा. OTP बताइए.")
        compose.setParent { SafetyScreen(model) }
        compose.onNodeWithText("Pankaj से पूछें", substring = true).assertIsDisplayed()
    }

    @Test fun moneyAlreadyGoneDialsTheCyberHelpline() {
        val model = newModel(people = 1)
        model.openCheck("Your SBI account is blocked. Share the OTP sent to your phone to unblock it now.")
        compose.setParent { if (model.screen == Screen.MoneyGone) dev.pankajkhannade.nexus.MoneyGoneScreen(model) else SafetyScreen(model) }
        // Since UX sprint 2 this opens the full money-gone steps; 1930 is the first one.
        compose.onNodeWithText("Money already gone?", substring = true).performScrollTo().performClick()
        assertEquals(Screen.MoneyGone, model.screen)
        compose.onNodeWithTag("money_1930").performScrollTo().performClick()
        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:1930", intent.dataString)
    }

    @Test fun theCheckedMessageIsFoldedAway() {
        val model = newModel(people = 1)
        model.openCheck("Your KYC expires today. Click bit.ly/kyc-upd8 and enter your PIN.")
        compose.setParent { SafetyScreen(model) }
        compose.onAllNodesWithText("Message checked", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Show the message").performScrollTo().performClick()
        compose.onNodeWithText("bit.ly/kyc-upd8", substring = true).performScrollTo().assertIsDisplayed()
    }

    // ---- 5. Handoffs ----
    @Test fun whatsAppSendIsCoachedAloudBeforeItOpens() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 4)
        model.understood("पंकजला वाटसब कर आणि सांग की मी घरी आहे", typed = true)
        compose.setParent { MessageScreen(model) }
        val before = model.spoken.size
        compose.onNodeWithText("Send on WhatsApp").performScrollTo().performClick()
        assertTrue("a coaching line was said", model.spoken.size > before)
        assertEquals("WhatsApp उघडतोय. हिरवं पाठवा बटण दाबा.", model.spoken.last())
        assertEquals("com.whatsapp", nextStartedIntent()!!.`package`)
    }

    @Test fun rapidoGetsThePasteStepsSpoken() {
        fakeInstall(CabApp.RAPIDO.pkg)
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        model.chooseDestination(Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636))
        compose.setParent { RideScreen(model) }
        compose.onNodeWithText("Nexus will say each step aloud.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Rapido").performScrollTo().performClick()
        assertTrue(model.spoken.last(), model.spoken.last().startsWith("Rapido उघडतोय. “कुठे जायचे” दाबा"))
    }

    @Test fun aSpokenRideHomeIsReadBack() {
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        model.understood("घरी जायचं आहे", typed = true)
        assertTrue(model.screen == Screen.Ride)
        assertEquals("घरी जायची गाडी. कॅब ॲप उघडायला मोठं बटण दाबा.", model.spoken.last())
    }

    @Test fun setupOffersToSaveATypedPersonInPhoneContacts() {
        val model = newModel(people = 0)
        model.setupStep = 1
        assertTrue(model.putSetupPerson(null, "Raju", "+91 70000 11122", "other", askFamily = true, alias = "", typed = true))
        compose.setContent { NexusTheme(mode = UiMode.Child) { SetupFlow(model) } }
        compose.onNodeWithText("Save Raju in phone contacts").performScrollTo().performClick()
        val intent = nextStartedIntent()!!
        assertEquals(ContactsContract.Intents.Insert.ACTION, intent.action)
        assertEquals(ContactsContract.RawContacts.CONTENT_TYPE, intent.type)
        assertEquals("Raju", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
        assertEquals("+917000011122", intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        // Saved once: the offer goes away.
        compose.onAllNodesWithText("Save Raju in phone contacts").assertCountEquals(0)
    }

    @Test fun aPersonPickedFromThePhoneBookIsNotOfferedAgain() {
        val model = newModel(people = 0)
        model.setupStep = 1
        model.putSetupPerson(null, "Pankaj", "+91 98765 43210", "son", askFamily = true, alias = "", typed = false)
        compose.setContent { NexusTheme(mode = UiMode.Child) { SetupFlow(model) } }
        compose.onAllNodesWithText("phone contacts", substring = true).assertCountEquals(0)
    }

    @Test fun everyParentFlowScreenHasTheBottomBar() {
        fakeInstall(CabApp.UBER.pkg)
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        compose.setParent { RideScreen(model) }
        compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        assertTrue(model.screen == Screen.Home)
        assertFalse(model.spoken.any { it.contains("photo", ignoreCase = true) })
    }
}

/** A small solid image in faces/, standing in for a picked photo. */
fun writeTestFace(): String {
    val bmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).apply {
        drawColor(android.graphics.Color.rgb(222, 184, 135))
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(60, 40, 30) }
        drawCircle(22f, 26f, 4f, paint); drawCircle(42f, 26f, 4f, paint)
        drawRect(22f, 44f, 42f, 47f, paint)
    }
    val file = java.io.File(dev.pankajkhannade.nexus.facesDir(app), "test-face.jpg")
    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    return file.path
}
