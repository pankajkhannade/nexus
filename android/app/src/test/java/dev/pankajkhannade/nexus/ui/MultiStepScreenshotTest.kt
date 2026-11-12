package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pankajkhannade.nexus.AppCategory
import dev.pankajkhannade.nexus.BlockedScreen
import dev.pankajkhannade.nexus.Channel
import dev.pankajkhannade.nexus.Dictation
import dev.pankajkhannade.nexus.GuardReason
import dev.pankajkhannade.nexus.MessageScreen
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Multi-step and guard goldens on a small phone (360x640dp), Marathi and Hindi: the next step waiting on
 * home, the guard's "won't send" screen, and the name confirm on the message screen.
 * Record: ./gradlew :app:recordRoborazziDebug   Verify: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class MultiStepScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/roborazzi/ms/$name.png")

    private fun family(language: String): NexusViewModel =
        newModel(people = 4, home = "Shivaji Nagar, Pune").also { readyFor(it, language) }

    private fun nextStep(language: String, said: String) {
        fakeInstall("com.whatsapp")
        val model = family(language)
        model.understood(said, typed = true)
        model.cameBack()
        model.actionOpened(AppCategory.CAB)
        model.screen = Screen.Home
        model.cameBack()
        model.answerReturnCheck(true)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        shot("home_next_step_360_$language")
    }

    @Config(qualifiers = "+mr") @Test fun next_step_mr() = nextStep("mr", "स्वारगेटला ओला बुक कर आणि पंकजला सांग मी निघाले")
    @Config(qualifiers = "+hi") @Test fun next_step_hi() = nextStep("hi", "स्वारगेट के लिए कैब बुक करो और पंकज को बताओ कि मैं निकल गई हूँ")

    private fun blocked(language: String) {
        val model = family(language)
        compose.setParent { BlockedScreen(model, GuardReason.SECRET_CODE) }
        shot("guard_blocked_pin_360_$language")
    }

    @Config(qualifiers = "+mr") @Test fun blocked_mr() = blocked("mr")
    @Config(qualifiers = "+hi") @Test fun blocked_hi() = blocked("hi")

    private fun confirmName(language: String, text: String) {
        fakeInstall("com.whatsapp")
        val model = family(language)
        model.openDraft(Dictation(model.saved.people.first(), null, Channel.ANY, text, text, sure = false))
        compose.setParent { MessageScreen(model) }
        shot("message_confirm_name_360_$language")
    }

    @Config(qualifiers = "+mr") @Test fun confirm_name_mr() = confirmName("mr", "मी निघाले.")
    @Config(qualifiers = "+hi") @Test fun confirm_name_hi() = confirmName("hi", "मैं निकल गई हूँ।")
}
