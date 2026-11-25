package dev.pankajkhannade.nexus

import android.Manifest
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val QUICK_TIMES = listOf(8 to 0, 14 to 0, 21 to 0)

/** The child sets medicine times. Optional. The same editor is used in setup and in Family settings. */
@Composable
fun DoseEditor(doses: List<Dose>, onChange: (List<Dose>) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(21) }
    var minute by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    val short = stringResource(R.string.dose_name_short)
    val full = stringResource(R.string.dose_full)
    // Re-read on every resume, so the warning goes away when the child comes back from the system page.
    var asked by remember { mutableIntStateOf(0) }
    val resumed = rememberResumeTick()
    val block = remember(resumed, asked, doses.isEmpty()) { doseBlock(context) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked += 1 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dose_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.dose_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (doses.isNotEmpty() && block != null) {
            Text(stringResource(R.string.dose_alarms_off), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            BigButton(stringResource(R.string.dose_alarms_allow), {
                when (block) {
                    DoseBlock.NOTIFY -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    DoseBlock.EXACT -> runCatching { context.startActivity(allowDoseAlarmsIntent(context)) }
                }
            }, kind = ButtonKind.Primary)
        }
        if (doses.isEmpty()) {
            Text(stringResource(R.string.dose_none), style = MaterialTheme.typography.bodyLarge)
        }
        doses.forEach { dose ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${dose.name}  ${doseClock(dose)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                BigButton(stringResource(R.string.dose_remove), { onChange(doses.filterNot { it.id == dose.id }) }, Modifier.weight(1f), kind = ButtonKind.Quiet)
            }
        }
        if (doses.size < MAX_DOSES) {
            OutlinedTextField(
                name, { name = it; problem = null }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.dose_name)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                QUICK_TIMES.forEach { (h, m) ->
                    val on = hour == h && minute == m
                    BigButton((if (on) "✓ " else "") + "%02d:%02d".format(h, m), { hour = h; minute = m }, Modifier.weight(1f),
                        kind = if (on) ButtonKind.Primary else ButtonKind.Secondary)
                }
            }
            BigButton(stringResource(R.string.dose_pick_time), {
                TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
            }, kind = ButtonKind.Secondary)
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BigButton(stringResource(R.string.dose_add), {
                when {
                    doses.size >= MAX_DOSES -> problem = full
                    cleanDoseName(name) == null -> problem = short
                    else -> {
                        val dose = Dose("d" + System.currentTimeMillis().toString(36), cleanDoseName(name)!!, hour, minute)
                        onChange(doses + dose)
                        name = ""
                        problem = null
                    }
                }
            })
        }
    }
}

/** The screen the alarm opens. Speaks the medicine and asks once. Yes and No are both real answers. */
@Composable
fun DoseDueScreen(model: NexusViewModel) {
    val dose = model.saved.doses.firstOrNull { it.id == model.dueDoseId }
    if (dose == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { model.leaveDose() }
        return
    }
    // Over the lock screen only while this card is up: NexusApp sets it from the screen on every frame.
    androidx.compose.runtime.LaunchedEffect(dose.id) { model.say(model.dosePrompt(dose)) }
    val answer: (Boolean) -> Unit = { taken -> model.answerDose(dose.id, taken) }
    ScreenColumn {
        Text(dose.name, style = MaterialTheme.typography.displaySmall)
        Text(doseClock(dose), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.dose_ask), style = MaterialTheme.typography.headlineSmall)
        BigButton("✓  " + stringResource(R.string.dose_taken), { answer(true) }, kind = ButtonKind.Call)
        BigButton(stringResource(R.string.dose_not_yet), { answer(false) }, kind = ButtonKind.Secondary)
        BigButton(stringResource(R.string.dose_send), { model.sendDoseSummary() }, kind = ButtonKind.Secondary)
    }
}

/** Today's answers, in the parent's language, for the WhatsApp the child receives. */
fun doseSummary(model: NexusViewModel, day: LocalDate = LocalDate.ofEpochDay(model.today)): String {
    val context = model.getApplication<android.app.Application>().inLanguage(model.language)
    fun state(mark: DoseMark?): String = when (mark?.taken) {
        true -> context.getString(R.string.dose_taken)
        false -> context.getString(R.string.dose_not_yet)
        null -> context.getString(R.string.dose_no_answer)
    }
    val lines = doseShareLines(model.saved.doses, model.saved.doseMarks, day, ::state)
    return context.getString(R.string.dose_share, day.toString(), lines.joinToString("\n"))
}
