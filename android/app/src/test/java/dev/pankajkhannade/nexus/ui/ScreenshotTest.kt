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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.SafetyScreen
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
}
