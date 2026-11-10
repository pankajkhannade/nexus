package dev.pankajkhannade.nexus.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.UiMode
import org.robolectric.Shadows.shadowOf

/**
 * Shared helpers for JVM UI tests (Robolectric + Compose). See docs/testing.md.
 *
 * - [newModel] builds the real ViewModel and waits for its DataStore load, then clears people and home,
 *   because Robolectric reuses one DataStore singleton for every test in a JVM fork.
 * - [fakeInstall] makes `isInstalled(pkg)` true without a real app, so intent hand-offs can be asserted.
 * - [nextStartedIntent] is what Nexus asked Android to open (dialer, WhatsApp, Play Store); nothing launches.
 */
val app: Application get() = ApplicationProvider.getApplicationContext()

val FAMILY = listOf(
    Triple("Pankaj", "+91 98765 43210", "son"),
    Triple("Priya", "+91 91234 56789", "daughter"),
    Triple("Aai", "+91 99887 76655", "spouse"),
    Triple("Raju", "+91 70000 11122", "other"),
)

/** Tue 10 Nov 2026: screenshots show a date, so pin it or goldens change every day. */
val FIXED_DAY: Long = java.time.LocalDate.of(2026, 11, 10).toEpochDay()

fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

fun newModel(people: Int = 0, home: String = ""): NexusViewModel {
    val model = NexusViewModel(app)
    val deadline = System.currentTimeMillis() + 10_000
    while (!model.loaded) {
        check(System.currentTimeMillis() < deadline) { "NexusViewModel did not finish loading" }
        idleMain()
        Thread.sleep(2)
    }
    model.saved.people.forEach { model.removePerson(it) }
    FAMILY.take(people).forEach { (name, phone, relation) ->
        check(model.savePerson(null, name, phone, relation, askFamily = relation == "son", alias = "")) { "bad fixture $phone" }
    }
    model.saveHome(home)
    model.notice = ""
    model.dayOffset = FIXED_DAY - java.time.LocalDate.now().toEpochDay()
    idleMain()
    return model
}

fun fakeInstall(pkg: String) {
    val pm = shadowOf(app.packageManager)
    val main = ComponentName(pkg, "$pkg.Main")
    pm.addActivityIfNotPresent(main)
    pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_INFO) })
}

fun nextStartedIntent(): Intent? = shadowOf(app).nextStartedActivity

/** Parent-mode theme around [content], the way NexusApp shows it after setup. */
fun ComposeContentTestRule.setParent(content: @Composable () -> Unit) = setContent {
    NexusTheme(mode = UiMode.Parent) { content() }
}
