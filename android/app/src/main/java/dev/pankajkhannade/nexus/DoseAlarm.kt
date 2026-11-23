package dev.pankajkhannade.nexus

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

const val EXTRA_DOSE_ID = "nexus_dose_id"

/** Opens Nexus at the medicine time, even if the app is closed. Uses the alarm-clock API, not a background sync. */
fun rescheduleDoses(context: Context, doses: List<Dose>, now: ZonedDateTime = ZonedDateTime.now(), alsoCancel: List<Dose> = emptyList()) {
    runCatching {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        (alsoCancel + doses).distinctBy { it.id }.forEach { alarms.cancel(doseIntent(app, it.id)) }
        doses.forEach { dose ->
            val whenMs = nextDoseAt(dose.hour, dose.minute, now).toInstant().toEpochMilli()
            val pending = doseIntent(app, dose.id)
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, pending), pending)
        }
    }
}

private fun doseIntent(context: Context, id: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_DOSE_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getActivity(context, id.hashCode(), intent, flags)
}

/** A medicine alarm may show over the lock screen. Cleared once the parent has answered. */
fun Activity.showDoseOverLock(on: Boolean) {
    if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(on)
        setTurnScreenOn(on)
    }
}

/** After a reboot the alarm-clock entries are gone, so they are put back from what is saved on the phone. */
class DoseBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
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
