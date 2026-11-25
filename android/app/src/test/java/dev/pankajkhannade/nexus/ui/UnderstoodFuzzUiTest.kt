package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.junit4.createComposeRule
import dev.pankajkhannade.nexus.FuzzPhrases
import dev.pankajkhannade.nexus.Screen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/** QA 2026-11-25: the real ViewModel acts on odd phrases (plans, guard, messages, rides) without throwing. */
@RunWith(RobolectricTestRunner::class)
class UnderstoodFuzzUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun understoodNeverThrows() {
        fakeInstall("com.whatsapp")
        fakeInstall("com.olacabs.customer")
        val rnd = Random(7)
        val failures = mutableListOf<String>()
        for (people in listOf(0, 4)) {
            val model = newModel(people = people, home = if (people > 0) "Kothrud, Pune" else "")
            (FuzzPhrases.fixed + List(300) { FuzzPhrases.phrase(rnd) }).forEach { said ->
                runCatching {
                    model.understood(said, typed = true)
                    idleMain()
                    // Walk the next step of a plan too, then go home for the next phrase.
                    model.pendingStep()?.let { model.stepLabel(it) }
                    model.screen = Screen.Home
                }.onFailure { failures += "people=$people ${it::class.simpleName}: ${it.message} <- \"${said.take(120)}\"\n${it.stackTrace.take(4).joinToString("\n")}" }
                while (nextStartedIntent() != null) Unit
            }
        }
        assertTrue(failures.distinct().take(10).joinToString("\n\n"), failures.isEmpty())
    }
}
