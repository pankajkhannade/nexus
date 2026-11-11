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
import androidx.compose.ui.platform.testTag
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
    ParentScaffold(onBack = { model.clearCheck(); model.screen = Screen.Home }) {
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
        // "I already paid" needs no message to check first.
        BigButton("⚠️  " + stringResource(R.string.money_gone), { model.openMoneyGone() }, kind = ButtonKind.Secondary)
    }
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/**
 * The verdict (UX sprint 1, fix #4): the safe action, "Ask <child>", is pinned above the bottom bar, so it is
 * on screen without scrolling on a small phone at large text. The checked message is folded away; a
 * "Money already gone? Call 1930" line (India's cyber-fraud helpline) is offered for warnings.
 */
@Composable
private fun VerdictView(model: NexusViewModel, verdict: Verdict) {
    val context = LocalContext.current
    val askers = model.saved.people.filter { it.askFamily }.ifEmpty { model.saved.people }
    var choosing by remember { mutableStateOf(false) }
    val hasWhatsApp = remember { isInstalled(context, "com.whatsapp") }
    var sendingTo by remember { mutableStateOf<Person?>(null) }
    var showText by remember { mutableStateOf(false) }
    val close = { model.clearCheck(); model.screen = Screen.Home }
    ParentScaffold(onBack = close, above = {
        val target = sendingTo
        when {
            target != null -> {
                val howTo = when {
                    model.checkImage != null && hasWhatsApp -> R.string.whatsapp_pick_chat
                    model.checkImage != null -> R.string.share_pick_app
                    hasWhatsApp -> R.string.whatsapp_will_open
                    else -> R.string.sms_will_open
                }
                Text(stringResource(howTo, shownName(target)), style = MaterialTheme.typography.bodyLarge)
                BigButton(stringResource(R.string.ok), {
                    sendingTo = null
                    model.record(Event.ASK_FAMILY)
                    val ask = context.getString(R.string.is_this_real)
                    val image = model.checkImage
                    if (image != null) {
                        askFamilyWithImage(context, image, askFamilyText(model.checkText, ask))
                    } else {
                        if (hasWhatsApp) model.speak(R.string.coach_whatsapp_send) else model.speak(R.string.coach_sms_send)
                        askFamilyByText(context, target.phone, askFamilyText(model.checkText, ask))
                    }
                })
            }
            choosing -> askers.forEach { person ->
                BigButton(shownName(person), { choosing = false; sendingTo = person }, kind = ButtonKind.Secondary)
            }
            askers.size == 1 -> BigButton("💬  " + stringResource(R.string.ask_x, shownName(askers[0])), { sendingTo = askers[0] }, Modifier.testTag("safe_action"))
            askers.size > 1 -> BigButton("💬  " + stringResource(R.string.ask_family), { choosing = true }, Modifier.testTag("safe_action"))
        }
    }) {
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
        if (verdict.level != Level.NONE) {
            // UX sprint 2: the full पैसे गेले steps (1930, bank, cybercrime.gov.in, tell the child), not a bare dial pad.
            BigButton("⚠️  " + stringResource(R.string.money_gone), { model.openMoneyGone() }, Modifier.testTag("money_gone"), kind = ButtonKind.Secondary)
        }
        if (model.checkText.isNotBlank()) {
            BigButton(stringResource(if (showText) R.string.hide_message else R.string.show_message), { showText = !showText }, kind = ButtonKind.Quiet)
            if (showText) {
                OutlinedPanel {
                    Text(stringResource(R.string.message_checked), style = MaterialTheme.typography.titleSmall)
                    Text(model.checkText.take(400), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        BigButton(stringResource(R.string.ok_close), close, kind = ButtonKind.Secondary)
    }
}
