package dev.pankajkhannade.nexus

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Dictated message confirm screen. Shows who it is for and the message in large type, reads it back,
 * and opens WhatsApp (or SMS) with the text typed in. The parent taps Send in that app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageScreen(model: NexusViewModel) {
    val draft = model.draft ?: return
    val context = LocalContext.current
    val whatsApp = remember { whatsAppPackage(context) }
    var editing by remember { mutableStateOf(false) }
    var typed by remember(model.draftVersion) { mutableStateOf(draft.text) }
    LaunchedEffect(model.draftVersion) { model.speakDraft() }
    val canSpeak = granted(context, Manifest.permission.RECORD_AUDIO) && model.voiceMode != VoiceMode.UNAVAILABLE
    val person = draft.person
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) model.resolveDraftRecipient()
    }

    // UX sprint 2: the shared bottom bar. मागे = don't send; the right slot speaks the message again, and
    // while listening it becomes ✓ झालं with ✕ रद्द करा (keep the old words) on the left.
    val onSpeak = LocalOnSpeak.current
    ParentScaffold(
        onBack = { if (model.dictating) model.stopDictating() else model.cancelDraft() },
        backLabel = if (model.dictating) "✕  " + stringResource(R.string.cancel_action) else null,
        speakLabel = when {
            model.dictating -> "✓  " + stringResource(R.string.listening_done)
            canSpeak && !editing -> "🎤  " + stringResource(R.string.speak_again)
            else -> null
        },
        onSpeak = when {
            model.dictating -> ({ model.doneDictating() })
            canSpeak && !editing -> ({ model.dictateAgain() })
            else -> ({ model.cancelDraft(); onSpeak() })
        },
        speakKind = if (model.dictating) ButtonKind.Call else ButtonKind.Primary,
    ) {
        if (person == null) {
            BigCentredText(stringResource(R.string.msg_who))
            draft.askedName?.let {
                Text(stringResource(R.string.msg_who_unknown, it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!model.hasContactsPermission()) {
                    BigButton(stringResource(R.string.msg_find_in_contacts), { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }, kind = ButtonKind.Secondary)
                }
            }
            model.draftChoices.forEachIndexed { i, p -> ContactChoice(p, i) { model.setDraftPerson(p) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 2) {
                model.saved.people.forEach { p ->
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 140.dp)
                            .clickable { model.setDraftPerson(p) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(p, model.saved.people, 88.dp)
                        Text(shownName(p), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(person, model.saved.people, 72.dp)
                Text(stringResource(R.string.msg_to, shownName(person)), style = MaterialTheme.typography.headlineMedium)
            }
        }

        // While listening, the live words take the message's place, right under the name, so they're on screen.
        if (model.dictating) {
            Text("🎤  " + stringResource(R.string.listening), style = MaterialTheme.typography.headlineSmall)
            LiveWords(model.dictationLive)
            return@ParentScaffold
        }

        if (editing) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.msg_edit_label)) },
                textStyle = MaterialTheme.typography.headlineSmall,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton(stringResource(R.string.done), { model.setDraftText(typed.trim()); editing = false })
        } else {
            val tapToEdit = stringResource(R.string.msg_tap_to_edit)
            OutlinedPanel(Modifier.clickable(onClickLabel = tapToEdit) { typed = draft.text; editing = true }) {
                Text(
                    draft.text.ifBlank { stringResource(R.string.msg_what) },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (draft.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(tapToEdit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Guard (2026-11-12): a name that was only roughly heard is confirmed before any send button appears.
        if (person != null && !editing && !draft.sure) {
            OutlinedPanel(color = NexusColors.WarningContainer) {
                Text(stringResource(R.string.msg_confirm_name, shownName(person)), style = MaterialTheme.typography.headlineSmall)
            }
            BigButton("✓  " + stringResource(R.string.msg_confirm_name_yes, shownName(person)), { model.confirmDraftPerson() }, kind = ButtonKind.Call)
            BigButton(stringResource(R.string.msg_someone_else), { model.clearDraftPerson() }, kind = ButtonKind.Secondary)
        }
        if (person != null && !editing && draft.sure) {
            val smsFirst = draft.channel == Channel.SMS || whatsApp == null
            if (!model.saved.messageExplained) {
                Text(
                    stringResource(if (smsFirst) R.string.msg_explain_sms else R.string.msg_explain_whatsapp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            val sendWhatsApp: @Composable (ButtonKind) -> Unit = { kind ->
                BigButton(stringResource(R.string.msg_send_whatsapp), { model.sendDraft(Channel.WHATSAPP) }, kind = kind)
            }
            val sendSms: @Composable (ButtonKind) -> Unit = { kind ->
                BigButton(stringResource(R.string.msg_send_sms), { model.sendDraft(Channel.SMS) }, kind = kind)
            }
            if (smsFirst) {
                sendSms(ButtonKind.Call)
                if (whatsApp != null) sendWhatsApp(ButtonKind.Secondary)
            } else {
                sendWhatsApp(ButtonKind.Call)
                sendSms(ButtonKind.Secondary)
            }
            if (model.notice.isNotBlank()) Text(model.notice, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
        BigButton(stringResource(R.string.msg_cancel), { model.cancelDraft() }, kind = ButtonKind.Quiet)
    }
}
