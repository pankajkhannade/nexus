package dev.pankajkhannade.nexus

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(onDone: (Country, String, String, List<Person>) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var country by remember { mutableStateOf(COUNTRIES.first { it.code == "IN" }) }
    var home by remember { mutableStateOf("") }
    var office by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(List(5) { "" to "" }) }
    var note by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    Column(
        Modifier
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FIRST TIME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Set up Nexus", style = MaterialTheme.typography.headlineMedium)
        Text("Step ${step + 1} of 3. This stays on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (step) {
            0 -> CountryStep(country, note) { next, message ->
                country = next
                note = message
                if (message.isEmpty()) step = 1
            }
            1 -> PlaceStep(country.code, home, office, note, { home = it }, { office = it }) { message ->
                note = message
                if (message.isEmpty()) step = 2
            }
            else -> PeopleStep(country.dial, rows, note, { rows = it }, { note = it }) {
                val people = rows.mapNotNull { (name, phone) ->
                    if (name.isBlank() || phone.isBlank()) null
                    else Person(id = phone.filter { ch -> ch.isDigit() }, name = name.trim(), phone = international(phone, country.dial))
                }
                onDone(country, home, office, people)
            }
        }
        if (step > 0) {
            OutlinedButton(onClick = { note = ""; step -= 1 }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}

@Composable
private fun CountryStep(current: Country, note: String, onContinue: (Country, String) -> Unit) {
    var picked by remember { mutableStateOf(current) }
    var open by remember { mutableStateOf(false) }
    Text("Country of residence", style = MaterialTheme.typography.titleMedium)
    Text("Local numbers use this dial code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${picked.name} (${picked.dial})")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            COUNTRIES.forEach { item ->
                Text(
                    "${item.name} (${item.dial})",
                    modifier = Modifier
                        .clickable {
                            picked = item
                            open = false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
    if (note.isNotEmpty()) Text(note, color = MaterialTheme.colorScheme.error)
    Button(onClick = { onContinue(picked, "") }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}

@Composable
private fun PlaceStep(
    countryCode: String,
    home: String,
    office: String,
    note: String,
    onHome: (String) -> Unit,
    onOffice: (String) -> Unit,
    onContinue: (String) -> Unit,
) {
    PlaceBox("Home", home, countryCode, onHome)
    PlaceBox("Office", office, countryCode, onOffice)
    Text("Start typing a building, street, or area.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (note.isNotEmpty()) Text(note, color = MaterialTheme.colorScheme.error)
    Button(
        onClick = {
            val message = if (home.trim().length < 4 || office.trim().length < 4) "Add both addresses." else ""
            onContinue(message)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
}

@Composable
private fun PlaceBox(label: String, value: String, countryCode: String, onChange: (String) -> Unit) {
    var hits by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf("") }
    LaunchedEffect(value, countryCode) {
        val query = value.trim()
        if (query.length < 3 || query == picked) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        hits = withContext(Dispatchers.IO) {
            runCatching { suggestPlaces(query, countryCode) }.getOrDefault(emptyList())
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text("Search a place") },
        singleLine = true,
    )
    hits.forEach { place ->
        Text(
            place,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .clickable {
                    picked = place
                    hits = emptyList()
                    onChange(place)
                }
                .padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PeopleStep(
    dial: String,
    rows: List<Pair<String, String>>,
    note: String,
    onRows: (List<Pair<String, String>>) -> Unit,
    onNote: (String) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val person = readPickedPhone(context, uri) ?: return@rememberLauncherForActivityResult
        val next = rows.toMutableList()
        val index = next.indexOfFirst { it.first.isBlank() && it.second.isBlank() }.let { if (it == -1) 0 else it }
        next[index] = person.name to person.phone
        onRows(next)
        onNote("")
    }
    Text("Save at least one person. The other four are optional.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(
        onClick = {
            picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Choose from phone contacts") }
    rows.forEachIndexed { index, row ->
        val problem = phoneProblem(row.second, dial, false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.first,
                onValueChange = { value -> onRows(rows.mapIndexed { i, item -> if (i == index) value to item.second else item }) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (index == 0) "Name" else "Optional") },
                singleLine = true,
            )
            OutlinedTextField(
                value = row.second,
                onValueChange = { value -> onRows(rows.mapIndexed { i, item -> if (i == index) item.first to value else item }) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Phone") },
                singleLine = true,
            )
        }
        if (!problem.isNullOrEmpty()) Text(problem, color = MaterialTheme.colorScheme.error)
    }
    if (note.isNotEmpty()) Text(note, color = MaterialTheme.colorScheme.error)
    Button(
        onClick = {
            val problems = rows.map { (name, phone) ->
                when {
                    name.isBlank() && phone.isBlank() -> ""
                    name.isBlank() -> "Add a name for this number."
                    phone.isBlank() -> "Add a phone number."
                    else -> phoneProblem(phone, dial, true).orEmpty()
                }
            }
            val ready = rows.indices.any { rows[it].first.isNotBlank() && rows[it].second.isNotBlank() && problems[it].isEmpty() }
            val message = when {
                problems.any { it.isNotEmpty() } -> problems.first { it.isNotEmpty() }
                !ready -> "Save at least one person."
                else -> ""
            }
            onNote(message)
            if (message.isEmpty()) onDone()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) { Text("Save and continue") }
}

private fun readPickedPhone(context: android.content.Context, uri: Uri): Person? {
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
