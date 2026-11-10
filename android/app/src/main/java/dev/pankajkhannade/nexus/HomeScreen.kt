package dev.pankajkhannade.nexus

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat

private const val SAMPLE =
    "Meet me at 21 MG Road, Flat 4, Bengaluru. Blue gate. Call me at 98765 43210 if you get lost."

@Composable
fun HomeScreen(model: NexusViewModel, onPrivacy: () -> Unit) {
    val saved = model.saved
    val context = LocalContext.current
    val reading = readMessage(model.draft, saved)
    var ride by remember { mutableStateOf<Pair<String, String>?>(null) }
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
        if (granted) matches = lookupContacts(context, asked) else {
            matches = emptyList()
            model.notice = "Contacts permission was not granted."
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPhone(context, uri)?.let { model.savePerson(it) }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Nexus", style = MaterialTheme.typography.headlineMedium)
            Row {
                TextButton(onClick = { model.setTheme(!saved.themeDark) }) {
                    Text(if (saved.themeDark) "Light" else "Dark")
                }
                TextButton(onClick = onPrivacy) { Text("Privacy") }
            }
        }
        Text(
            "One action at a time. You confirm the fare, the call, or the message in the other app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Where to", style = MaterialTheme.typography.titleLarge)
        Destination("Home", saved.home.ifBlank { "Add a home address" }) {
            if (saved.home.isBlank()) editing = true else ride = "Home" to saved.home
        }
        Destination("Office", saved.office.ifBlank { "Add an office address" }) {
            if (saved.office.isBlank()) editing = true else ride = "Office" to saved.office
        }

        Text("People", style = MaterialTheme.typography.titleLarge)
        if (saved.people.isEmpty()) {
            Text("No one saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        saved.people.forEach { item ->
            Destination(item.name, item.phone) { person = item }
        }
        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit people and places") }

        Text("Message", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = model.draft,
            onValueChange = { model.draft = it; model.notice = "" },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Call Aai, or pick me up at home") },
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    runCatching { speech.launch(intent) }.onFailure { model.notice = "Speech recognition is not available." }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Speak") }
            OutlinedButton(onClick = { model.draft = SAMPLE }, modifier = Modifier.weight(1f)) { Text("Sample") }
        }

        if (model.draft.isNotBlank()) {
            MessageCard(
                reading = reading,
                dial = saved.dial,
                onRide = { label, address -> ride = label to address },
                onPerson = { person = it },
                onFind = { asked ->
                    askContacts = asked
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    when {
                        granted -> {
                            explainContacts = false
                            matches = lookupContacts(context, asked)
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
            Text(model.notice, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    ride?.let { (label, address) ->
        RideDialog(
            label = label,
            address = address,
            last = saved.lastCab,
            onClose = { ride = null },
            onCab = { which ->
                ride = null
                model.rememberCab(which)
                model.notice = openCab(context, which, address).orEmpty()
            },
            onMaps = {
                ride = null
                openMaps(context, address)
            },
        )
    }
    person?.let { item ->
        PersonDialog(item, onClose = { person = null }) { action ->
            person = null
            when (action) {
                "call" -> openDial(context, item.phone)
                "text" -> openSms(context, item.phone, "On my way.")
                else -> model.notice = openWhatsApp(context, item.phone, "On my way.").orEmpty()
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
                val savedPerson = chosen.copy(phone = international(chosen.phone, saved.dial))
                model.savePerson(savedPerson)
                askContacts = null
                matches = null
                explainContacts = false
                person = savedPerson
            },
        )
    }
    if (!saved.tourDone) {
        Tour(onDone = model::finishTour)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Destination(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MessageCard(
    reading: Reading,
    dial: String,
    onRide: (String, String) -> Unit,
    onPerson: (Person) -> Unit,
    onFind: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Found", style = MaterialTheme.typography.titleMedium)
            Text(reading.place?.let { "${reading.placeLabel}: $it" } ?: "No place", color = MaterialTheme.colorScheme.onSurfaceVariant)
            reading.person?.let { item ->
                Text("${item.name} · ${international(item.phone, dial)}")
                Button(onClick = { onPerson(item) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (reading.ask == "text") "Message ${item.name}" else "Call ${item.name}")
                }
            }
            reading.askedName?.let { asked ->
                Text("No saved person named $asked.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onFind(asked) }, modifier = Modifier.fillMaxWidth()) { Text("Find $asked in contacts") }
            }
            reading.place?.let { place ->
                Button(onClick = { onRide(reading.placeLabel ?: "Ride", place) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ride to ${reading.placeLabel}")
                }
            }
        }
    }
}

@Composable
private fun RideDialog(
    label: String,
    address: String,
    last: String,
    onClose: () -> Unit,
    onCab: (String) -> Unit,
    onMaps: () -> Unit,
) {
    val order = listOf("uber" to "Uber", "ola" to "Ola", "rapido" to "Rapido")
        .sortedByDescending { it.first == last.ifBlank { "uber" } }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Text(address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Open one app. You confirm the fare there.")
                order.forEachIndexed { index, (id, name) ->
                    if (index == 0) {
                        Button(onClick = { onCab(id) }, modifier = Modifier.fillMaxWidth()) { Text("Open $name") }
                    } else {
                        OutlinedButton(onClick = { onCab(id) }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                    }
                }
                TextButton(onClick = onMaps, modifier = Modifier.fillMaxWidth()) { Text("Directions in Maps") }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun PersonDialog(person: Person, onClose: () -> Unit, onAction: (String) -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(person.name, style = MaterialTheme.typography.titleLarge)
                Text(person.phone, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onAction("call") }, modifier = Modifier.fillMaxWidth()) { Text("Call") }
                OutlinedButton(onClick = { onAction("whatsapp") }, modifier = Modifier.fillMaxWidth()) { Text("WhatsApp") }
                OutlinedButton(onClick = { onAction("text") }, modifier = Modifier.fillMaxWidth()) { Text("Text") }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
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
        title = { Text("Find $asked") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (explain) {
                    Text("Nexus looks through the contacts on this phone for that name. The list stays on the phone and is not uploaded.")
                } else if (matches.isEmpty()) {
                    Text("No contact matched $asked.")
                } else {
                    matches.forEach { item ->
                        TextButton(onClick = { onPick(item) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${item.name}  ${item.phone}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (explain) TextButton(onClick = onAllow) { Text("Continue") } else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = { if (explain) TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun Tour(onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf(
        "Home and Office" to "Tap one. Nexus opens only the cab app you choose, with that address filled in.",
        "People" to "Tap a person to call, text, or WhatsApp. The dialer still asks you to place the call.",
        "A message" to "Type “call Aai” or “pick me up at home”. Share text into Nexus from another app the same way.",
    )
    val (title, body) = steps[step]
    Dialog(onDismissRequest = onDone) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tour ${step + 1} of ${steps.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Skip") }
                    Button(
                        onClick = { if (step == steps.lastIndex) onDone() else step += 1 },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (step == steps.lastIndex) "Done" else "Next") }
                }
            }
        }
    }
}

@Composable
private fun EditDialog(
    saved: Saved,
    onClose: () -> Unit,
    onPlace: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onPick: () -> Unit,
) {
    var home by remember { mutableStateOf(saved.home) }
    var office by remember { mutableStateOf(saved.office) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("People and places", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Add a contact") }
                saved.people.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(item.id) }) { Text("Remove") }
                    }
                }
                OutlinedTextField(home, { home = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Home") })
                OutlinedTextField(office, { office = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Office") })
                Button(
                    onClick = {
                        onPlace("Home", home)
                        onPlace("Office", office)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

private fun readPhone(context: android.content.Context, uri: Uri): Person? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val name = cursor.getString(0).orEmpty()
        val phone = cursor.getString(1).orEmpty()
        if (name.isBlank() || phone.isBlank()) return null
        return Person(phone.filter { it.isDigit() }, name, phone)
    }
    return null
}
