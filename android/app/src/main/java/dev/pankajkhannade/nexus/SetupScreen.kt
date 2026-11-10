package dev.pankajkhannade.nexus

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MAX_PEOPLE = 4
private const val STEPS = 3

@Composable
fun SetupScreen(onDone: (Country, String, String, List<Person>) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var country by remember { mutableStateOf(COUNTRIES.first { it.code == "IN" }) }
    var home by remember { mutableStateOf("") }
    var office by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(List(MAX_PEOPLE) { "" to "" }) }
    var note by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.setup_step, step + 1, STEPS),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        when (step) {
            0 -> CountryStep(country) { country = it; step = 1 }
            1 -> PlaceStep(country.code, home, office, { home = it }, { office = it }) { step = 2 }
            else -> PeopleStep(country.dial, rows, note, { rows = it }, { note = it }) {
                val people = rows.mapNotNull { (name, phone) ->
                    val e164 = normalizePhone(phone, country.dial)
                    if (name.isBlank() || e164 == null) null else Person(phoneDigits(e164), name.trim(), e164)
                }
                onDone(country, home, office, people)
            }
        }
        if (step > 0) {
            BigButton(stringResource(R.string.back), { note = null; step -= 1 }, kind = ButtonKind.Secondary)
        }
    }
}

@Composable
private fun CountryStep(current: Country, onContinue: (Country) -> Unit) {
    var picked by remember { mutableStateOf(current) }
    var open by remember { mutableStateOf(false) }
    Text(stringResource(R.string.country_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.country_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column {
        BigButton(stringResource(R.string.country_item, picked.name, picked.dial), { open = true }, kind = ButtonKind.Secondary)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            COUNTRIES.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.country_item, item.name, item.dial)) },
                    onClick = { picked = item; open = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
    BigButton(stringResource(R.string.continue_), { onContinue(picked) })
}

@Composable
private fun PlaceStep(
    countryCode: String,
    home: String,
    office: String,
    onHome: (String) -> Unit,
    onOffice: (String) -> Unit,
    onContinue: () -> Unit,
) {
    PlaceBox(stringResource(R.string.place_home), home, countryCode, onHome)
    PlaceBox(stringResource(R.string.place_office_optional), office, countryCode, onOffice)
    Text(stringResource(R.string.place_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.place_search_network), color = MaterialTheme.colorScheme.onSurfaceVariant)
    BigButton(stringResource(R.string.continue_), onContinue)
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
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
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
private fun PeopleStep(
    dial: String,
    rows: List<Pair<String, String>>,
    note: Int?,
    onRows: (List<Pair<String, String>>) -> Unit,
    onNote: (Int?) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val (name, raw) = readPickedPhone(context, uri) ?: return@rememberLauncherForActivityResult
        val e164 = normalizePhone(raw, dial)
        if (e164 == null) {
            onNote(R.string.contact_number_unusable)
            return@rememberLauncherForActivityResult
        }
        val next = rows.toMutableList()
        val index = next.indexOfFirst { it.first.isBlank() && it.second.isBlank() }.let { if (it == -1) 0 else it }
        next[index] = name to displayPhone(e164)
        onRows(next)
        onNote(null)
    }
    Text(stringResource(R.string.people_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
    BigButton(
        stringResource(R.string.people_pick),
        { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
        kind = ButtonKind.Secondary,
    )
    rows.forEachIndexed { index, row ->
        val problem = phoneProblem(row.second, dial, strict = false)
        Text(
            stringResource(if (index == 0) R.string.person_n else R.string.person_n_optional, index + 1),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = row.first,
            onValueChange = { value -> onRows(rows.mapIndexed { i, item -> if (i == index) value to item.second else item }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = row.second,
            onValueChange = { value -> onRows(rows.mapIndexed { i, item -> if (i == index) item.first to value else item }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_phone)) },
            singleLine = true,
            isError = problem != null,
            supportingText = problem?.let { { Text(phoneProblemText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        )
    }
    note?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
    BigButton(stringResource(R.string.save_and_continue), {
        val problems: List<Int?> = rows.map { (name, phone) ->
            when {
                name.isBlank() && phone.isBlank() -> null
                name.isBlank() -> R.string.person_need_name
                phone.isBlank() -> R.string.person_need_phone
                else -> phoneProblem(phone, dial, strict = true)?.res()
            }
        }
        val ready = rows.indices.any { rows[it].first.isNotBlank() && rows[it].second.isNotBlank() && problems[it] == null }
        val message = problems.firstOrNull { it != null } ?: if (!ready) R.string.people_need_one else null
        onNote(message)
        if (message == null) onDone()
    })
}
