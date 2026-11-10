package dev.pankajkhannade.nexus

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

/** The three things a parent must be able to say before the child leaves. Ride is skipped when home is unset. */
enum class PracticeTask { CALL, MESSAGE, RIDE }

fun practiceTasks(saved: Saved): List<PracticeTask> = buildList {
    add(PracticeTask.CALL)
    add(PracticeTask.MESSAGE)
    if (saved.home.isSet) add(PracticeTask.RIDE)
}

/** True when what was understood is the task on screen, not a different action. */
fun practicePasses(task: PracticeTask, command: Command): Boolean = when (task) {
    PracticeTask.CALL -> command is Command.Call || (command is Command.FindContact && command.ask == Ask.CALL)
    PracticeTask.MESSAGE ->
        command is Command.Message || command is Command.WhatsApp ||
            (command is Command.FindContact && command.ask == Ask.MESSAGE)
    PracticeTask.RIDE -> command == Command.RideHome
}

/** 0 nothing yet, 1 not heard, 2 heard as a different action, 3 this check passed. */
const val PRACTICE_NONE = 0
const val PRACTICE_MISSED = 1
const val PRACTICE_WRONG = 2
const val PRACTICE_YES = 3

@Composable
fun practicePhrase(task: PracticeTask, name: String): String = when (task) {
    PracticeTask.CALL -> stringResource(R.string.try_phrase, name)
    PracticeTask.MESSAGE -> stringResource(R.string.practice_message_phrase, name)
    PracticeTask.RIDE -> stringResource(R.string.practice_ride_phrase)
}

@Composable
fun PracticeScreen(model: NexusViewModel, onSpeak: () -> Unit) {
    val saved = model.saved
    val tasks = practiceTasks(saved)
    val index = model.practiceIndex
    if (index !in tasks.indices) {
        PracticeResult(model, tasks)
        return
    }
    val task = tasks[index]
    val person = saved.people.firstOrNull { it.askFamily } ?: saved.people.firstOrNull()
    val name = person?.alias?.ifBlank { person.name } ?: person?.name.orEmpty()
    val context = LocalContext.current
    val mic = granted(context, Manifest.permission.RECORD_AUDIO)
    Centered {
        Text(
            stringResource(R.string.practice_title, index + 1, tasks.size),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.practice_ask), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text("“" + practicePhrase(task, name) + "”", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        when (model.practiceHint) {
            PRACTICE_YES -> Text(stringResource(R.string.practice_yes), style = MaterialTheme.typography.titleLarge)
            PRACTICE_MISSED -> Text(stringResource(R.string.practice_missed), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            PRACTICE_WRONG -> Text(stringResource(R.string.practice_wrong), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (!mic) {
            Text(stringResource(R.string.practice_mic_off), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (model.practiceHint == PRACTICE_YES) {
            BigButton(stringResource(if (index == tasks.lastIndex) R.string.practice_see_result else R.string.practice_next), model::nextPractice)
        } else if (mic) {
            MicButton(onSpeak)
        }
        BigButton(stringResource(R.string.practice_skip_check), model::skipPracticeTask, kind = ButtonKind.Quiet)
    }
}

@Composable
private fun PracticeResult(model: NexusViewModel, tasks: List<PracticeTask>) {
    val passed = model.practiceResults
    val worked = tasks.indices.count { passed.getOrElse(it) { false } }
    val callFailed = tasks.indexOf(PracticeTask.CALL).let { it >= 0 && !passed.getOrElse(it) { false } }
    ScreenColumn {
        Text(stringResource(R.string.practice_result_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (callFailed) R.string.practice_result_stay else R.string.practice_result_body, worked, tasks.size),
            style = MaterialTheme.typography.bodyLarge,
        )
        tasks.forEachIndexed { i, task ->
            val ok = passed.getOrElse(i) { false }
            val label = when (task) {
                PracticeTask.CALL -> if (ok) R.string.practice_call_ok else R.string.practice_call_no
                PracticeTask.MESSAGE -> if (ok) R.string.practice_message_ok else R.string.practice_message_no
                PracticeTask.RIDE -> if (ok) R.string.practice_ride_ok else R.string.practice_ride_no
            }
            Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
        }
        if (worked < tasks.size) {
            BigButton(stringResource(R.string.practice_again), model::retryPractice)
        }
        BigButton(
            stringResource(if (callFailed) R.string.practice_leave else R.string.done),
            model::finishPractice,
            kind = if (callFailed) ButtonKind.Secondary else ButtonKind.Primary,
        )
    }
}
