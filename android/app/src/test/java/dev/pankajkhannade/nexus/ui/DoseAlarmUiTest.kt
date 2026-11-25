package dev.pankajkhannade.nexus.ui

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pankajkhannade.nexus.DOSE_RESCHEDULE_ACTIONS
import dev.pankajkhannade.nexus.Dose
import dev.pankajkhannade.nexus.DoseBootReceiver
import dev.pankajkhannade.nexus.DoseEditor
import dev.pankajkhannade.nexus.DoseRingReceiver
import dev.pankajkhannade.nexus.ringDose
import dev.pankajkhannade.nexus.showDoseAlarm
import dev.pankajkhannade.nexus.EXTRA_DOSE_ID
import dev.pankajkhannade.nexus.NexusApp
import dev.pankajkhannade.nexus.Screen
import dev.pankajkhannade.nexus.rescheduleDoses
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZonedDateTime

/**
 * QA 2026-11-25, medicine reminders on real phones:
 * - Android 14 starts with exact alarms off, and setAlarmClock then throws: no medicine ever rang.
 * - The alarm opened the activity directly: a background activity start, blocked on Android 10+, so nothing showed.
 * - An alarm clock rings once; tomorrow's ring was only set again when the app process restarted.
 * - Back from the medicine card left Nexus usable over the PIN lock screen.
 */
@RunWith(RobolectricTestRunner::class)
class DoseAlarmUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val bp = Dose("bp", "BP tablet", 21, 0)
    private val alarms: ShadowAlarmManager get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before fun allowExactAlarms() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @After fun clearAlarms() {
        val manager = app.getSystemService(AlarmManager::class.java)
        alarms.scheduledAlarms.toList().forEach { manager.cancel(it.operation!!) }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun readyModel() = newModel(people = 1).also { readyFor(it, "en"); it.setDoses(listOf(bp)) }

    @Test fun withoutExactAlarmsNothingIsSetAndTheChildIsAskedToAllowThem() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(rescheduleDoses(app, listOf(bp)))
        assertTrue(alarms.scheduledAlarms.isEmpty())

        compose.setContent { DoseEditor(listOf(bp)) {} }
        compose.onNodeWithText("Medicine alarms are off on this phone", substring = true).assertExists()
        compose.onNodeWithText("Allow alarms").performClick()
        val opened = shadowOf(compose.activity).nextStartedActivity ?: nextStartedIntent()!!
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, opened.action)
        assertEquals("package:${app.packageName}", opened.dataString)
    }

    @Test fun withExactAlarmsTheNextRingIsSetAndNoWarningShows() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val now = ZonedDateTime.now()
        assertTrue(rescheduleDoses(app, listOf(bp), now))
        val ring = alarms.scheduledAlarms.single()
        assertTrue(ring.triggerAtTime > now.toInstant().toEpochMilli())
        compose.setContent { DoseEditor(listOf(bp)) {} }
        compose.onNodeWithText("Allow alarms").assertDoesNotExist()
    }

    @Test fun theRingPostsAFullScreenAlarmAndSetsTomorrowsRing() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val model = readyModel()
        val manager = app.getSystemService(AlarmManager::class.java)
        val ring = alarms.scheduledAlarms.single()
        // The alarm goes to a receiver, never straight to the activity (Android 10+ blocks that start).
        assertEquals(DoseRingReceiver::class.java.name, shadowOf(ring.operation).savedIntent.component?.className)
        manager.cancel(ring.operation!!)

        val id = shadowOf(ring.operation).savedIntent.getStringExtra(EXTRA_DOSE_ID)!!
        ringDose(app, id, model.saved) // the receiver passes what is saved on the phone
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))

        val alarm = notifications.allNotifications.single()
        assertEquals(bp.id, shadowOf(alarm.fullScreenIntent).savedIntent.getStringExtra(EXTRA_DOSE_ID))
        assertEquals("BP tablet  21:00", alarm.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("tomorrow's ring", 1, alarms.scheduledAlarms.size)
    }

    @Test fun openingTheCardTakesTheAlarmDown() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val model = readyModel()
        showDoseAlarm(app, bp, "en")
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertEquals(1, notifications.allNotifications.size)
        model.noteDoseIntent(bp.id)
        assertEquals(Screen.DoseDue, model.screen)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test fun withoutNotificationsTheChildIsAskedFirst() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        compose.setContent { DoseEditor(listOf(bp)) {} }
        compose.onNodeWithText("Medicine alarms are off on this phone", substring = true).assertExists()
        compose.onNodeWithText("Allow alarms").assertExists()
    }

    @Test fun reopeningNexusSetsTheRingsAgain() {
        val model = readyModel()
        alarms.scheduledAlarms.toList().forEach { app.getSystemService(AlarmManager::class.java).cancel(it.operation!!) }
        model.cameBack()
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test fun leavingTheMedicineCardStopsShowingOverTheLockScreen() {
        val model = readyModel()
        compose.setContent { NexusApp(model) }
        model.noteDoseIntent(bp.id)
        compose.waitForIdle()
        assertTrue(shadowOf(compose.activity).showWhenLocked)

        model.leaveDose() // Back
        compose.waitForIdle()
        assertFalse(shadowOf(compose.activity).showWhenLocked)
        assertFalse(shadowOf(compose.activity).turnScreenOn)
    }

    @Test fun rebootUpdateClockAndPermissionChangesAllReachTheReceiver() {
        DOSE_RESCHEDULE_ACTIONS.forEach { action ->
            val found = app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0)
            assertTrue("$action is not declared", found.any { it.activityInfo.name == DoseBootReceiver::class.java.name })
        }
        assertTrue(Intent.ACTION_MY_PACKAGE_REPLACED in DOSE_RESCHEDULE_ACTIONS)
    }

    @Test fun removingTheLastDoseCancelsItsRing() {
        rescheduleDoses(app, listOf(bp))
        assertEquals(1, alarms.scheduledAlarms.size)
        rescheduleDoses(app, emptyList(), alsoCancel = listOf(bp))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
}
