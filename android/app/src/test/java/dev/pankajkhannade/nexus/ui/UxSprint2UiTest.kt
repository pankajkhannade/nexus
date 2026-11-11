package dev.pankajkhannade.nexus.ui

import android.Manifest
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.pankajkhannade.nexus.AppCategory
import dev.pankajkhannade.nexus.CallingScreen
import dev.pankajkhannade.nexus.ConfirmScreen
import dev.pankajkhannade.nexus.EventType
import dev.pankajkhannade.nexus.HelpScreen
import dev.pankajkhannade.nexus.ListeningScreen
import dev.pankajkhannade.nexus.MessageScreen
import dev.pankajkhannade.nexus.MoneyGoneScreen
import dev.pankajkhannade.nexus.Need
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.NotUnderstoodScreen
import dev.pankajkhannade.nexus.ParentHome
import dev.pankajkhannade.nexus.Person
import dev.pankajkhannade.nexus.SafetyScreen
import dev.pankajkhannade.nexus.Screen
import dev.pankajkhannade.nexus.SettingsScreen
import dev.pankajkhannade.nexus.SetupFlow
import dev.pankajkhannade.nexus.SetupPage
import dev.pankajkhannade.nexus.UiMode
import dev.pankajkhannade.nexus.dailyCounts
import dev.pankajkhannade.nexus.computeMetrics
import dev.pankajkhannade.nexus.initialOf
import dev.pankajkhannade.nexus.setupPages
import dev.pankajkhannade.nexus.shownIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * UX sprint 2 acceptance checks on a small phone (360x640dp). "Displayed" means on screen without scrolling.
 * Sections follow the sprint items: countdown, button names, honest setup, help and "did it work?",
 * the bottom bar, पैसे गेले, 2.0x text, Devanagari names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class UxSprint2UiTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetFont() = RuntimeEnvironment.setFontScale(1f)

    private fun child(model: NexusViewModel) = compose.setContent { NexusTheme(mode = UiMode.Child) { SetupFlow(model) } }

    // ---- 1. Countdown ----
    @Test fun theCountdownChoiceIsSavedAndSurvivesARestart() {
        val model = newModel(people = 1)
        compose.setContent { NexusTheme(mode = UiMode.Child) { SettingsScreen(model) } }
        compose.onNodeWithText("✓ 5 s").performScrollTo().assertExists()
        compose.onNodeWithText("8 s").performScrollTo().performClick()
        assertEquals(8, model.saved.countdownSeconds)
        compose.onNodeWithText("✓ 8 s").assertExists()
        // Saved with the rest of the state, so a restart reads it back (and old saves default to 5).
        val raw = dev.pankajkhannade.nexus.encode(model.saved)
        assertEquals(8, dev.pankajkhannade.nexus.decode(raw).countdownSeconds)
        assertEquals(5, dev.pankajkhannade.nexus.decode(raw.replace("\"countdown\":8", "\"x\":0")).countdownSeconds)
        assertEquals(5, dev.pankajkhannade.nexus.decode(raw.replace("\"countdown\":8", "\"countdown\":4")).countdownSeconds)
        model.setCountdown(5)
    }

    // ---- 2. Clear names: झालं / रद्द करा ----
    @Config(qualifiers = "+mr")
    @Test fun listeningSaysDoneAndCancelInMarathiInTheBottomBar() {
        val model = newModel(people = 1)
        compose.setParent { ListeningScreen(model) }
        compose.onNodeWithText("✓  झालं").assertIsDisplayed()
        compose.onNodeWithText("✕  रद्द करा").assertIsDisplayed()
        compose.onAllNodesWithText("थांबा").assertCountEquals(0)
        compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
    }

    @Config(qualifiers = "+hi")
    @Test fun theCallCountdownSaysCancelInHindi() {
        val model = newModel(people = 1)
        model.startCall(model.saved.people[0])
        compose.mainClock.autoAdvance = false
        compose.setParent { (model.screen as? Screen.Calling)?.let { CallingScreen(model, it.person) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("✕  रद्द करें").assertIsDisplayed().performClick()
        assertEquals(Screen.Home, model.screen)
        compose.mainClock.advanceTimeBy(6_000)
        assertNull(nextStartedIntent())
    }

    @Config(qualifiers = "+mr")
    @Test fun reDictatingAMessageSaysDoneAndCancel() {
        val model = newModel(people = 4)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val fake = dev.pankajkhannade.nexus.FakeSpeechEngine()
        model.useSpeechEngine(fake) { fake.clock }
        model.understood("पंकजला सांग की मी घरी आहे", typed = true)
        compose.setParent { MessageScreen(model) }
        compose.onNodeWithText("🎤  पुन्हा बोला").performClick()
        compose.onNodeWithText("✓  झालं").assertIsDisplayed()
        compose.onNodeWithText("✕  रद्द करा").assertIsDisplayed().performClick()
        assertFalse(model.dictating)
        assertEquals(Screen.Message, model.screen)
    }

    @Test fun settingsSaysMoveUpInWordsAndKeepsRemoveAwayFromEdit() {
        val model = newModel(people = 2)
        compose.setContent { NexusTheme(mode = UiMode.Child) { SettingsScreen(model) } }
        compose.onAllNodesWithText("↑").assertCountEquals(0)
        compose.onNodeWithText("↑ Move up").performScrollTo().assertIsDisplayed()
        val edit = compose.onAllNodesWithText("Edit")[0].fetchSemanticsNode().boundsInRoot
        val remove = compose.onNodeWithText("🗑  Remove Pankaj").fetchSemanticsNode().boundsInRoot
        assertTrue("Remove is on its own row, below Edit", remove.top >= edit.bottom + 8f)
    }

    // ---- 3. Honest, shorter setup ----
    @Test fun setupCountsOnlyRealStepsAndHasNoNearHomePage() {
        assertEquals(
            listOf(SetupPage.LANGUAGE, SetupPage.FAMILY, SetupPage.HOME, SetupPage.MIC, SetupPage.CALL, SetupPage.HANDOVER),
            setupPages(micGranted = false, callGranted = false),
        )
        assertEquals(listOf(SetupPage.LANGUAGE, SetupPage.FAMILY, SetupPage.HOME, SetupPage.HANDOVER), setupPages(micGranted = true, callGranted = true))
        val model = newModel(people = 0)
        model.setupStep = 0
        child(model)
        compose.onNodeWithText("Step 1 of 5").assertIsDisplayed()
    }

    @Test fun aPermissionAlreadyOnIsNotCountedAsAStep() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE)
        val model = NexusViewModel(app).also { m -> while (!m.loaded) { idleMain(); Thread.sleep(2) } }
        model.setupStep = 0
        child(model)
        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
    }

    @Test fun handoverIsAllSetNotAnotherStep() {
        val model = newModel(people = 1)
        model.setupPeople = model.saved.people
        model.planSetup(micGranted = true, callGranted = true)
        model.setupStep = model.setupPlan.indexOf(SetupPage.HANDOVER)
        child(model)
        compose.onNodeWithText("All set").assertIsDisplayed()
        compose.onAllNodesWithText("Step", substring = true).assertCountEquals(0)
    }

    @Test fun thePinIsReadFromTheAddressAndNotAskedAgain() {
        val model = newModel(people = 1)
        model.lookupHome = { Pair(dev.pankajkhannade.nexus.Place(it), null) }
        model.planSetup(micGranted = true, callGranted = true)
        model.setupStep = 2
        model.setupHome = "Baner Road, Pune 411045"
        child(model)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(3_000) { model.setupPincode == "411045" }
        compose.onNodeWithText("PIN code: 411045").assertExists()
        compose.onAllNodesWithText("What is the home PIN code?").assertCountEquals(0)
    }

    @Test fun withoutAPinInTheAddressItIsOneQuestionPrefilledByTheGeocoder() {
        val model = newModel(people = 1)
        model.setupPincode = ""
        model.lookupHome = { Pair(dev.pankajkhannade.nexus.Place(it, 18.56, 73.81), "411007") }
        model.planSetup(micGranted = true, callGranted = true)
        model.setupStep = 2
        model.setupHome = "Aundh, Pune"
        child(model)
        compose.waitUntil(3_000) { model.setupPincode == "411007" }
        compose.onNodeWithText("What is the home PIN code?").assertExists()
        compose.onNodeWithTag("pincode").assertExists()
    }

    @Test fun setupPicksTheInstalledNearHomeAppsByItself() {
        fakeInstall("com.grofers.customerapp")
        val model = newModel(people = 1)
        model.setupServices = emptyMap()
        model.setupPeople = model.saved.people
        model.finishSetup(dev.pankajkhannade.nexus.COUNTRIES.first { it.code == "IN" })
        assertEquals("com.grofers.customerapp", model.saved.services[Need.GROCERY.key])
    }

    // ---- 4. Help on home, and "did it work?" ----
    @Test fun helpIsOnHomeWithFourPeopleAndEverythingStillFits() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithText("Help: ask Pankaj").assertIsDisplayed()
        FAMILY.forEach { (name, _, _) -> compose.onNodeWithContentDescription("Call $name").assertIsDisplayed() }
        compose.onNodeWithTag("speak_bar").assertIsDisplayed()
        compose.onNodeWithTag("home_help").performClick()
        assertEquals(Screen.Help, model.screen)
        assertTrue(EventType.HELP_OPENED in model.events.all().map { it.type })
    }

    @Test fun helpCallsOrMessagesTheChild() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 2)
        compose.setParent { HelpScreen(model) }
        compose.onNodeWithText("📞  Call Pankaj").assertIsDisplayed()
        compose.onNodeWithText("💬  Message Pankaj").performClick()
        val intent = nextStartedIntent()!!
        assertEquals("com.whatsapp", intent.`package`)
        assertTrue(intent.dataString!!, intent.dataString!!.contains("need%20help") || intent.dataString!!.contains("need+help"))
        compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
    }

    @Test fun afterACallHomeAsksDidItWorkAndLogsOnlyYesOrNo() {
        val model = newModel(people = 1)
        setUpDaysAgo(model, 0)
        model.cameBack()
        model.actionOpened(AppCategory.DIALER)
        model.cameBack()
        assertEquals(AppCategory.DIALER, model.returnCheck)
        assertEquals("Did the call go through?", model.spoken.last())
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithText("Did the call go through?").assertIsDisplayed()
        compose.onNodeWithText("✓  Yes, done").performClick()
        assertNull(model.returnCheck)
        val e = model.events.all().single { it.type == EventType.RETURN_CHECK }
        assertEquals(true, e.ok)
        assertEquals(AppCategory.DIALER, e.category)
        // Nothing but enums, a boolean and the time.
        assertEquals(listOf(e.t, EventType.RETURN_CHECK, AppCategory.DIALER, true), listOfNotNull(e.t, e.type, e.category, e.ok, e.lang, e.command, e.ms, e.chars))
        val counts = dailyCounts(model.events.all(), java.time.Instant.ofEpochMilli(e.t).atZone(ZoneId.systemDefault()).toLocalDate())
        assertEquals(1, counts["did_it_work_yes"])
        assertEquals(1, counts["did_it_work_yes_dialer"])
        assertEquals(1, computeMetrics(model.events.all(), model.nowMs).didItWork.num)
    }

    @Test fun noAfterARideOpensHelp() {
        val model = newModel(people = 1)
        setUpDaysAgo(model, 0)
        model.cameBack()
        model.actionOpened(AppCategory.CAB)
        model.cameBack()
        compose.setParent { if (model.screen == Screen.Help) HelpScreen(model) else ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithText("Did the ride get booked?").assertIsDisplayed()
        compose.onNodeWithText("✕  No").performClick()
        assertEquals(Screen.Help, model.screen)
        assertEquals(false, model.events.all().single { it.type == EventType.RETURN_CHECK }.ok)
        compose.onNodeWithText("📞  Call Pankaj").assertIsDisplayed()
    }

    @Test fun aPlayStoreTripGetsNoQuestion() {
        val model = newModel(people = 1)
        setUpDaysAgo(model, 0)
        model.cameBack()
        model.actionOpened(AppCategory.PLAY_STORE)
        model.cameBack()
        assertNull(model.returnCheck)
    }

    // ---- 5. The bottom bar on the remaining parent screens ----
    @Test fun confirmCallingListeningMessageHelpAndMoneyGoneAllHaveTheBar() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 2)
        val p = model.saved.people[0]
        var which by androidx.compose.runtime.mutableStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setParent {
            when (which) {
                0 -> ConfirmScreen(model, p)
                1 -> CallingScreen(model, p)
                2 -> ListeningScreen(model)
                3 -> MessageScreen(model)
                4 -> HelpScreen(model)
                else -> MoneyGoneScreen(model)
            }
        }
        model.understood("Pankaj la sang ki mi ghari aahe", typed = true)
        for (i in 0..5) {
            which = i
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
        }
    }

    // ---- 6. पैसे गेले ----
    @Config(qualifiers = "+mr")
    @Test fun moneyGoneShowsFourCalmStepsInOrderWithOneTapEach() {
        fakeInstall("com.whatsapp")
        val model = newModel(people = 1)
        model.openCheck("तुमचे बँक खाते बंद होईल. OTP सांगा.")
        compose.setParent { if (model.screen == Screen.MoneyGone) MoneyGoneScreen(model) else SafetyScreen(model) }
        compose.onNodeWithTag("money_gone").performScrollTo().performClick()
        assertEquals(Screen.MoneyGone, model.screen)
        assertEquals("घाबरू नका. आधी 1930 ला फोन करा. मग बँकेला.", model.spoken.last())
        compose.onNodeWithText("पैसे गेले? आत्ता हे करा").assertIsDisplayed()
        val tops = (1..4).map { compose.onNodeWithTag("money_step_$it").fetchSemanticsNode().positionInRoot.y }
        assertEquals("steps are in order", tops.sorted(), tops)
        // 1930: the dial pad, never an automatic call.
        compose.onNodeWithTag("money_1930").assertIsDisplayed().performClick()
        nextStartedIntent()!!.let { assertEquals(Intent.ACTION_DIAL, it.action); assertEquals("tel:1930", it.dataString) }
        // cybercrime.gov.in in the browser.
        compose.onNodeWithText("🌐  cybercrime.gov.in उघडा").performScrollTo().performClick()
        nextStartedIntent()!!.let { assertEquals(Intent.ACTION_VIEW, it.action); assertEquals("https://cybercrime.gov.in", it.dataString) }
        // Tell the child: WhatsApp with the words typed in (the parent presses Send).
        compose.onNodeWithText("💬  Pankaj ला मेसेज करा").performScrollTo().performClick()
        assertEquals("com.whatsapp", nextStartedIntent()!!.`package`)
        compose.onNodeWithText("आता हे करू नका").performScrollTo().assertIsDisplayed()
        // Back returns to the verdict it came from.
        compose.onNodeWithTag("bar_back").performClick()
        assertEquals(Screen.Safety, model.screen)
        assertTrue(EventType.MONEY_GONE in model.events.all().map { it.type })
    }

    @Test fun moneyGoneIsReachableWithoutAMessageToCheck() {
        val model = newModel(people = 1)
        compose.setParent { if (model.screen == Screen.MoneyGone) MoneyGoneScreen(model) else SafetyScreen(model) }
        compose.onNodeWithText("Money already gone?", substring = true).performScrollTo().performClick()
        assertEquals(Screen.MoneyGone, model.screen)
        compose.onNodeWithTag("bar_back").performClick()
        assertEquals(Screen.Home, model.screen)
    }

    @Test fun theAppAsksForNoRiskyPermissions() {
        val requested = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE).forEach {
            assertFalse("$it must not be requested", it in requested)
        }
    }

    // ---- 7. 2.0x text ----
    @Config(qualifiers = "+mr")
    @Test fun homeAtTwiceTheTextKeepsHelpSpeakAndTheFirstPersonOnScreen() {
        RuntimeEnvironment.setFontScale(2f)
        fakeInstall("com.whatsapp")
        val model = newModel(people = 4, home = "Shivaji Nagar, Pune")
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithTag("speak_bar").assertIsDisplayed()
        compose.onNodeWithTag("home_help").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pankaj ला फोन करा", substring = true).assertIsDisplayed()
        compose.assertNoClippedText()
    }

    @Config(qualifiers = "+mr")
    @Test fun notUnderstoodAtTwiceTheText() {
        RuntimeEnvironment.setFontScale(2f)
        val model = newModel(people = 4)
        model.understood("प्रिया आज पाऊस खूप आहे", typed = true)
        compose.setParent { NotUnderstoodScreen(model) {} }
        compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
        compose.onNodeWithTag("heard_line").assertIsDisplayed()
        compose.onNodeWithText("🎤  पुन्हा बोला").performScrollTo().assertIsDisplayed()
        compose.assertNoClippedText()
    }

    @Config(qualifiers = "+mr")
    @Test fun scamWarningAtTwiceTheTextKeepsAskOnScreen() {
        RuntimeEnvironment.setFontScale(2f)
        val model = newModel(people = 1)
        model.openCheck("तुमचे बँक खाते बंद होईल. OTP सांगा.")
        compose.setParent { SafetyScreen(model) }
        compose.onNodeWithTag("safe_action").assertIsDisplayed()
        compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
        compose.assertNoClippedText()
    }

    @Config(qualifiers = "+hi")
    @Test fun scamWarningAtTwiceTheTextInHindi() {
        RuntimeEnvironment.setFontScale(2f)
        val model = newModel(people = 1)
        model.openCheck("आपका बैंक खाता बंद हो जाएगा. OTP बताइए.")
        compose.setParent { SafetyScreen(model) }
        compose.onNodeWithTag("safe_action").assertIsDisplayed()
        compose.assertNoClippedText()
    }

    @Test fun everySetupPageAtTwiceTheTextHasNoClippedWordsAndAReachableButton() {
        RuntimeEnvironment.setFontScale(2f)
        val model = newModel(people = 1)
        model.setupPeople = model.saved.people
        model.setupHome = "Shivaji Nagar, Pune 411005"
        model.planSetup(micGranted = false, callGranted = false)
        child(model)
        val buttons = listOf("Continue", "Continue", "Continue", "Allow", "Allow", "Hand over")
        buttons.forEachIndexed { i, label ->
            model.setupStep = i
            compose.waitForIdle()
            compose.onNodeWithTag("setup_step").assertIsDisplayed()
            compose.onAllNodesWithText(label)[0].performScrollTo().assertIsDisplayed()
            compose.assertNoClippedText()
        }
    }

    @Config(qualifiers = "+mr")
    @Test fun marathiSetupAtTwiceTheText() {
        RuntimeEnvironment.setFontScale(2f)
        val model = newModel(people = 1)
        model.setupPeople = model.saved.people
        model.planSetup(micGranted = true, callGranted = true)
        model.setupStep = 1
        child(model)
        compose.onNodeWithText("पुढे चला").performScrollTo().assertIsDisplayed()
        compose.assertNoClippedText()
    }

    // ---- 8. Devanagari names ----
    @Test fun aDevanagariNameIsShownInMarathiAndHindiOnly() {
        val p = Person(id = "1", name = "Pankaj", phone = "+919876543210", localName = "पंकज")
        assertEquals("पंकज", p.shownIn("mr"))
        assertEquals("पंकज", p.shownIn("hi"))
        assertEquals("Pankaj", p.shownIn("en"))
        // Latin typed into the Devanagari box changes nothing; Latin names are left alone.
        assertEquals("Pankaj", p.copy(localName = "Pankaj K").shownIn("mr"))
        assertEquals("Priya", Person(id = "2", name = "Priya", phone = "").shownIn("mr"))
        assertEquals("श्री", initialOf("श्रीकांत"))
        assertEquals("क्ष", initialOf("क्षमा"))
        assertEquals("P", initialOf("pankaj"))
    }

    @Config(qualifiers = "+mr")
    @Test fun homeAndCallingUseTheDevanagariName() {
        val model = newModel(people = 2)
        val p = model.saved.people[0]
        model.savePerson(p, p.name, p.phone, p.relation, p.askFamily, p.alias, p.photo, localName = "पंकज")
        idleMain()
        compose.setParent { ParentHome(model, onSpeak = {}, onSettings = {}) }
        compose.onNodeWithText("पंकज").assertIsDisplayed()
        compose.onNodeWithText("Priya").assertIsDisplayed() // no Devanagari name: unchanged
        compose.onNodeWithText("मदत: पंकज ला विचारा").assertIsDisplayed()
    }

    @Test fun theDevanagariNameIsUnderstoodByVoice() {
        val model = newModel(people = 2)
        val p = model.saved.people[0]
        model.savePerson(p, p.name, p.phone, p.relation, p.askFamily, p.alias, p.photo, localName = "पंकज")
        idleMain()
        model.understood("पंकज ला फोन लाव", typed = true)
        val s = model.screen
        assertTrue("$s", s is Screen.Calling && s.person.name == "Pankaj")
        // And it is said in Devanagari in the parent's language (Marathi by default).
        assertEquals("पंकज", model.nameOf(model.saved.people[0]))
    }

}
