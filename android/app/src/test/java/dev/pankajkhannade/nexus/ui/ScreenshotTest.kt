package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pankajkhannade.nexus.CabApp
import dev.pankajkhannade.nexus.Event
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.PrivacyScreen
import dev.pankajkhannade.nexus.RideScreen
import dev.pankajkhannade.nexus.SettingsScreen
import dev.pankajkhannade.nexus.UiMode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.SafetyScreen
import dev.pankajkhannade.nexus.MessageScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshots on the JVM (no emulator). Goldens live in app/src/test/roborazzi/.
 * Record: ./gradlew :app:recordRoborazziDebug   Verify: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/roborazzi/$name.png")

    @Test fun parentHome_en() {
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        shot("parent_home_en")
    }

    @Config(qualifiers = "+mr")
    @Test fun parentHome_mr() {
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        shot("parent_home_mr")
    }

    @Config(qualifiers = "+mr")
    @Test fun safetyDanger_mr() {
        val model = newModel(people = 1)
        model.openCheck("तुमचे बँक खाते बंद होईल. OTP सांगा.")
        compose.setParent { SafetyScreen(model) }
        shot("safety_danger_mr")
    }

    // ---- S10 Ride home ----
    @Test fun rideHome_en() {
        fakeInstall(CabApp.UBER.pkg); fakeInstall(CabApp.OLA.pkg); fakeInstall(CabApp.RAPIDO.pkg)
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        model.rememberCab(CabApp.OLA)
        compose.setParent { RideScreen(model) }
        shot("ride_home_en")
    }

    @Config(qualifiers = "+mr")
    @Test fun rideHome_noCabApp_mr() {
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        compose.setParent { RideScreen(model) }
        shot("ride_home_no_cab_mr")
    }

    // ---- S11 Family settings ----
    @Test fun familySettings_en() {
        val model = newModel(people = 2, home = "Shivaji Nagar, Pune")
        compose.setContent { NexusTheme(mode = UiMode.Child) { SettingsScreen(model) } }
        shot("family_settings_en")
    }

    @Test fun familySettingsExport_en() {
        val model = newModel(people = 2)
        model.record(Event.CALL); model.record(Event.SAFETY_CHECK); model.record(Event.RIDE)
        compose.setContent { NexusTheme(mode = UiMode.Child) { SettingsScreen(model) } }
        compose.onNodeWithText("Export my usage").performScrollTo().performClick()
        compose.onNodeWithText("Share").performScrollTo()
        shot("family_settings_export_en")
    }

    // ---- S12 feedback card ----
    @Config(qualifiers = "+mr")
    @Test fun feedbackQ1_mr() {
        val model = newModel(people = 2)
        setUpDaysAgo(model, 3)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        shot("feedback_q1_mr")
    }

    @Test fun feedbackQ2_en() {
        val model = newModel(people = 2)
        setUpDaysAgo(model, 7)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithText("A little").performClick()
        shot("feedback_q2_en")
    }

    // ---- S13 Privacy ----
    @Config(qualifiers = "+mr")
    @Test fun privacy_mr() {
        val model = newModel(people = 1)
        compose.setParent { PrivacyScreen(model) {} }
        shot("privacy_mr")
    }

    @Test fun privacy_en() {
        val model = newModel(people = 1)
        compose.setParent { PrivacyScreen(model) {} }
        shot("privacy_en")
    }

    // ---- Voice-dictated message confirm ----
    @Config(qualifiers = "+mr")
    @Test fun messageConfirm_mr() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 1)
        model.understood("पंकचला वाटसब कर आणि सांग की तू कधी येणार आहेस घरी. आणी येताना एक साबून घेऊन ये.", typed = true)
        compose.setParent { MessageScreen(model) }
        shot("message_confirm_mr")
    }

    @Test fun messageConfirm_en() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 1)
        model.understood("whatsapp Pankaj when will you come home and bring soap on the way", typed = true)
        compose.setParent { MessageScreen(model) }
        shot("message_confirm_en")
    }

    // ---- S15 Near home ----
    private fun nearHomeModel() = newModel(people = 2).homeWithCoordinates().also {
        fakeInstall(BLINKIT); fakeInstall(URBAN_COMPANY); fakeInstall(TATA_1MG)
        it.pickService(dev.pankajkhannade.nexus.Need.GROCERY, BLINKIT)
        it.pickService(dev.pankajkhannade.nexus.Need.MEDICINE, TATA_1MG)
        it.pickService(dev.pankajkhannade.nexus.Need.HOME_SERVICE, URBAN_COMPANY)
    }

    @Config(qualifiers = "+mr")
    @Test fun nearHome_mr() {
        val model = nearHomeModel()
        compose.setParent { dev.pankajkhannade.nexus.NearHomeScreen(model) }
        shot("near_home_mr")
    }

    @Test fun nearHome_en() {
        val model = nearHomeModel()
        compose.setParent { dev.pankajkhannade.nexus.NearHomeScreen(model) }
        shot("near_home_en")
    }

    @Config(qualifiers = "+mr")
    @Test fun nearEmergency_mr() {
        val model = nearHomeModel()
        compose.setParent { dev.pankajkhannade.nexus.EmergencyScreen(model) }
        shot("near_emergency_mr")
    }

    @Test fun nearSetupDropdown_en() {
        fakeInstall(ZEPTO); fakeInstall("com.bigbasket.mobileapp")
        val model = newModel(people = 1)
        model.setupHome = "Baner Road, Pune 411045"
        model.setupServices = emptyMap()
        model.setupPincode = ""
        compose.setContent { NexusTheme(mode = UiMode.Child) { dev.pankajkhannade.nexus.NearHomeStep(model) } }
        compose.onNodeWithTag("picker_grocery").performClick()
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/roborazzi/near_setup_dropdown_en.png")
    }

    // ---- Real-phone feedback 1: listening, phone book, destinations ----
    @Test fun listeningLiveWords_en() {
        val fake = dev.pankajkhannade.nexus.FakeSpeechEngine()
        val model = newModel(people = 1)
        model.useSpeechEngine(fake) { fake.clock }
        model.startListening()
        fake.partial("WhatsApp Pankaj and tell him")
        fake.error(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
        fake.partial("when will you come home")
        compose.setParent { dev.pankajkhannade.nexus.ListeningScreen(model) }
        shot("listening_live_en")
    }

    @Config(qualifiers = "+mr")
    @Test fun whichContact_mr() {
        val model = newModel(people = 1)
        model.contacts = dev.pankajkhannade.nexus.FakeContacts()
        model.io = kotlinx.coroutines.Dispatchers.Unconfined
        model.understood("राहुलला फोन लाव", typed = true)
        val choose = model.screen as dev.pankajkhannade.nexus.Screen.ChooseContact
        compose.setParent { dev.pankajkhannade.nexus.ChooseContactScreen(model, choose) }
        shot("which_contact_mr")
    }

    @Config(qualifiers = "+mr")
    @Test fun destinationConfirm_mr() {
        val model = newModel(people = 1, home = "Baner, Pune")
        model.placeFinder = dev.pankajkhannade.nexus.PlaceFinder { _, _, _ ->
            listOf(
                dev.pankajkhannade.nexus.Place("दीनानाथ मंगेशकर हॉस्पिटल, एरंडवणे", 18.5089, 73.8323),
                dev.pankajkhannade.nexus.Place("दीनानाथ मंगेशकर हॉस्पिटल ओपीडी, कर्वे रोड", 18.5080, 73.8310),
            )
        }
        model.io = kotlinx.coroutines.Dispatchers.Unconfined
        model.understood("मला दीनानाथ हॉस्पिटलला जायचं", typed = true)
        compose.setParent { dev.pankajkhannade.nexus.DestinationScreen(model) }
        shot("destination_confirm_mr")
    }
}
