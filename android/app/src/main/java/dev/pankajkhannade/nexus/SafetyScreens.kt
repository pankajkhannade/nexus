package dev.pankajkhannade.nexus

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** S8 "Is this safe?": paste/type, pick a screenshot, or arrive via Share. Then a verdict with reasons. */
@Composable
fun SafetyScreen(model: NexusViewModel) {
    val verdict = model.verdict
    when {
        model.checking -> Centered {
            CircularProgressIndicator()
            BigCentredText(stringResource(R.string.reading_picture))
        }
        verdict != null -> VerdictView(model, verdict)
        else -> CheckInput(model)
    }
}

@Composable
private fun CheckInput(model: NexusViewModel) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(model.checkText) }
    var noText by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(model::openCheckImage)
    }
    ScreenColumn {
        Text("🛡  " + stringResource(R.string.check_message), style = MaterialTheme.typography.headlineMedium)
        if (model.checkImage != null) {
            // An image was read but no text was found in it.
            Text(stringResource(R.string.no_text_in_picture), style = MaterialTheme.typography.bodyLarge)
        }
        BigButton(stringResource(R.string.paste_message), {
            val clip = clipboardText(context)
            if (clip.isNullOrBlank()) noText = true else model.openCheck(clip)
        })
        if (noText) Text(stringResource(R.string.clipboard_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(stringResource(R.string.or_type_message)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        BigButton(stringResource(R.string.check), { model.openCheck(text) }, enabled = text.isNotBlank(), kind = ButtonKind.Secondary)
        BigButton(stringResource(R.string.pick_screenshot), {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, kind = ButtonKind.Secondary)
        OutlinedPanel {
            Text(stringResource(R.string.share_guide_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.share_guide), style = MaterialTheme.typography.bodyLarge)
        }
        BigButton(stringResource(R.string.back), { model.clearCheck(); model.screen = Screen.Home }, kind = ButtonKind.Quiet)
    }
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

@Composable
private fun VerdictView(model: NexusViewModel, verdict: Verdict) {
    val context = LocalContext.current
    val askers = model.saved.people.filter { it.askFamily }.ifEmpty { model.saved.people }
    var choosing by remember { mutableStateOf(false) }
    var sendingTo by remember { mutableStateOf<Person?>(null) }
    ScreenColumn {
        VerdictBanner(verdict.level)
        verdict.shown.forEach { reason ->
            Text("•  " + stringResource(reasonText(reason)), style = MaterialTheme.typography.bodyLarge)
        }
        if (verdict.level == Level.NONE) {
            Text(stringResource(R.string.verdict_none_body), style = MaterialTheme.typography.bodyLarge)
        }
        if (verdict.showNeverShare) {
            OutlinedPanel(color = NexusColors.DangerContainer) {
                Text(stringResource(R.string.never_share), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (model.checkText.isNotBlank()) {
            OutlinedPanel {
                Text(stringResource(R.string.message_checked), style = MaterialTheme.typography.titleSmall)
                Text(model.checkText.take(400), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val target = sendingTo
        when {
            target != null -> OutlinedPanel {
                Text(stringResource(if (model.checkImage != null) R.string.whatsapp_pick_chat else R.string.whatsapp_will_open, target.name), style = MaterialTheme.typography.bodyLarge)
                BigButton(stringResource(R.string.ok), {
                    sendingTo = null
                    model.record(Event.ASK_FAMILY)
                    val ask = context.getString(R.string.is_this_real)
                    val image = model.checkImage
                    if (image != null) {
                        askFamilyWithImage(context, image, askFamilyText(model.checkText, ask))
                    } else {
                        askFamilyByText(context, target.phone, askFamilyText(model.checkText, ask))
                    }
                })
            }
            choosing -> askers.forEach { person ->
                BigButton(person.name, { choosing = false; sendingTo = person }, kind = ButtonKind.Secondary)
            }
            askers.size == 1 -> BigButton("💬  " + stringResource(R.string.ask_x, askers[0].name), { sendingTo = askers[0] })
            askers.size > 1 -> BigButton("💬  " + stringResource(R.string.ask_family), { choosing = true })
        }
        BigButton(stringResource(R.string.ok_close), { model.clearCheck(); model.screen = Screen.Home }, kind = ButtonKind.Secondary)
    }
}
