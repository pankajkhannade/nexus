package dev.pankajkhannade.nexus

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

const val EXTRA_DOSE_ID = "nexus_dose_id"
private const val DOSE_CHANNEL = "doses"

/**
 * True when the phone lets Nexus ring at an exact time. Android 12+ needs "Alarms & reminders" for that
 * (SCHEDULE_EXACT_ALARM). Android 12 and 13 grant it at install; Android 14+ starts with it off.
 * Without it, setAlarmClock throws and no medicine would ever ring (QA 2026-11-25).
 */
fun canRingDoses(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
    return alarms.canScheduleExactAlarms()
}

/** Android 13+ asks before an app may show notifications; the medicine alarm is one. */
fun canNotifyDoses(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * What still blocks a medicine alarm on this phone, in the order the child fixes them, or null when it can ring.
 * NOTIFY is a runtime permission (a dialog); EXACT is a system page.
 */
enum class DoseBlock { NOTIFY, EXACT }

fun doseBlock(context: Context): DoseBlock? = when {
    !canNotifyDoses(context) -> DoseBlock.NOTIFY
    !canRingDoses(context) -> DoseBlock.EXACT
    else -> null
}

/** The system page where the child turns on "Alarms & reminders" for Nexus (Android 12+). */
fun allowDoseAlarmsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * Rings at the medicine time, even if the app is closed. The alarm goes to [DoseRingReceiver], which posts a
 * full-screen notification that opens the medicine card. (QA 2026-11-25: an alarm that opened the activity
 * directly is a background activity start, which Android 10+ blocks, so nothing appeared.)
 * Idempotent: every call cancels and sets the next ring of each dose, so it is safe on every app start,
 * after each ring (an alarm clock rings once), after a reboot, an update or a clock change.
 * Returns false when the phone does not allow exact alarms (nothing is set; the home card still asks).
 */
fun rescheduleDoses(context: Context, doses: List<Dose>, now: ZonedDateTime = ZonedDateTime.now(), alsoCancel: List<Dose> = emptyList()): Boolean {
    val app = context.applicationContext
    return runCatching {
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return false
        (alsoCancel + doses).distinctBy { it.id }.forEach { alarms.cancel(ringIntent(app, it.id)) }
        if (doses.isEmpty()) return true
        if (!canRingDoses(app)) return false
        doses.forEach { dose ->
            val whenMs = nextDoseAt(dose.hour, dose.minute, now).toInstant().toEpochMilli()
            // The clock icon in the status bar opens the medicine card too.
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, cardIntent(app, dose.id)), ringIntent(app, dose.id))
        }
        true
    }.getOrDefault(false)
}

private const val IMMUTABLE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

/** Opens the medicine card for [id] (notification tap, full-screen intent, status-bar alarm icon). */
private fun cardIntent(context: Context, id: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_DOSE_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(context, id.hashCode(), intent, IMMUTABLE)
}

private fun ringIntent(context: Context, id: String): PendingIntent {
    val intent = Intent(context, DoseRingReceiver::class.java).putExtra(EXTRA_DOSE_ID, id)
    return PendingIntent.getBroadcast(context, id.hashCode(), intent, IMMUTABLE)
}

/** The notification id for a dose, so the medicine card can take it down once it is open. */
fun doseNotificationId(id: String): Int = id.hashCode()

/** Takes down the alarm notification once the parent is looking at the card. */
fun clearDoseNotification(context: Context, id: String) =
    NotificationManagerCompat.from(context).cancel(doseNotificationId(id))

/**
 * Posts the medicine alarm: sound, heads-up when the phone is in use, and the medicine card full screen when
 * the phone is asleep or locked. The text is in the parent's language and names the medicine; it stays on the phone.
 */
fun showDoseAlarm(context: Context, dose: Dose, language: String) {
    val app = context.applicationContext
    if (!canNotifyDoses(app)) return
    val text = app.inLanguage(language)
    val manager = app.getSystemService(NotificationManager::class.java) ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(DOSE_CHANNEL, text.getString(R.string.dose_title), NotificationManager.IMPORTANCE_HIGH),
        )
    }
    val card = cardIntent(app, dose.id)
    val notification = NotificationCompat.Builder(app, DOSE_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("${dose.name}  ${doseClock(dose)}")
        .setContentText(text.getString(R.string.dose_ask))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(card)
        .setFullScreenIntent(card, true)
        .setAutoCancel(true)
        .build()
    runCatching { manager.notify(doseNotificationId(dose.id), notification) }
    wakeScreen(app)
}

/** Lights a sleeping screen for the card (some phones show a full-screen alarm without waking). 15 s at most. */
@Suppress("DEPRECATION")
private fun wakeScreen(context: Context) = runCatching {
    val power = context.getSystemService(android.os.PowerManager::class.java) ?: return@runCatching
    if (power.isInteractive) return@runCatching
    power.newWakeLock(
        android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "nexus:dose",
    ).acquire(15_000)
}

/** The medicine time for [id] has come: show the alarm and set tomorrow's ring (an alarm clock rings once). */
fun ringDose(context: Context, id: String, saved: Saved) {
    if (!saved.setupDone) return
    saved.doses.firstOrNull { it.id == id }?.let { showDoseAlarm(context, it, saved.language) }
    rescheduleDoses(context, saved.doses)
}

/** Not exported: only Nexus's own alarm sends it. */
class DoseRingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_DOSE_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ringDose(context, id, NexusStore(context).read())
            } finally {
                pending.finish()
            }
        }
    }
}

/** A medicine alarm may show over the lock screen. Cleared once the parent has answered. */
fun Activity.showDoseOverLock(on: Boolean) {
    if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(on)
        setTurnScreenOn(on)
    }
}

/** Broadcasts after which the medicine alarms are put back from what is saved on the phone. */
val DOSE_RESCHEDULE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED, // alarms are gone after a reboot
    Intent.ACTION_MY_PACKAGE_REPLACED, // a new Nexus APK was installed
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", // the child allowed Alarms & reminders
)

/** After a reboot, an update, a clock change or a permission change, the medicine alarms are set again. */
class DoseBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in DOSE_RESCHEDULE_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val saved = NexusStore(context).read()
                if (saved.setupDone) rescheduleDoses(context, saved.doses)
            } finally {
                pending.finish()
            }
        }
    }
}
