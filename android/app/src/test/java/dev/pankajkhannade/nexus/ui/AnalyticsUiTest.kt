package dev.pankajkhannade.nexus.ui

import android.speech.SpeechRecognizer
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.AppCategory
import dev.pankajkhannade.nexus.CommandType
import dev.pankajkhannade.nexus.EndReason
import dev.pankajkhannade.nexus.EventType
import dev.pankajkhannade.nexus.FakeSpeechEngine
import dev.pankajkhannade.nexus.MetricsScreen
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.Screen
import dev.pankajkhannade.nexus.Source
import dev.pankajkhannade.nexus.TesterSource
import dev.pankajkhannade.nexus.UiMode
import dev.pankajkhannade.nexus.UploadConsentScreen
import dev.pankajkhannade.nexus.UsageEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The event log wired into real flows, and the How Nexus is doing / consent screens. */
@RunWith(RobolectricTestRunner::class)
class AnalyticsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun types(model: NexusViewModel) = model.events.all().map { it.type }

    private fun showMetrics(model: NexusViewModel) = compose.setContent { NexusTheme(mode = UiMode.Child) { MetricsScreen(model) } }

    @Test fun aVoiceCallLogsStartEndAndCommandWithTiming() {
        val model = newModel(people = 1)
        val fake = FakeSpeechEngine()
        model.useSpeechEngine(fake) { fake.clock }
        model.startListening()
        fake.final("call Pankaj")
        idleMain()
        val end = model.events.all().single { it.type == EventType.VOICE_END }
        assertEquals(EndReason.COMPLETE, end.end)
        assertEquals("call Pankaj".length, end.chars)
        val cmd = model.events.all().single { it.type == EventType.COMMAND }
        assertEquals(CommandType.CALL, cmd.command)
        assertEquals(Source.VOICE, cmd.source)
        assertEquals(true, cmd.ok)
        assertNotNull(cmd.ms)
        assertEquals(listOf(EventType.VOICE_START, EventType.VOICE_END, EventType.COMMAND), types(model))
    }

    @Test fun aCutOffAndItsErrorCodeAreLogged() {
        val model = newModel(people = 1)
        val fake = FakeSpeechEngine()
        model.useSpeechEngine(fake) { fake.clock }
        model.startListening()
        fake.partial("पंकजला सांग")
        fake.error(SpeechRecognizer.ERROR_NETWORK)
        idleMain()
        assertEquals(EndReason.CUT_OFF, model.events.all().single { it.type == EventType.VOICE_END }.end)
        assertEquals(SpeechRecognizer.ERROR_NETWORK, model.events.all().single { it.type == EventType.RECOGNIZER_ERROR }.code)
    }

    @Test fun stopWhileListeningIsACancel() {
        val model = newModel(people = 1)
        val fake = FakeSpeechEngine()
        model.useSpeechEngine(fake) { fake.clock }
        model.startListening()
        model.cancelListening()
        assertEquals(EndReason.CANCELLED, model.events.all().single { it.type == EventType.VOICE_END }.end)
        assertTrue(EventType.CANCEL in types(model))
    }

    @Test fun aQuickReturnAfterAnActionIsLoggedWithTheTimeAway() {
        val model = newModel(people = 1)
        model.cameBack() // the first start is the launch itself
        model.tile(CommandType.CALL)
        model.actionOpened(AppCategory.DIALER)
        model.cameBack()
        val back = model.events.all().single { it.type == EventType.ACTION_RETURN }
        assertEquals(AppCategory.DIALER, back.category)
        assertTrue(back.ms!! < 5_000)
        model.cameBack()
        assertEquals(EventType.FOREGROUND, types(model).last())
        assertEquals(Source.TILE, model.events.all().single { it.type == EventType.ACTION_OPENED }.source)
    }

    @Test fun theScreenShowsCountsNextToPercentages() {
        val model = newModel(people = 1)
        seedDemoUsage(model)
        showMetrics(model)
        compose.onNodeWithText("Understood the first time: 7 of 9 (78%)").assertExists()
        compose.onNodeWithText("Used on 6 of the last 7 days").assertExists()
    }

    @Test fun testerSourceGoesIntoTheSummary() {
        val model = newModel(people = 1)
        showMetrics(model)
        compose.onNodeWithText("Friend").performScrollTo().performClick()
        assertEquals(TesterSource.FRIEND, model.testerSource)
        assertEquals("friend", JSONObject(model.summary()).getString("tester_source"))
    }

    @Test fun turningUploadOnGoesThroughConsent() {
        val model = newModel(people = 1)
        showMetrics(model)
        compose.onNodeWithTag("upload_switch").performScrollTo().assertIsOff().performClick()
        assertEquals(Screen.UploadConsent, model.screen)
        assertFalse(model.uploadOn)
        assertNull(model.analyticsPrefs.installId)
    }

    @Test fun consentYesTurnsUploadOnAndNoKeepsItOff() {
        val model = newModel(people = 1)
        compose.setContent { NexusTheme(mode = UiMode.Child) { UploadConsentScreen(model) } }
        compose.onNodeWithText("No, keep it on the phone").performScrollTo().performClick()
        assertFalse(model.uploadOn)
        compose.onNodeWithText("Yes, send daily counts").performScrollTo().performClick()
        assertTrue(model.uploadOn)
        assertNotNull(model.analyticsPrefs.installId)
        assertEquals(Screen.Metrics, model.screen)
    }

    @Test fun uploadSwitchOffNeedsNoConsentScreen() {
        val model = newModel(people = 1)
        model.setUploadConsent(true)
        showMetrics(model)
        compose.onNodeWithTag("upload_switch").performScrollTo().assertIsOn().performClick()
        assertFalse(model.uploadOn)
        assertFalse(model.analyticsPrefs.uploadOn)
    }

    @Test fun deleteAllUsageDataWipesEverything() {
        val model = newModel(people = 1)
        seedDemoUsage(model)
        model.record(dev.pankajkhannade.nexus.Event.CALL)
        model.setUploadConsent(true)
        model.chooseTesterSource(TesterSource.FAMILY)
        showMetrics(model)
        compose.onNodeWithText("🗑  Delete all usage data").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertTrue(model.events.all().isEmpty())
        assertFalse(model.uploadOn)
        assertNull(model.analyticsPrefs.installId)
        assertEquals(TesterSource.UNSET, model.testerSource)
        assertEquals(0, model.usage.total(dev.pankajkhannade.nexus.Event.CALL))
        assertEquals(1, model.saved.people.size) // people stay
        compose.onNodeWithText("Understood the first time: none yet").assertExists()
    }
}

/**
 * A tester's first 9 days, pinned to [FIXED_DAY] so screenshots don't change: setup in 4 minutes, 9 first tries
 * (7 understood cleanly), a retry, a cut-off, tiles and a crash.
 */
fun seedDemoUsage(model: NexusViewModel) {
    val day = 86_400_000L
    val base = (FIXED_DAY - 8) * day + 11 * 3_600_000L - 19_800_000L // 11:00 IST, 8 days ago
    var t = base
    fun add(e: UsageEvent) = model.track(e)
    fun at(type: EventType, dt: Long = 1_000, f: (Long) -> UsageEvent = { UsageEvent(it, type) }) { t += dt; add(f(t)) }
    add(UsageEvent(t, EventType.SETUP_START))
    listOf(1, 2, 3, 4, 5).forEach { s -> at(EventType.SETUP_STEP, 40_000) { UsageEvent(it, EventType.SETUP_STEP, step = s) } }
    at(EventType.PERMISSION) { UsageEvent(it, EventType.PERMISSION, perm = dev.pankajkhannade.nexus.Perm.MIC, ok = true) }
    at(EventType.PERMISSION) { UsageEvent(it, EventType.PERMISSION, perm = dev.pankajkhannade.nexus.Perm.CALL, ok = true) }
    at(EventType.SETUP_DONE, 36_000) { UsageEvent(it, EventType.SETUP_DONE, lang = dev.pankajkhannade.nexus.Lang.MR, ms = 240_000) }
    val plan = listOf(
        Triple(0, dev.pankajkhannade.nexus.Lang.MR, CommandType.CALL), Triple(0, dev.pankajkhannade.nexus.Lang.MR, CommandType.MESSAGE),
        Triple(1, dev.pankajkhannade.nexus.Lang.MR, CommandType.CALL), Triple(2, dev.pankajkhannade.nexus.Lang.HI, CommandType.RIDE_HOME),
        Triple(4, dev.pankajkhannade.nexus.Lang.MR, CommandType.CALL), Triple(5, dev.pankajkhannade.nexus.Lang.MR, CommandType.NEAR_HOME),
        Triple(6, dev.pankajkhannade.nexus.Lang.MR, CommandType.CALL), Triple(7, dev.pankajkhannade.nexus.Lang.EN, CommandType.MESSAGE),
    )
    plan.forEachIndexed { i, (d, lang, cmd) ->
        t = base + d * day + 2 * 3_600_000L + i * 600_000L
        add(UsageEvent(t, EventType.APP_OPEN))
        add(UsageEvent(t + 1_000, EventType.VOICE_START, lang = lang))
        val ok = i != 2
        add(UsageEvent(t + 5_000, EventType.VOICE_END, lang = lang, end = if (ok) EndReason.COMPLETE else EndReason.CUT_OFF, ms = 4_000, chars = 24))
        add(UsageEvent(t + 5_100, EventType.COMMAND, lang = lang, command = if (ok) cmd else CommandType.UNKNOWN, source = Source.VOICE, ok = ok, ms = 4_100))
        if (!ok) {
            // retry 15 s later, understood
            add(UsageEvent(t + 20_000, EventType.VOICE_START, lang = lang))
            add(UsageEvent(t + 24_000, EventType.VOICE_END, lang = lang, end = EndReason.SILENCE, ms = 4_000, chars = 20))
            add(UsageEvent(t + 24_100, EventType.COMMAND, lang = lang, command = cmd, source = Source.VOICE, ok = true, ms = 4_100))
        }
        if (cmd == CommandType.CALL) {
            add(UsageEvent(t + 30_000, EventType.ACTION_OPENED, category = AppCategory.DIALER, source = Source.VOICE))
            add(UsageEvent(t + 200_000, EventType.ACTION_RETURN, category = AppCategory.DIALER, ms = 170_000))
        }
    }
    // One more voice try on day 7 that was understood but opened the wrong app (back in 3 s)
    t = base + 7 * day + 6 * 3_600_000L
    add(UsageEvent(t, EventType.VOICE_START, lang = dev.pankajkhannade.nexus.Lang.MR))
    add(UsageEvent(t + 4_000, EventType.COMMAND, lang = dev.pankajkhannade.nexus.Lang.MR, command = CommandType.WHATSAPP, source = Source.VOICE, ok = true, ms = 4_000))
    add(UsageEvent(t + 5_000, EventType.ACTION_OPENED, category = AppCategory.WHATSAPP, source = Source.VOICE))
    add(UsageEvent(t + 8_000, EventType.ACTION_RETURN, category = AppCategory.WHATSAPP, ms = 3_000))
    // Tiles and a crash
    add(UsageEvent(base + 3 * day, EventType.COMMAND, command = CommandType.CALL, source = Source.TILE, ok = true))
    add(UsageEvent(base + 3 * day + 1, EventType.APP_OPEN))
    add(UsageEvent(base + 6 * day + 99, EventType.COMMAND, command = CommandType.RIDE_HOME, source = Source.TILE, ok = true))
    add(UsageEvent(base + 6 * day + 100, EventType.CRASH))
}
