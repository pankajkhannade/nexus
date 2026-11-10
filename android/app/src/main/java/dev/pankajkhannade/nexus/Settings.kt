package dev.pankajkhannade.nexus

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** S11 Family settings (child scale). Reached by holding the home hint for 2 seconds. */
@Composable
fun SettingsScreen(model: NexusViewModel) {
    val context = LocalContext.current
    val saved = model.saved
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<PersonDraft?>(null) }
    var home by remember { mutableStateOf(saved.home.address) }
    var command by remember { mutableStateOf("") }
    var pin by remember(saved.pincode) { mutableStateOf(saved.pincode) }
    var exportText by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        ScreenColumn {
            Text(stringResource(R.string.family_settings), style = MaterialTheme.typography.headlineMedium)

            Text(stringResource(R.string.people), style = MaterialTheme.typography.titleLarge)
            saved.people.forEachIndexed { i, person ->
                OutlinedPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(person, saved.people, 48.dp)
                        Text(
                            person.name + "\n" + displayPhone(person.phone) + (if (person.askFamily) " · " + stringResource(R.string.ask_tag) else ""),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton(stringResource(R.string.edit), {
                            editing = PersonDraft(person, person.name, displayPhone(person.phone), person.relation, person.askFamily, person.alias, typed = true, photo = person.photo)
                        }, Modifier.weight(1f), kind = ButtonKind.Secondary)
                        if (i > 0) BigButton("↑", { model.movePerson(person, up = true) }, Modifier.weight(0.5f), kind = ButtonKind.Secondary)
                        BigButton(stringResource(R.string.remove), {
                            val undo = model.removePerson(person)
                            scope.launch {
                                val r = snackbar.showSnackbar(
                                    context.getString(R.string.removed_x, person.name),
                                    actionLabel = context.getString(R.string.undo),
                                    duration = SnackbarDuration.Long,
                                )
                                if (r == SnackbarResult.ActionPerformed) undo()
                            }
                        }, Modifier.weight(1f), kind = ButtonKind.Quiet)
                    }
                }
            }
            if (saved.people.size < NexusViewModel.MAX_PEOPLE) {
                BigButton(stringResource(R.string.add_person), {
                    editing = PersonDraft(null, "", "", "", askFamily = saved.people.none { it.askFamily }, alias = "", typed = true)
                }, kind = ButtonKind.Secondary)
            }

            Text(stringResource(R.string.parent_language), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGES.forEach { (code, label) ->
                    BigButton((if (saved.language == code) "✓ " else "") + label, { model.applyLanguage(code) }, Modifier.weight(1f),
                        kind = if (saved.language == code) ButtonKind.Primary else ButtonKind.Secondary)
                }
            }
            BigButton("🎤  " + stringResource(R.string.retest_mic), {
                if (granted(context, Manifest.permission.RECORD_AUDIO)) model.startListening() else model.screen = Screen.Home
            }, kind = ButtonKind.Secondary)

            Text(stringResource(R.string.place_home), style = MaterialTheme.typography.titleLarge)
            PlaceBox(stringResource(R.string.place_home), home, saved.home) { home = it }
            BigButton(stringResource(R.string.save), {
                val undo = model.saveHome(home)
                scope.launch {
                    val r = snackbar.showSnackbar(
                        context.getString(R.string.home_saved),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        undo()
                        home = model.saved.home.address
                    }
                }
            }, enabled = home.trim() != saved.home.address, kind = ButtonKind.Secondary)

            Text("📍  " + stringResource(R.string.near_apps), style = MaterialTheme.typography.titleLarge)
            NearHomePickers(
                pincode = pin,
                onPincode = { pin = it; model.setPincode(it) },
                pickedFor = { model.pickedPackage(it) },
                onPick = { need, pkg -> model.pickService(need, pkg) },
                showRide = saved.home.isSet,
            )

            Text(stringResource(R.string.country), style = MaterialTheme.typography.titleLarge)
            CountryPicker(COUNTRIES.firstOrNull { it.code == saved.countryCode } ?: guessCountry(context)) { model.setCountry(it) }

            Text(stringResource(R.string.permissions), style = MaterialTheme.typography.titleLarge)
            Text(permissionLine(context, Manifest.permission.RECORD_AUDIO, R.string.perm_mic), style = MaterialTheme.typography.bodyLarge)
            Text(permissionLine(context, Manifest.permission.CALL_PHONE, R.string.perm_call), style = MaterialTheme.typography.bodyLarge)
            BigButton(stringResource(R.string.open_phone_settings), {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }, kind = ButtonKind.Secondary)

            Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    R.string.usage_summary,
                    model.usage.activeDays(),
                    model.usage.total(Event.CALL),
                    model.usage.total(Event.VOICE),
                    model.usage.total(Event.SAFETY_CHECK),
                    model.usage.total(Event.ASK_FAMILY),
                    model.usage.total(Event.RIDE),
                    model.usage.total(Event.WHATSAPP),
                    model.usage.total(Event.MESSAGE),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(
                    R.string.usage_near,
                    model.usage.total(Event.NEAR_GROCERY),
                    model.usage.total(Event.NEAR_MEDICINE),
                    model.usage.total(Event.NEAR_DOCTOR),
                    model.usage.total(Event.NEAR_RIDE),
                    model.usage.total(Event.NEAR_HOME_SERVICE),
                    model.usage.total(Event.NEAR_EMERGENCY),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            val export = exportText
            if (export == null) {
                BigButton(stringResource(R.string.export_usage), {
                    exportText = exportText(model.usage, model.feedback, saved.installDay, model.today, saved.language)
                }, kind = ButtonKind.Secondary)
            } else {
                OutlinedPanel { Text(export, style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.export_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigButton(stringResource(R.string.share), { shareText(context, export) })
            }

            BigButton("📊  " + stringResource(R.string.metrics_title), { model.screen = Screen.Metrics }, kind = ButtonKind.Secondary)

            Text(stringResource(R.string.test_command), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.test_command_hint)) }, singleLine = true)
            BigButton(stringResource(R.string.try_command), { model.understood(command, typed = true) }, enabled = command.isNotBlank(), kind = ButtonKind.Secondary)

            BigButton(stringResource(R.string.privacy), { model.screen = Screen.Privacy }, kind = ButtonKind.Secondary)
            BigButton(stringResource(R.string.done), { model.screen = Screen.Home })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    editing?.let { draft ->
        PersonSheet(
            draft = draft,
            dial = model.dial,
            onDismiss = { editing = null },
            onRemove = null,
            onSave = { d -> model.savePerson(d.original, d.name, d.phone, d.relation, d.askFamily, d.alias, d.photo).also { if (it) editing = null } },
            duplicateOf = { phone -> saved.people.firstOrNull { it.phone == phone && it.id != draft.original?.id }?.name },
        )
    }
}

private fun permissionLine(context: android.content.Context, permission: String, label: Int): String =
    context.getString(label) + ": " + context.getString(if (granted(context, permission)) R.string.perm_on else R.string.perm_off)

/** S13 Privacy: short cards in the parent's language. The voice line reflects what this phone actually does. */
@Composable
fun PrivacyScreen(model: NexusViewModel, onBack: () -> Unit) {
    val voice = if (model.voiceMode == VoiceMode.ON_DEVICE) R.string.voice_on_device else R.string.privacy_voice_online
    val cards = listOf(
        Triple("👪", R.string.privacy_stored_title, R.string.privacy_stored),
        Triple("🎤", R.string.privacy_voice_title, voice),
        Triple("🛡", R.string.privacy_messages_title, R.string.privacy_messages),
        Triple("📤", R.string.privacy_share_title, R.string.privacy_share),
        Triple("🌐", R.string.privacy_network_title, R.string.privacy_network),
    )
    ScreenColumn {
        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.headlineMedium)
        cards.forEach { (icon, title, body) ->
            OutlinedPanel {
                Text("$icon  " + stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
            }
        }
        BigButton(stringResource(R.string.back), onBack, kind = ButtonKind.Secondary)
    }
}
