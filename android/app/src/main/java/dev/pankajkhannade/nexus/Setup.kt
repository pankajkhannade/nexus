package dev.pankajkhannade.nexus

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SETUP_STEPS = 5

val LANGUAGES = listOf("mr" to "मराठी", "hi" to "हिंदी", "en" to "English")

/** Child-assisted setup S1-S4. The child does this on the parent's phone. */
@Composable
fun SetupFlow(model: NexusViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(progress = { (model.setupStep + 1f) / SETUP_STEPS }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (model.setupStep > 0) {
                    BigButton(stringResource(R.string.back), { model.setupStep -= 1 }, Modifier.weight(0.4f), kind = ButtonKind.Quiet)
                }
                Text(
                    stringResource(R.string.setup_step_of, model.setupStep + 1, SETUP_STEPS),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        when (model.setupStep) {
            0 -> WelcomeStep(model)
            1 -> FamilyStep(model)
            2 -> HomeStep(model)
            3 -> NearHomeStep(model)
            else -> PermissionsStep(model)
        }
    }
}

/** S1: welcome and the parent's language, each with a sample phrase that can be played aloud. */
@Composable
private fun WelcomeStep(model: NexusViewModel) {
    val context = LocalContext.current
    var missingVoice by remember { mutableStateOf<String?>(null) }
    ScreenColumn {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.language_question), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LANGUAGES.forEach { (code, label) ->
                val sample = context.inLanguage(code).getString(R.string.try_phrase, context.inLanguage(code).getString(R.string.sample_name))
                val selected = model.setupLanguage == code
                Surface(
                    shape = CardShape,
                    color = if (selected) NexusColors.SurfaceContainer else NexusColors.Background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (selected) 3.dp else 1.5.dp, if (selected) NexusColors.Primary else NexusColors.Outline, CardShape)
                        .selectable(selected = selected, role = Role.RadioButton) { model.chooseLanguage(code) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (selected) "✓ " else "") + label, style = MaterialTheme.typography.titleLarge)
                            Text("“$sample”", style = MaterialTheme.typography.bodyLarge)
                        }
                        BigButton(stringResource(R.string.play_sample), {
                            model.chooseLanguage(code)
                            if (model.speaker.hasVoice(code)) {
                                missingVoice = null
                                model.speaker.speak(code, sample)
                            } else {
                                missingVoice = code
                            }
                        }, Modifier.weight(0.7f), kind = ButtonKind.Secondary)
                    }
                }
            }
        }
        missingVoice?.let { code ->
            OutlinedPanel(color = NexusColors.WarningContainer) {
                Text(stringResource(R.string.voice_missing, LANGUAGES.first { it.first == code }.second), style = MaterialTheme.typography.bodyLarge)
                BigButton(stringResource(R.string.voice_install), {
                    runCatching {
                        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, kind = ButtonKind.Secondary)
            }
        }
        Text(stringResource(R.string.welcome_self), color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton(stringResource(R.string.continue_), { model.setupStep = 1 })
    }
}

/** S2: up to 4 people from the system contact picker (no contacts permission) or typed in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyStep(model: NexusViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<PersonDraft?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val (name, raw) = readPickedPhone(context, uri) ?: return@rememberLauncherForActivityResult
        // The contact's own photo, when the phone has one we may read (copied into Nexus, never uploaded).
        val photo = contactPhotoUri(context, uri)?.let { saveFace(context, it) }.orEmpty()
        val phone = normalizePhone(raw, guessCountry(context).dial)
        if (phone == null) {
            note = context.getString(R.string.contact_number_unusable)
            return@rememberLauncherForActivityResult
        }
        model.setupPeople.firstOrNull { it.phone == phone }?.let {
            note = context.getString(R.string.already_added, it.name)
            return@rememberLauncherForActivityResult
        }
        note = null
        editing = PersonDraft(null, name, displayPhone(phone), "", askFamily = model.setupPeople.none { it.askFamily }, alias = "", typed = false, photo = photo)
    }
    val people = model.setupPeople
    ScreenColumn {
        Text(stringResource(R.string.family_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.family_body), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
            people.forEach { person ->
                PersonTile(Modifier.weight(1f), person, people) {
                    editing = PersonDraft(person, person.name, displayPhone(person.phone), person.relation, person.askFamily, person.alias, typed = true, photo = person.photo)
                }
            }
            if (people.size < NexusViewModel.MAX_PEOPLE) {
                AddTile(Modifier.weight(1f)) {
                    picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }
            }
        }
        if (people.size < NexusViewModel.MAX_PEOPLE) {
            BigButton(stringResource(R.string.type_number_instead), {
                editing = PersonDraft(null, "", "", "", askFamily = people.none { it.askFamily }, alias = "", typed = true)
            }, kind = ButtonKind.Quiet)
        }
        note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
        SaveToPhoneContacts(people.filter { it.id in model.setupTyped })
        PhoneBookOption { model.permissionAnswered(Perm.CONTACTS, it) }
        val reason = when {
            people.isEmpty() -> R.string.need_one_person
            people.none { it.askFamily } -> R.string.need_ask_family
            else -> null
        }
        reason?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        BigButton(stringResource(R.string.continue_), { model.setupStep = 2 }, enabled = reason == null)
    }
    editing?.let { draft ->
        PersonSheet(
            draft = draft,
            dial = guessCountry(context).dial,
            onDismiss = { editing = null },
            onRemove = draft.original?.let { original -> { model.removeSetupPerson(original); editing = null } },
            onSave = { d -> model.putSetupPerson(d.original, d.name, d.phone, d.relation, d.askFamily, d.alias, d.photo, typed = d.typed).also { if (it) editing = null } },
            duplicateOf = { phone -> model.setupPeople.firstOrNull { it.phone == phone && it.id != draft.original?.id }?.name },
        )
    }
}

/**
 * Handoff continuity (UX sprint 1, fix #5): a person typed in by number is not in the phone's contacts, so
 * the dialer would show a bare number. One tap opens the phone's own "new contact" screen with the name and
 * number filled in (ACTION_INSERT: no WRITE_CONTACTS permission; the child taps Save there).
 */
@Composable
private fun SaveToPhoneContacts(typed: List<Person>) {
    if (typed.isEmpty()) return
    val context = LocalContext.current
    var done by remember { mutableStateOf<Set<String>>(emptySet()) }
    val left = typed.filter { it.id !in done }
    if (left.isEmpty()) return
    OutlinedPanel {
        Text(stringResource(R.string.save_to_phone_why), style = MaterialTheme.typography.bodyLarge)
        left.forEach { person ->
            BigButton(stringResource(R.string.save_to_phone, person.name), {
                if (openAddContact(context, person.name, person.phone)) done = done + person.id
            }, kind = ButtonKind.Secondary)
        }
    }
}

/** Setup option: let the parent call anyone in the phone book by name. Contacts stay on the phone. */
@Composable
private fun PhoneBookOption(onAnswer: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(granted(context, Manifest.permission.READ_CONTACTS)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> on = ok; onAnswer(ok) }
    OutlinedPanel {
        Text("📒  " + stringResource(R.string.setup_contacts_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.setup_contacts_body), style = MaterialTheme.typography.bodyLarge)
        if (on) {
            Text(stringResource(R.string.setup_contacts_on), style = MaterialTheme.typography.titleMedium, color = NexusColors.Call)
        } else {
            BigButton(stringResource(R.string.setup_contacts_allow), { ask.launch(Manifest.permission.READ_CONTACTS) }, kind = ButtonKind.Secondary)
        }
    }
}

@Composable
private fun PersonTile(modifier: Modifier, person: Person, family: List<Person>, onClick: () -> Unit) {
    Surface(
        shape = CardShape,
        color = NexusColors.SurfaceContainer,
        contentColor = NexusColors.OnSurface,
        modifier = modifier
            .heightIn(min = 140.dp)
            .border(CardBorder, CardShape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Avatar(person, family, 56.dp)
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            val relation = if (person.relation.isNotBlank()) stringResource(relationText(person.relation)) else ""
            val ask = if (person.askFamily) stringResource(R.string.ask_tag) else ""
            Text(listOf(relation, ask).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = CardShape,
        color = NexusColors.Background,
        modifier = modifier
            .heightIn(min = 140.dp)
            .border(CardBorder, CardShape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("+", fontSize = 40.sp, color = NexusColors.Primary)
            Text(stringResource(R.string.add_person), style = MaterialTheme.typography.titleMedium)
        }
    }
}

data class PersonDraft(
    val original: Person?,
    val name: String,
    val phone: String,
    val relation: String,
    val askFamily: Boolean,
    val alias: String,
    val typed: Boolean,
    /** faces/ path ("" = coloured initial). From the photo picker or the contact's own photo. */
    val photo: String = "",
)

/** Details for one person: name as the parent says it, relation, Ask-family flag, spoken alias. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonSheet(
    draft: PersonDraft,
    dial: String,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)?,
    onSave: (PersonDraft) -> Boolean,
    duplicateOf: (String) -> String?,
) {
    var d by remember { mutableStateOf(draft) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // The child can pick a face from the gallery (system photo picker: no storage permission).
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { saveFace(context, it) }?.let { d = d.copy(photo = it) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = NexusColors.Background) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(Person(id = draft.original?.id ?: "new", name = d.name.ifBlank { "?" }, phone = "", photo = d.photo), emptyList(), 56.dp)
                    BigButton(
                        stringResource(if (d.photo.isBlank()) R.string.add_photo else R.string.change_photo),
                        { pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        Modifier.weight(1f), kind = ButtonKind.Secondary,
                    )
                }
                OutlinedTextField(
                    d.name, { d = d.copy(name = it) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.name_as_said)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                if (d.typed) {
                    val problem = phoneProblem(d.phone, dial, strict = false)
                    OutlinedTextField(
                        d.phone, { d = d.copy(phone = it) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_phone)) }, singleLine = true, isError = problem != null,
                        supportingText = problem?.let { { Text(phoneProblemText(it)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    )
                } else {
                    Text(d.phone, style = MaterialTheme.typography.bodyLarge)
                }
                Text(stringResource(R.string.relation_title), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Relation.ALL.forEach { r ->
                        FilterChip(
                            selected = d.relation == r,
                            onClick = { d = d.copy(relation = r) },
                            label = { Text(stringResource(relationText(r))) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ask_family_toggle), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(d.askFamily, { d = d.copy(askFamily = it) })
                }
                OutlinedTextField(
                    d.alias, { d = d.copy(alias = it) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.alias_label)) },
                    supportingText = { Text(stringResource(R.string.alias_hint)) }, singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                BigButton(stringResource(R.string.save), {
                    val phone = normalizePhone(d.phone, dial)
                    error = when {
                        d.name.isBlank() -> context.getString(R.string.person_need_name)
                        phone == null -> context.getString(phoneProblem(d.phone, dial, strict = true)?.res() ?: R.string.person_need_phone)
                        duplicateOf(phone) != null -> context.getString(R.string.already_added, duplicateOf(phone))
                        else -> null
                    }
                    if (error == null && !onSave(d)) error = context.getString(R.string.person_need_phone)
                })
                onRemove?.let { BigButton(stringResource(R.string.remove), it, kind = ButtonKind.Secondary) }
                BigButton(stringResource(R.string.close), onDismiss, kind = ButtonKind.Quiet)
            }
        }
    }
}

/** S3: optional home address for "Book a ride home". */
@Composable
private fun HomeStep(model: NexusViewModel) {
    val context = LocalContext.current
    ScreenColumn {
        Text(stringResource(R.string.home_step_title), style = MaterialTheme.typography.headlineMedium)
        PlaceBox(stringResource(R.string.place_home), model.setupHome) { model.setupHome = it }
        Text(stringResource(R.string.place_search_network), color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton(stringResource(R.string.continue_), { model.setupStep = 3 }, enabled = model.setupHome.trim().length >= 4)
        BigButton(stringResource(R.string.skip), { model.setupHome = ""; model.setupStep = 3 }, kind = ButtonKind.Secondary)
    }
}

/** S15 setup: one app per need (top apps, Installed tag or Install), and the home PIN. Also in Family settings. */
@Composable
fun NearHomeStep(model: NexusViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (model.setupServices.isEmpty()) model.setupServices = defaultPicks { isInstalled(context, it) }
        if (model.setupPincode.isBlank() && model.setupHome.isNotBlank()) model.setupPincode = model.findPincode(model.setupHome)
    }
    ScreenColumn {
        Text(stringResource(R.string.near_setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.near_setup_body), style = MaterialTheme.typography.bodyLarge)
        NearHomePickers(
            pincode = model.setupPincode,
            onPincode = { model.setupPincode = it },
            pickedFor = { model.setupServices[it.key].orEmpty() },
            onPick = { need, pkg -> model.setupServices = model.setupServices + (need.key to pkg) },
            showRide = model.setupHome.isNotBlank(),
        )
        BigButton(stringResource(R.string.continue_), { model.setupStep = 4 })
    }
}

/** S4 (child part): microphone and direct-calling permissions, each explained first, then hand over. */
@Composable
private fun PermissionsStep(model: NexusViewModel) {
    val context = LocalContext.current
    var micDone by remember { mutableStateOf(granted(context, Manifest.permission.RECORD_AUDIO)) }
    var callDone by remember { mutableStateOf(granted(context, Manifest.permission.CALL_PHONE)) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> micDone = true; model.markMicAsked(); model.permissionAnswered(Perm.MIC, ok) }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> callDone = true; model.markCallAsked(); model.permissionAnswered(Perm.CALL, ok) }
    when {
        !micDone -> PermissionCard(
            "🎤", stringResource(R.string.mic_title), stringResource(R.string.mic_why_child),
            stringResource(if (model.voiceMode == VoiceMode.ON_DEVICE) R.string.mic_on_device else R.string.mic_online),
            onAllow = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onNotNow = { micDone = true; model.markMicAsked(); model.permissionAnswered(Perm.MIC, false) },
        )
        !callDone -> PermissionCard(
            "📞", stringResource(R.string.call_title), stringResource(R.string.call_why), stringResource(R.string.call_stays),
            onAllow = { callLauncher.launch(Manifest.permission.CALL_PHONE) },
            onNotNow = { callDone = true; model.markCallAsked(); model.permissionAnswered(Perm.CALL, false) },
        )
        else -> ScreenColumn {
            Text(stringResource(R.string.handover_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.handover_body), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.handover_settings), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!granted(context, Manifest.permission.RECORD_AUDIO)) {
                Text(stringResource(R.string.mic_off_setup), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BigButton(stringResource(R.string.handover_button), {
                model.finishSetup(guessCountry(context))
                model.startPractice()
            })
        }
    }
}

fun granted(context: android.content.Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** S4 (parent part), in the parent's language: press the mic and say the phrase. The magic moment. */
@Composable
fun TryItScreen(model: NexusViewModel, onSpeak: () -> Unit) {
    val askPerson = model.saved.people.firstOrNull { it.askFamily } ?: model.saved.people.firstOrNull()
    Centered {
        askPerson?.let { Avatar(it, model.saved.people, 120.dp) }
        BigCentredText(stringResource(R.string.try_title))
        Text(
            "“" + stringResource(R.string.try_phrase, askPerson?.alias?.ifBlank { askPerson.name } ?: "") + "”",
            style = MaterialTheme.typography.headlineMedium,
        )
        MicButton(onSpeak)
        BigButton(stringResource(R.string.skip), { model.tryingInSetup = false }, kind = ButtonKind.Quiet)
    }
}

/** 96dp round mic button with a word under it. */
@Composable
fun MicButton(onClick: () -> Unit) {
    val label = stringResource(R.string.speak)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            shape = androidx.compose.foundation.shape.CircleShape,
            color = NexusColors.Primary,
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(96.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("🎤", fontSize = 40.sp)
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun PlaceBox(label: String, value: String, near: Place? = null, onChange: (String) -> Unit) {
    var hits by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf("") }
    LaunchedEffect(value) {
        val query = value.trim()
        if (query.length < 3 || query == picked) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        hits = withContext(Dispatchers.IO) {
            runCatching { suggestPlaces(query, near) }.getOrDefault(emptyList())
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    hits.forEach { place ->
        Text(
            place,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(CardBorder, CardShape)
                .clickable {
                    picked = place
                    hits = emptyList()
                    onChange(place)
                }
                .padding(14.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun phoneProblemText(problem: PhoneProblem): String = stringResource(problem.res())

fun PhoneProblem.res(): Int = when (this) {
    PhoneProblem.DIGITS_ONLY -> R.string.phone_digits_only
    PhoneProblem.INDIA_LENGTH -> R.string.phone_india_length
    PhoneProblem.INDIA_START -> R.string.phone_india_start
    PhoneProblem.LENGTH -> R.string.phone_length
}

@Composable
fun CountryPicker(current: Country, onPick: (Country) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        BigButton(stringResource(R.string.country_item, current.name, current.dial), { open = true }, kind = ButtonKind.Secondary)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            COUNTRIES.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.country_item, item.name, item.dial)) },
                    onClick = { onPick(item); open = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
