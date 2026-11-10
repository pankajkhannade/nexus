package dev.pankajkhannade.nexus

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
fun HomeScreen(model: NexusViewModel, onPrivacy: () -> Unit) {
    val saved = model.saved
    val context = LocalContext.current
    val reading = readMessage(model.draft, saved)
    var ride by remember { mutableStateOf<Trip?>(null) }
    var person by remember { mutableStateOf<Person?>(null) }
    var askContacts by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<Person>?>(null) }
    var explainContacts by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (said.isNotBlank()) model.draft = said
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val asked = askContacts ?: return@rememberLauncherForActivityResult
        explainContacts = false
        if (granted) {
            matches = lookupContacts(context, asked, saved.dial)
        } else {
            matches = emptyList()
            model.notice = context.getString(R.string.contacts_denied)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val (name, raw) = readPickedPhone(context, uri) ?: return@rememberLauncherForActivityResult
        val added = model.savePerson(name, raw)
        toast(context, if (added != null) context.getString(R.string.person_saved, added.name) else context.getString(R.string.contact_number_unusable))
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy), style = MaterialTheme.typography.labelMedium) }
        }
        Text(stringResource(R.string.home_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)

        Text(stringResource(R.string.people_title), style = MaterialTheme.typography.titleLarge)
        if (saved.people.isEmpty()) {
            Text(stringResource(R.string.no_people), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        saved.people.forEach { item ->
            PersonRow(item, onCall = { openDial(context, item.phone) }, onWhatsApp = {
                if (!openWhatsApp(context, item.phone, "")) model.notice = context.getString(R.string.whatsapp_missing)
            })
        }

        Text(stringResource(R.string.places_title), style = MaterialTheme.typography.titleLarge)
        if (saved.home.isSet) {
            BigButton(stringResource(R.string.go_home), { ride = Trip(dropoff = Stop(PlaceKind.HOME, saved.home)) })
        } else {
            BigButton(stringResource(R.string.add_home), { editing = true }, kind = ButtonKind.Secondary)
        }
        if (saved.office.isSet) {
            BigButton(stringResource(R.string.go_office), { ride = Trip(dropoff = Stop(PlaceKind.OFFICE, saved.office)) }, kind = ButtonKind.Secondary)
        }

        Text(stringResource(R.string.command_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = model.draft,
            onValueChange = { model.draft = it; model.notice = "" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.command_hint)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        BigButton(stringResource(R.string.speak), {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage())
            runCatching { speech.launch(intent) }.onFailure { model.notice = context.getString(R.string.speech_unavailable) }
        }, kind = ButtonKind.Secondary)

        if (model.draft.isNotBlank()) {
            ReadingCard(
                reading = reading,
                onPerson = { item ->
                    if (reading.ask == Ask.MESSAGE) {
                        if (!openWhatsApp(context, item.phone, "")) model.notice = context.getString(R.string.whatsapp_missing)
                    } else {
                        openDial(context, item.phone)
                    }
                },
                onNumber = { openDial(context, it) },
                onRide = { ride = it },
                onEdit = { editing = true },
                onFind = { asked ->
                    askContacts = asked
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    when {
                        granted -> {
                            explainContacts = false
                            matches = lookupContacts(context, asked, saved.dial)
                        }
                        !saved.contactsExplained -> {
                            explainContacts = true
                            matches = null
                        }
                        else -> permission.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
            )
        }
        if (model.notice.isNotEmpty()) {
            Text(model.notice, style = MaterialTheme.typography.bodyLarge)
        }
        BigButton(stringResource(R.string.edit_people_places), { editing = true }, kind = ButtonKind.Quiet)
    }

    ride?.let { trip ->
        RideDialog(
            trip = trip,
            last = CabApp.from(saved.lastCab),
            onClose = { ride = null },
            onCab = { app ->
                ride = null
                model.rememberCab(app)
                val message = rideMessage(context, app, openRide(context, app, trip))
                model.notice = message.orEmpty()
                message?.let { toast(context, it) }
            },
            onMaps = {
                ride = null
                if (!openMaps(context, trip)) model.notice = context.getString(R.string.ride_nothing)
            },
        )
    }
    person?.let { item ->
        PersonDialog(item, onClose = { person = null }) { action ->
            person = null
            when (action) {
                Ask.CALL -> openDial(context, item.phone)
                Ask.MESSAGE -> if (!openWhatsApp(context, item.phone, "")) model.notice = context.getString(R.string.whatsapp_missing)
            }
        }
    }
    if (askContacts != null && (explainContacts || matches != null)) {
        ContactsDialog(
            asked = askContacts!!,
            explain = explainContacts,
            matches = matches.orEmpty(),
            onDismiss = {
                askContacts = null
                matches = null
                explainContacts = false
            },
            onAllow = {
                model.explainContacts()
                explainContacts = false
                permission.launch(Manifest.permission.READ_CONTACTS)
            },
            onPick = { chosen ->
                val savedPerson = model.savePerson(chosen.name, chosen.phone)
                askContacts = null
                matches = null
                explainContacts = false
                person = savedPerson
            },
        )
    }
    if (editing) {
        EditDialog(
            saved = saved,
            onClose = { editing = false },
            onPlace = model::savePlace,
            onRemove = model::removePerson,
            onPick = { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
        )
    }
}

/** Speech language follows the app language (per-app locale): hi-IN, mr-IN, or en-IN. */
private fun speechLanguage(): String = when (Locale.getDefault().language) {
    "hi" -> "hi-IN"
    "mr" -> "mr-IN"
    else -> "en-IN"
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

private fun rideMessage(context: Context, app: CabApp, result: RideResult): String? = when (result) {
    RideResult.OPENED -> null
    RideResult.OPENED_AND_COPIED -> context.getString(R.string.address_copied)
    RideResult.NOT_INSTALLED_OPENED_MAPS -> context.getString(R.string.ride_not_installed, app.label)
    RideResult.NOTHING_TO_OPEN -> context.getString(R.string.ride_nothing)
}

@StringRes
private fun PlaceKind.label(): Int = when (this) {
    PlaceKind.HOME -> R.string.place_home
    PlaceKind.OFFICE -> R.string.place_office
    PlaceKind.ADDRESS -> R.string.place_home // not shown: addresses display their own text
}

@Composable
private fun stopText(stop: Stop?): String = when {
    stop == null -> stringResource(R.string.my_location)
    stop.kind == PlaceKind.ADDRESS -> stop.place.address
    stop.place.isSet -> "${stringResource(stop.kind.label())} · ${stop.place.address}"
    else -> stringResource(stop.kind.label())
}

@Composable
private fun OutlinedCard(content: @Composable () -> Unit) {
    Surface(
        shape = CardShape,
        color = NexusColors.SurfaceContainer,
        contentColor = NexusColors.OnSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(CardBorder, CardShape),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun PersonRow(person: Person, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val callLabel = stringResource(R.string.found_call, person.name)
    val waLabel = stringResource(R.string.found_message, person.name)
    OutlinedCard {
        Text(person.name, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.call), onCall, Modifier.weight(1f).semantics { contentDescription = callLabel }, kind = ButtonKind.Call)
            BigButton(stringResource(R.string.whatsapp), onWhatsApp, Modifier.weight(1f).semantics { contentDescription = waLabel }, kind = ButtonKind.Secondary)
        }
    }
}

@Composable
private fun ReadingCard(
    reading: Reading,
    onPerson: (Person) -> Unit,
    onNumber: (String) -> Unit,
    onRide: (Trip) -> Unit,
    onEdit: () -> Unit,
    onFind: (String) -> Unit,
) {
    OutlinedCard {
        var anything = false
        reading.person?.let { item ->
            anything = true
            val label = if (reading.ask == Ask.MESSAGE) R.string.found_message else R.string.found_call
            BigButton(stringResource(label, item.name), { onPerson(item) }, kind = if (reading.ask == Ask.MESSAGE) ButtonKind.Primary else ButtonKind.Call)
        }
        reading.number?.let { number ->
            anything = true
            BigButton(stringResource(R.string.found_call, displayPhone(number)), { onNumber(number) }, kind = ButtonKind.Call)
        }
        reading.askedName?.let { asked ->
            anything = true
            Text(stringResource(R.string.not_saved, asked), style = MaterialTheme.typography.bodyLarge)
            BigButton(stringResource(R.string.find_in_contacts, asked), { onFind(asked) }, kind = ButtonKind.Secondary)
        }
        reading.trip?.let { trip ->
            anything = true
            Text(stringResource(R.string.trip_from, stopText(trip.pickup)), style = MaterialTheme.typography.bodyLarge)
            trip.dropoff?.let { Text(stringResource(R.string.trip_to, stopText(it)), style = MaterialTheme.typography.bodyLarge) }
            val missing = listOfNotNull(trip.pickup, trip.dropoff).firstOrNull { !it.place.isSet }
            if (missing != null) {
                Text(stringResource(R.string.place_not_set, stringResource(missing.kind.label())), style = MaterialTheme.typography.bodyLarge)
                BigButton(stringResource(R.string.edit_people_places), onEdit, kind = ButtonKind.Secondary)
            } else {
                BigButton(stringResource(R.string.book_ride), { onRide(trip) })
            }
        }
        if (!anything) {
            Text(stringResource(R.string.nothing_found), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DialogCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun RideDialog(trip: Trip, last: CabApp?, onClose: () -> Unit, onCab: (CabApp) -> Unit, onMaps: () -> Unit) {
    val order = CabApp.entries.sortedByDescending { it == (last ?: CabApp.UBER) }
    Dialog(onDismissRequest = onClose) {
        DialogCard {
            Text(stringResource(R.string.book_ride), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.trip_from, stopText(trip.pickup)), style = MaterialTheme.typography.bodyLarge)
            trip.dropoff?.let { Text(stringResource(R.string.trip_to, stopText(it)), style = MaterialTheme.typography.bodyLarge) }
            Text(stringResource(R.string.ride_confirm_in_app), color = MaterialTheme.colorScheme.onSurfaceVariant)
            order.forEachIndexed { index, app ->
                BigButton(
                    stringResource(R.string.ride_open_app, app.label),
                    { onCab(app) },
                    kind = if (index == 0) ButtonKind.Primary else ButtonKind.Secondary,
                )
            }
            BigButton(stringResource(R.string.ride_maps), onMaps, kind = ButtonKind.Quiet)
            BigButton(stringResource(R.string.close), onClose, kind = ButtonKind.Quiet)
        }
    }
}

@Composable
private fun PersonDialog(person: Person, onClose: () -> Unit, onAction: (Ask) -> Unit) {
    Dialog(onDismissRequest = onClose) {
        DialogCard {
            Text(person.name, style = MaterialTheme.typography.titleLarge)
            BigButton(stringResource(R.string.call), { onAction(Ask.CALL) }, kind = ButtonKind.Call)
            BigButton(stringResource(R.string.whatsapp), { onAction(Ask.MESSAGE) }, kind = ButtonKind.Secondary)
            BigButton(stringResource(R.string.close), onClose, kind = ButtonKind.Quiet)
        }
    }
}

@Composable
private fun ContactsDialog(
    asked: String,
    explain: Boolean,
    matches: List<Person>,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
    onPick: (Person) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.find_title, asked)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    explain -> Text(stringResource(R.string.contacts_explain))
                    matches.isEmpty() -> Text(stringResource(R.string.no_contact_match, asked))
                    else -> matches.forEach { item ->
                        BigButton("${item.name}  ${displayPhone(item.phone)}", { onPick(item) }, kind = ButtonKind.Secondary)
                    }
                }
            }
        },
        confirmButton = {
            if (explain) {
                TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = { if (explain) TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

@Composable
private fun EditDialog(
    saved: Saved,
    onClose: () -> Unit,
    onPlace: (PlaceKind, String) -> Unit,
    onRemove: (String) -> Unit,
    onPick: () -> Unit,
) {
    var home by remember { mutableStateOf(saved.home.address) }
    var office by remember { mutableStateOf(saved.office.address) }
    Dialog(onDismissRequest = onClose) {
        DialogCard {
            Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.titleLarge)
            BigButton(stringResource(R.string.add_contact), onPick, kind = ButtonKind.Secondary)
            saved.people.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { onRemove(item.id) }) { Text(stringResource(R.string.remove)) }
                }
            }
            OutlinedTextField(home, { home = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.place_home)) })
            OutlinedTextField(office, { office = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.place_office_optional)) })
            BigButton(stringResource(R.string.save), {
                onPlace(PlaceKind.HOME, home)
                onPlace(PlaceKind.OFFICE, office)
                onClose()
            })
            BigButton(stringResource(R.string.close), onClose, kind = ButtonKind.Quiet)
        }
    }
}
