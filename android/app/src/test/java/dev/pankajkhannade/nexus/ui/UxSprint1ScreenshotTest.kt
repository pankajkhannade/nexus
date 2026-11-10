package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pankajkhannade.nexus.CabApp
import dev.pankajkhannade.nexus.NotUnderstoodScreen
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.Place
import dev.pankajkhannade.nexus.RideScreen
import dev.pankajkhannade.nexus.SafetyScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UX sprint 1 goldens on a small phone (360x640dp) in en / mr / hi, at text sizes 1.0, 1.3 and 1.5.
 * Record: ./gradlew :app:recordRoborazziDebug   Verify: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class UxSprint1ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetFont() = RuntimeEnvironment.setFontScale(1f)

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/roborazzi/ux1/$name.png")

    private fun home(name: String, scale: Float = 1f) {
        RuntimeEnvironment.setFontScale(scale)
        fakeInstall("com.whatsapp")
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        shot(name)
    }

    @Test fun home_en() = home("home_360_en")
    @Config(qualifiers = "+mr") @Test fun home_mr() = home("home_360_mr")
    @Config(qualifiers = "+hi") @Test fun home_hi() = home("home_360_hi")
    @Config(qualifiers = "+mr") @Test fun home_mr_13() = home("home_360_mr_fs13", 1.3f)
    @Config(qualifiers = "+mr") @Test fun home_mr_15() = home("home_360_mr_fs15", 1.5f)
    @Test fun home_en_15() = home("home_360_en_fs15", 1.5f)

    @Config(qualifiers = "+mr")
    @Test fun faces_sameInitial_withPhoto_mr() {
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        val p = model.saved.people[2]
        model.savePerson(p, p.name, p.phone, p.relation, p.askFamily, p.alias, writeTestFace())
        idleMain()
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        shot("faces_360_mr")
    }

    private fun notUnderstood(name: String, said: String, scale: Float = 1f) {
        RuntimeEnvironment.setFontScale(scale)
        val model = newModel(people = 4)
        model.understood(said, typed = true)
        compose.setParent { NotUnderstoodScreen(model) {} }
        shot(name)
    }

    @Test fun notUnderstood_en() = notUnderstood("not_understood_360_en", "Priya rain today")
    @Config(qualifiers = "+mr") @Test fun notUnderstood_mr() = notUnderstood("not_understood_360_mr", "प्रिया आज पाऊस खूप आहे")
    @Config(qualifiers = "+hi") @Test fun notUnderstood_hi() = notUnderstood("not_understood_360_hi", "प्रिया आज बारिश बहुत है")
    @Config(qualifiers = "+mr") @Test fun notUnderstood_mr_15() = notUnderstood("not_understood_360_mr_fs15", "प्रिया आज पाऊस खूप आहे", 1.5f)

    private fun scam(name: String, text: String, scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        val model = newModel(people = 1)
        model.openCheck(text)
        compose.setParent { SafetyScreen(model) }
        shot(name)
    }

    @Test fun scam_en_15() = scam("scam_360_en_fs15", "Your SBI account is blocked. Share the OTP sent to your phone to unblock it now.", 1.5f)
    @Config(qualifiers = "+mr") @Test fun scam_mr_13() = scam("scam_360_mr_fs13", "तुमचे बँक खाते बंद होईल. OTP सांगा.", 1.3f)
    @Config(qualifiers = "+mr") @Test fun scam_mr_15() = scam("scam_360_mr_fs15", "तुमचे बँक खाते बंद होईल. OTP सांगा.", 1.5f)
    @Config(qualifiers = "+hi") @Test fun scam_hi_15() = scam("scam_360_hi_fs15", "आपका बैंक खाता बंद हो जाएगा. OTP बताइए.", 1.5f)

    @Config(qualifiers = "+mr")
    @Test fun rideCoach_rapido_mr() {
        fakeInstall(CabApp.RAPIDO.pkg)
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        model.chooseDestination(Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636))
        compose.setParent { RideScreen(model) }
        shot("ride_coach_rapido_360_mr")
    }

    @Config(qualifiers = "+mr")
    @Test fun rideLink_ola_mr() {
        fakeInstall(CabApp.OLA.pkg); fakeInstall(CabApp.RAPIDO.pkg)
        val model = newModel(people = 1, home = "Shivaji Nagar, Pune")
        model.rememberCab(CabApp.OLA)
        model.chooseDestination(Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636))
        compose.setParent { RideScreen(model) }
        shot("ride_link_ola_360_mr")
    }

    /** Re-take of screenshot 21: the emulator caught an ANR dialog there, not Nexus's own state. */
    @Config(qualifiers = "+mr")
    @Test fun readingPicture_mr() {
        val model = newModel(people = 1)
        model.screen = dev.pankajkhannade.nexus.Screen.Safety
        model.checking = true
        // The spinner never stops: freeze the clock so the test (and the next one in this JVM) can go idle.
        compose.mainClock.autoAdvance = false
        compose.setParent { SafetyScreen(model) }
        compose.mainClock.advanceTimeBy(500)
        shot("reading_picture_360_mr")
    }
}
