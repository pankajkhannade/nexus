package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import dev.pankajkhannade.nexus.COUNTRIES
import dev.pankajkhannade.nexus.Helped
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.PrivacyScreen
import dev.pankajkhannade.nexus.exportText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Finishes setup on FIXED_DAY (so installDay = FIXED_DAY), then moves the clock [days] ahead. */
fun setUpDaysAgo(model: NexusViewModel, days: Long) {
    model.setupLanguage = "en"
    model.setupPeople = model.saved.people
    model.setupHome = model.saved.home.address
    model.finishSetup(COUNTRIES.first { it.code == "IN" })
    model.dayOffset += days
    idleMain()
}

/** S12 day-3 / day-7 feedback card. */
@RunWith(RobolectricTestRunner::class)
class FeedbackUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noCardOnSetupDay() {
        val model = newModel(people = 1)
        setUpDaysAgo(model, 0)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onAllNodesWithText("Did Nexus help this week?").assertCountEquals(0)
    }

    @Test fun dayThreeAskTwoQuestionsThenGoesAwayAndIsInTheExport() {
        val model = newModel(people = 1)
        setUpDaysAgo(model, 3)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }

        // The card sits after the family and tiles (UX sprint 1), so it may need a scroll on a small phone.
        compose.onNodeWithText("Did Nexus help this week?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A little").performScrollTo().performClick()
        compose.onNodeWithText("What would make it easier?").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Bigger photos")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        compose.onAllNodesWithText("What would make it easier?").assertCountEquals(0)
        compose.onAllNodesWithText("Did Nexus help this week?").assertCountEquals(0)
        assertEquals(Helped.LITTLE, model.feedback.answers[3]?.helped)
        val export = exportText(model.usage, model.feedback, model.saved.installDay, model.today, "en")
        assertTrue(export, export.contains("Day 3 feedback: helped=a_little; easier: \"Bigger photos\""))
    }

    @Test fun skippingQ2StillKeepsTheQ1Answer() {
        val model = newModel(people = 1)
        setUpDaysAgo(model, 7)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }

        compose.onNodeWithText("Yes").performScrollTo().performClick()
        compose.onNodeWithText("Skip").performScrollTo().performClick()
        compose.onAllNodesWithText("Did Nexus help this week?").assertCountEquals(0)
        assertEquals(Helped.YES, model.feedback.answers[7]?.helped)
        assertTrue(model.feedback.isClosed(7))
    }
}

/** S13 Privacy. */
@RunWith(RobolectricTestRunner::class)
class PrivacyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fiveCardsAndBack() {
        val model = newModel(people = 1)
        var back = false
        compose.setParent { PrivacyScreen(model) { back = true } }

        listOf("What is saved", "Your voice", "Messages you check", "Nothing is shared without your yes", "Internet use").forEach {
            compose.onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Not in Android backup", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("is safe", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Back").performScrollTo().performClick()
        assertTrue(back)
    }

    @Test fun voiceCardIsTruthfulWhenRecognitionIsOnline() {
        val model = newModel(people = 1)
        compose.setParent { PrivacyScreen(model) {} }
        // Robolectric has no on-device recogniser, so the card must not claim the voice stays on the phone.
        compose.onAllNodesWithText("Your voice stays on this phone.").assertCountEquals(0)
        compose.onNodeWithText("Google's speech service", substring = true).performScrollTo().assertIsDisplayed()
    }
}
