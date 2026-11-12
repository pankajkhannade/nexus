package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pankajkhannade.nexus.AppCategory
import dev.pankajkhannade.nexus.BlockedScreen
import dev.pankajkhannade.nexus.Channel
import dev.pankajkhannade.nexus.Dictation
import dev.pankajkhannade.nexus.EventType
import dev.pankajkhannade.nexus.GuardReason
import dev.pankajkhannade.nexus.MessageScreen
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.PlanQueue
import dev.pankajkhannade.nexus.PlannedStep
import dev.pankajkhannade.nexus.Screen
import dev.pankajkhannade.nexus.computeMetrics
import dev.pankajkhannade.nexus.dailyCounts
import dev.pankajkhannade.nexus.decode
import dev.pankajkhannade.nexus.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Multi-step requests and the safety guard (2026-11-12), end to end through the real ViewModel:
 * plan -> handoff -> "did it work?" -> next step on home, skip / cancel, process death, expiry, and the guard
 * on every outgoing message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class MultiStepUiTest {
    @get:Rule val compose = createComposeRule()

    /** A family set up in [language] (setup finished, the leave-behind check out of the way), like a real parent phone. */
    private fun family(people: Int, language: String = "en"): NexusViewModel = newModel(people = people).also { readyFor(it, language) }

    private fun types(model: NexusViewModel) = model.events.all().map { it.type }

    /** The parent leaves for the dialer (or another app) and comes back. */
    private fun handoff(model: NexusViewModel, category: AppCategory) {
        model.cameBack()
        model.actionOpened(category)
        model.screen = Screen.Home
        model.cameBack()
    }

    @Test fun callThenWhatsAppWaitsOnHomeAfterTheCall() {
        fakeInstall("com.whatsapp")
        val model = family(4)
        model.understood("Call Pankaj and then tell Priya I reached", typed = true)
        assertEquals(2, model.events.all().single { it.type == EventType.MULTI_STEP_STARTED }.step)
        assertTrue(model.screen is Screen.Calling)
        assertEquals("Pankaj", (model.screen as Screen.Calling).person.name)
        assertEquals(1, model.saved.plan?.next)

        handoff(model, AppCategory.DIALER)
        assertEquals(1, model.events.all().single { it.type == EventType.STEP_DONE }.step)
        assertEquals(AppCategory.DIALER, model.returnCheck)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithTag("return_check").assertIsDisplayed()
        compose.onAllNodesWithText("Next (2 of 2)").assertCountEquals(0)

        model.answerReturnCheck(true)
        compose.waitForIdle()
        compose.onNodeWithTag("next_step").assertIsDisplayed()
        compose.onNodeWithText("Message Priya").assertIsDisplayed()
        assertTrue(model.spoken.last(), model.spoken.last().contains("Next (2 of 2): Message Priya?"))

        compose.onNodeWithTag("next_step_yes").performClick()
        assertEquals(Screen.Message, model.screen)
        assertEquals("Priya", model.draft?.person?.name)
        assertNull("the last step started: nothing left to offer", model.saved.plan)
    }

    @Test fun ifHeDoesNotPickUpTheMessageIsOfferedOnlyWhenTheCallFailed() {
        val model = family(2)
        model.understood("Call Pankaj, and if he doesn't pick up, message him that I'm coming at 5", typed = true)
        handoff(model, AppCategory.DIALER)
        model.answerReturnCheck(false)
        assertEquals("a failed call offers the message, not Help", Screen.Home, model.screen)
        assertTrue(model.pendingStep()!!.onlyIfNotWorked)
        model.acceptNext()
        assertEquals(Screen.Message, model.screen)
        assertTrue(model.draft!!.text.contains("coming at 5"))

        val again = family(2)
        again.understood("Call Pankaj, and if he doesn't pick up, message him that I'm coming at 5", typed = true)
        handoff(again, AppCategory.DIALER)
        again.answerReturnCheck(true)
        assertNull("he picked up: the message is not needed", again.pendingStep())
        assertTrue(EventType.STEP_SKIPPED in types(again))
        assertTrue(again.spoken.last().contains("All done"))
    }

    @Config(qualifiers = "+mr")
    @Test fun skipAndCancelAreOneTapInMarathi() {
        val model = family(4, "mr")
        model.understood("पंकजला फोन लाव आणि प्रियाला फोन लाव आणि आईला फोन लाव", typed = true)
        assertEquals(3, model.saved.plan?.steps?.size)
        handoff(model, AppCategory.DIALER)
        model.answerReturnCheck(true)
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithText("पुढचं (२/३)").assertIsDisplayed()
        assertTrue(model.spoken.last(), model.spoken.last().startsWith("पुढचं (२/३): प्रिया ला फोन"))
        compose.onNodeWithText("वगळा").performClick()
        compose.onNodeWithText("पुढचं (३/३)").assertIsDisplayed()
        compose.onNodeWithText("✕  सगळं रद्द करा").performClick()
        assertNull(model.pendingStep())
        compose.onAllNodesWithText("पुढचं (३/३)").assertCountEquals(0)
        val skipped = model.events.all().filter { it.type == EventType.STEP_SKIPPED }
        assertEquals(listOf(true, false), skipped.map { it.ok })
        assertEquals(listOf(2, 3), skipped.map { it.step })
    }

    @Test fun thePlanSurvivesProcessDeathAndExpiresAfterHalfAnHour() {
        val model = family(4)
        model.understood("Call Pankaj and then call Priya", typed = true)
        idleMain()
        val plan = model.saved.plan!!
        assertEquals(plan, decode(encode(model.saved)).plan)
        // The plan is part of the saved state, so a new process reads it back with everything else. (Robolectric
        // keeps one DataStore per JVM fork across tests, so the round trip is checked on the saved JSON itself.)
        val reborn = decode(encode(model.saved))
        assertEquals("call Priya", reborn.plan?.pending?.text)
        // 30 minutes later the plan is gone, not nagging.
        val old = PlanQueue(plan.steps, plan.next, touchedAt = model.nowMs - 31 * 60_000L)
        assertTrue(old.expired(model.nowMs))
        model.dayOffset += 1
        assertNull(model.pendingStep())
        assertNull(decode(encode(model.saved).dropLast(1) + ",\"plan\":{\"steps\":[],\"next\":0}}").plan)
    }

    @Test fun aNewRequestReplacesAWaitingPlan() {
        val model = family(4)
        model.understood("Call Pankaj and then call Priya", typed = true)
        model.screen = Screen.Home
        model.understood("Call Aai", typed = true)
        assertNull(model.saved.plan)
        assertEquals(false, model.events.all().last { it.type == EventType.STEP_SKIPPED }.ok)
    }

    // ---- Guard ----

    @Test fun ho09AtmPinIsBlockedWithAWayToAskTheChild() {
        fakeInstall("com.whatsapp")
        val model = family(2)
        model.understood("Message Pankaj my ATM PIN 4590 so he can withdraw cash", typed = true)
        assertEquals(Screen.Blocked(GuardReason.SECRET_CODE), model.screen)
        assertNull("nothing is drafted", model.draft)
        assertTrue(model.spoken.last(), model.spoken.last().contains("PIN or OTP"))
        val blocked = model.events.all().single { it.type == EventType.GUARD_BLOCKED }
        assertEquals(GuardReason.SECRET_CODE, blocked.guard)
        assertFalse(blocked.toJson().contains("4590"))
        compose.setParent { BlockedScreen(model, GuardReason.SECRET_CODE) }
        compose.onNodeWithText("Nexus won't send this", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("guard_ask").assertIsDisplayed().performClick()
        assertEquals("Pankaj", (model.screen as Screen.Calling).person.name)
        assertNull(nextStartedIntent())
    }

    @Config(qualifiers = "+mr")
    @Test fun theAskButtonSaysAskPankajInMarathi() {
        val model = family(2, "mr")
        model.understood("पंकजला सांग माझा पिन 4590 आहे", typed = true)
        assertEquals(Screen.Blocked(GuardReason.SECRET_CODE), model.screen)
        compose.setParent { BlockedScreen(model, GuardReason.SECRET_CODE) }
        compose.onNodeWithText("📞  Pankaj ला विचारा").assertIsDisplayed()
        compose.onNodeWithText("ठीक आहे, पाठवू नका").performClick()
        assertEquals(Screen.Home, model.screen)
    }

    @Test fun anEditedMessageIsCheckedAgainAtSend() {
        fakeInstall("com.whatsapp")
        val model = family(2)
        model.understood("tell Pankaj I reached", typed = true)
        assertEquals(Screen.Message, model.screen)
        model.setDraftText("my OTP is 482113")
        assertFalse(model.sendDraft(Channel.WHATSAPP))
        assertEquals(Screen.Blocked(GuardReason.SECRET_CODE), model.screen)
        assertNull(nextStartedIntent())
    }

    @Test fun aNameThatIsNotSureIsConfirmedBeforeSend() {
        fakeInstall("com.whatsapp")
        val model = family(2)
        val pankaj = model.saved.people.first()
        model.openDraft(Dictation(pankaj, null, Channel.ANY, "I reached.", "I reached", sure = false))
        compose.setParent { MessageScreen(model) }
        compose.onNodeWithText("This message will go to Pankaj. Is that right?").assertIsDisplayed()
        compose.onAllNodesWithText("Send on WhatsApp", substring = true).assertCountEquals(0)
        assertFalse(model.sendDraft(Channel.WHATSAPP))
        compose.onNodeWithText("✓  Yes, Pankaj").performClick()
        compose.onAllNodesWithText("This message will go to Pankaj. Is that right?").assertCountEquals(0)
        assertTrue(model.sendDraft(Channel.WHATSAPP))
        assertEquals(GuardReason.NAME_UNSURE, model.events.all().single { it.type == EventType.GUARD_BLOCKED }.guard)
    }

    @Test fun adv02CallSunilAsksWhichSunil() {
        val model = family(1)
        model.savePerson(null, "Sunil Patil", "+91 90000 00005", "other", false, "")
        model.savePerson(null, "Sunil Kale", "+91 90000 00006", "other", false, "")
        model.understood("Call Sunil", typed = true)
        val choose = model.screen as Screen.ChooseContact
        assertEquals(setOf("Sunil Patil", "Sunil Kale"), choose.options.map { it.name }.toSet())
        assertEquals(GuardReason.NAME_UNSURE, model.events.all().single { it.type == EventType.GUARD_BLOCKED }.guard)
    }

    @Test fun multiStepAndGuardEventsCarryNoWords() {
        val model = family(4)
        model.understood("Call Pankaj, and if he doesn't pick up, message him that I'm coming at 5", typed = true)
        handoff(model, AppCategory.DIALER)
        model.answerReturnCheck(true)
        model.screen = Screen.Home
        model.understood("Message Pankaj my ATM PIN 4590", typed = true)
        val lines = model.events.all().map { it.toJson() }
        lines.forEach { line -> listOf("Pankaj", "coming", "4590", "PIN").forEach { assertFalse(line, line.contains(it)) } }
        val day = java.time.Instant.ofEpochMilli(model.events.all().last().t).atZone(ZoneId.systemDefault()).toLocalDate()
        val counts = dailyCounts(model.events.all(), day)
        assertEquals(1, counts["multi_step_started"])
        assertEquals(1, counts["multi_step_2"])
        assertEquals(1, counts["step_done"])
        assertEquals(1, counts["step_skipped"])
        assertEquals(1, counts["guard_secret_code"])
        val report = computeMetrics(model.events.all(), model.nowMs)
        assertEquals(1, report.multiStepStarted)
        assertEquals(1, report.guardBlocked[GuardReason.SECRET_CODE])
        assertNotNull(model.summary())
        assertTrue(model.summary().contains("\"guard_blocked\""))
        assertFalse(model.summary().contains("4590"))
    }

    @Test fun locationIsNeverAddedAndThePlanSaysSo() {
        val model = family(4)
        model.understood("Call Pankaj and share my location and then call Priya", typed = true)
        assertEquals(2, model.saved.plan?.steps?.size)
        assertTrue(model.spoken.any { it.contains("I don't send your location") })
    }

    @Test fun singleCommandsBehaveAsBefore() {
        val model = family(2)
        model.understood("Call Pankaj", typed = true)
        assertTrue(model.screen is Screen.Calling)
        assertNull(model.saved.plan)
        assertFalse(EventType.MULTI_STEP_STARTED in types(model))
        model.screen = Screen.Home
        handoff(model, AppCategory.DIALER)
        model.answerReturnCheck(false)
        assertEquals("no plan: a failed call still opens Help", Screen.Help, model.screen)
    }
}
