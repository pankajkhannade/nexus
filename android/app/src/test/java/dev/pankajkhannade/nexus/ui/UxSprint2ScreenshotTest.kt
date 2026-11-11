package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pankajkhannade.nexus.AppCategory
import dev.pankajkhannade.nexus.CallingScreen
import dev.pankajkhannade.nexus.ConfirmScreen
import dev.pankajkhannade.nexus.HelpScreen
import dev.pankajkhannade.nexus.ListeningScreen
import dev.pankajkhannade.nexus.MoneyGoneScreen
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.NotUnderstoodScreen
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.Place
import dev.pankajkhannade.nexus.SafetyScreen
import dev.pankajkhannade.nexus.SettingsScreen
import dev.pankajkhannade.nexus.SetupFlow
import dev.pankajkhannade.nexus.SetupPage
import dev.pankajkhannade.nexus.UiMode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UX sprint 2 goldens on a small phone (360x640dp), Marathi first, Hindi for home and the money-gone steps,
 * and 2.0x text for home, not understood, the scam warning and setup.
 * Record: ./gradlew :app:recordRoborazziDebug   Verify: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class UxSprint2ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetFont() = RuntimeEnvironment.setFontScale(1f)

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/roborazzi/ux2/$name.png")

    private fun family(people: Int = 4): NexusViewModel = newModel(people = people, home = "Shivaji Nagar, Pune")

    // ---- Home: help, did it work?, Devanagari names ----
    private fun home(name: String, scale: Float = 1f, ask: AppCategory? = null, devanagari: Boolean = false) {
        RuntimeEnvironment.setFontScale(scale)
        fakeInstall("com.whatsapp")
        val model = family()
        if (devanagari) {
            val names = listOf("पंकज", "प्रिया", "आई")
            model.saved.people.take(3).forEachIndexed { i, p -> model.savePerson(p, p.name, p.phone, p.relation, p.askFamily, p.alias, p.photo, names[i]) }
            idleMain()
        }
        model.returnCheck = ask
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        shot(name)
    }

    @Config(qualifiers = "+mr") @Test fun home_help_mr() = home("home_help_360_mr")
    @Config(qualifiers = "+hi") @Test fun home_help_hi() = home("home_help_360_hi")
    @Config(qualifiers = "+mr") @Test fun home_did_it_work_mr() = home("home_did_it_work_360_mr", ask = AppCategory.DIALER)
    @Config(qualifiers = "+hi") @Test fun home_did_it_work_hi() = home("home_did_it_work_360_hi", ask = AppCategory.CAB)
    @Config(qualifiers = "+mr") @Test fun home_devanagari_mr() = home("home_devanagari_names_360_mr", devanagari = true)
    @Config(qualifiers = "+mr") @Test fun home_mr_fs20() = home("home_360_mr_fs20", 2f)

    @Config(qualifiers = "+mr")
    @Test fun help_mr() {
        val model = family(2)
        compose.setParent { HelpScreen(model) }
        shot("help_360_mr")
    }

    // ---- Countdown and the clear names ----
    @Config(qualifiers = "+mr")
    @Test fun calling_mr() {
        val model = family(1)
        compose.mainClock.autoAdvance = false
        compose.setParent { CallingScreen(model, model.saved.people[0]) }
        compose.mainClock.advanceTimeByFrame()
        shot("calling_countdown_5s_360_mr")
    }

    @Config(qualifiers = "+mr")
    @Test fun confirm_mr() {
        val model = family(1)
        compose.setParent { ConfirmScreen(model, model.saved.people[0]) }
        shot("confirm_360_mr")
    }

    @Config(qualifiers = "+mr")
    @Test fun listening_mr() {
        val model = family(1)
        val fake = dev.pankajkhannade.nexus.FakeSpeechEngine()
        model.useSpeechEngine(fake) { fake.clock }
        compose.setParent { ListeningScreen(model) }
        shot("listening_360_mr")
    }

    @Config(qualifiers = "+mr")
    @Test fun countdown_setting_mr() {
        val model = family(2)
        compose.setContent { NexusTheme(mode = UiMode.Child) { SettingsScreen(model) } }
        compose.onNodeWithTag("countdown_choice").performScrollTo()
        shot("countdown_setting_360_mr")
    }

    // ---- Honest setup ----
    private fun setup(name: String, page: SetupPage, scale: Float = 1f, home: String = "") {
        RuntimeEnvironment.setFontScale(scale)
        val model = family(2)
        model.setupPeople = model.saved.people
        model.setupHome = home
        model.lookupHome = { Place(it, 18.53, 73.85) to "411005" }
        model.planSetup(micGranted = false, callGranted = false)
        model.setupStep = model.setupPlan.indexOf(page)
        compose.setContent { NexusTheme(mode = UiMode.Child) { SetupFlow(model) } }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        shot(name)
    }

    @Config(qualifiers = "+mr") @Test fun setup_language_mr() = setup("setup_step1_360_mr", SetupPage.LANGUAGE)
    @Config(qualifiers = "+mr") @Test fun setup_home_pin_mr() = setup("setup_home_pin_360_mr", SetupPage.HOME, home = "Shivaji Nagar, Pune")
    @Config(qualifiers = "+mr") @Test fun setup_mic_mr() = setup("setup_mic_step4_360_mr", SetupPage.MIC)
    @Config(qualifiers = "+mr") @Test fun setup_handover_mr() = setup("setup_all_set_360_mr", SetupPage.HANDOVER)
    @Config(qualifiers = "+mr") @Test fun setup_language_mr_fs20() = setup("setup_step1_360_mr_fs20", SetupPage.LANGUAGE, 2f)

    // ---- 2.0x text: not understood and the scam warning ----
    @Config(qualifiers = "+mr")
    @Test fun notUnderstood_mr_fs20() {
        RuntimeEnvironment.setFontScale(2f)
        val model = family()
        model.understood("प्रिया आज पाऊस खूप आहे", typed = true)
        compose.setParent { NotUnderstoodScreen(model) {} }
        shot("not_understood_360_mr_fs20")
    }

    @Config(qualifiers = "+mr")
    @Test fun scam_mr_fs20() {
        RuntimeEnvironment.setFontScale(2f)
        val model = family(1)
        model.openCheck("तुमचे बँक खाते बंद होईल. OTP सांगा.")
        compose.setParent { SafetyScreen(model) }
        shot("scam_360_mr_fs20")
    }

    // ---- पैसे गेले ----
    private fun moneyGone(name: String, scrollTo: String? = null) {
        fakeInstall("com.whatsapp")
        val model = family(1)
        model.openCheck("तुमचे बँक खाते बंद होईल. OTP सांगा.")
        model.openMoneyGone()
        compose.setParent { MoneyGoneScreen(model) }
        scrollTo?.let { compose.onNodeWithTag(it).performScrollTo() }
        shot(name)
    }

    @Config(qualifiers = "+mr") @Test fun scam_entry_mr() {
        val model = family(1)
        model.openCheck("तुमचे बँक खाते बंद होईल. OTP सांगा.")
        compose.setParent { SafetyScreen(model) }
        compose.onNodeWithTag("money_gone").performScrollTo()
        shot("scam_money_gone_button_360_mr")
    }
    @Config(qualifiers = "+mr") @Test fun money_gone_mr() = moneyGone("money_gone_360_mr")
    @Config(qualifiers = "+mr") @Test fun money_gone_mr_2() = moneyGone("money_gone_360_mr_steps3_4", "money_step_4")
    @Config(qualifiers = "+hi") @Test fun money_gone_hi() = moneyGone("money_gone_360_hi")
    @Config(qualifiers = "+hi") @Test fun money_gone_hi_2() = moneyGone("money_gone_360_hi_steps3_4", "money_step_4")
}
