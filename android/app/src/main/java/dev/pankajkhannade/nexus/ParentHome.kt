package dev.pankajkhannade.nexus

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** S5: the parent's daily screen. Up to 4 people, Check a message, Go home, Near home (S15), and a sticky Speak bar. */
@Composable
fun ParentHome(model: NexusViewModel, onSpeak: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val saved = model.saved
    val hasWhatsApp = remember { isInstalled(context, "com.whatsapp") }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            ScreenColumn {
                val date = LocalDate.ofEpochDay(model.today).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.greeting), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    Text(date, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                model.feedback.due(saved.installDay, model.today)?.let { round -> FeedbackCard(model, round) }
                saved.people.forEach { person ->
                    PersonRow(person, hasWhatsApp, onCall = { model.tile(CommandType.CALL); model.startCall(person) }, onWhatsApp = { model.tile(CommandType.WHATSAPP); model.openWhatsAppFor(person) })
                }
                BigButton("🛡  " + stringResource(R.string.check_message), { model.tile(CommandType.SAFETY); model.clearCheck(); model.screen = Screen.Safety }, kind = ButtonKind.Secondary)
                if (saved.home.isSet) {
                    BigButton("🚕  " + stringResource(R.string.go_home), { model.tile(CommandType.RIDE_HOME); model.screen = Screen.Ride }, kind = ButtonKind.Secondary)
                }
                BigButton("📍  " + stringResource(R.string.near_home), { model.screen = Screen.NearHome }, kind = ButtonKind.Secondary)
                if (model.notice.isNotEmpty()) Text(model.notice, style = MaterialTheme.typography.bodyLarge)
                HoldToOpen(onSettings)
            }
        }
        SpeakBar(onSpeak)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonRow(person: Person, hasWhatsApp: Boolean, onCall: () -> Unit, onWhatsApp: () -> Unit) {
    val callLabel = stringResource(R.string.found_call, person.name)
    val waLabel = stringResource(R.string.found_message, person.name)
    OutlinedPanel(Modifier.heightIn(min = 112.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(person.name)
            Column {
                Text(person.name, style = MaterialTheme.typography.titleLarge)
                if (person.relation.isNotBlank()) Text(stringResource(relationText(person.relation)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
            BigButton("📞 " + stringResource(R.string.call), onCall, Modifier.weight(1f).semantics { contentDescription = callLabel }, kind = ButtonKind.Call)
            if (hasWhatsApp) {
                BigButton(stringResource(R.string.whatsapp), onWhatsApp, Modifier.weight(1f).semantics { contentDescription = waLabel }, kind = ButtonKind.Secondary)
            }
        }
    }
}

@Composable
private fun SpeakBar(onSpeak: () -> Unit) {
    Surface(color = NexusColors.Background, shadowElevation = 8.dp) {
        Box(Modifier.padding(16.dp)) {
            Surface(
                onClick = onSpeak,
                shape = CardShape,
                color = NexusColors.Primary,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎤  " + stringResource(R.string.speak), style = MaterialTheme.typography.displaySmall)
                }
            }
        }
    }
}

/** "For family: hold 2 seconds for settings". A 2-second press opens Family settings; TalkBack gets an action. */
@Composable
private fun HoldToOpen(onOpen: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.family_settings)
    Text(
        stringResource(R.string.settings_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { customActions = listOf(CustomAccessibilityAction(label) { onOpen(); true }) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val released = withTimeoutOrNull(2000) { waitForUpOrCancellation() }
                    if (released == null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpen()
                    }
                }
            }
            .padding(vertical = 16.dp),
    )
}

/** S12: day-3 / day-7 card, inline above the people. Q1 three buttons; Q2 optional text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackCard(model: NexusViewModel, round: Int) {
    val answered = model.feedback.answers[round]?.helped != null
    var easier by remember(round) { mutableStateOf("") }
    OutlinedPanel(color = NexusColors.WarningContainer) {
        if (!answered) {
            Text(stringResource(R.string.feedback_q1), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Helped.YES to R.string.fb_yes, Helped.LITTLE to R.string.fb_little, Helped.NO to R.string.fb_no).forEach { (h, res) ->
                    BigButton(stringResource(res), { model.answerFeedback(round, h) }, Modifier.weight(1f).heightIn(min = 72.dp), kind = ButtonKind.Primary)
                }
            }
            BigButton(stringResource(R.string.skip), { model.skipFeedback(round) }, kind = ButtonKind.Quiet)
        } else {
            Text(stringResource(R.string.feedback_q2), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                easier, { easier = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.feedback_q2_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (granted(LocalContext.current, Manifest.permission.RECORD_AUDIO)) {
                if (model.feedbackListening) {
                    BigButton("✓  " + stringResource(R.string.listening_done), { model.doneFeedbackSpeaking() }, kind = ButtonKind.Secondary)
                } else {
                    BigButton("🎤  " + stringResource(R.string.speak_answer), { model.speakFeedback { easier = it } }, kind = ButtonKind.Secondary)
                }
            }
            BigButton(stringResource(R.string.save), { model.easierFeedback(round, easier) }, enabled = easier.isNotBlank())
            BigButton(stringResource(R.string.skip), { model.finishFeedback(round) }, kind = ButtonKind.Quiet)
        }
    }
}

/**
 * S10: book a ride home, or to a place the parent said or typed. One primary button for the last-used
 * cab app, then "Other app" with the remaining installed ones, and "Somewhere else" to type a place.
 * Pickup is where the parent is now (the cab app's GPS). The parent always checks the fare and confirms
 * inside the cab app. No Maps button unless no cab app is installed (then Maps directions for a place).
 */
@Composable
fun RideScreen(model: NexusViewModel) {
    val context = LocalContext.current
    val installed = remember { CabApp.entries.filter { isInstalled(context, it.pkg) } }
    val apps = rideOrder(installed, model.saved.lastCab)
    val target = model.rideTo
    val home = model.saved.home
    var typing by remember(target) { mutableStateOf(target == null && !home.isSet) }
    var typed by remember { mutableStateOf("") }
    val trip = when {
        target != null -> Trip(dropoff = Stop(PlaceKind.ADDRESS, target, label = target.address.substringBefore(',').trim()))
        else -> Trip(dropoff = Stop(PlaceKind.HOME, home))
    }
    val hasPlace = target != null || home.isSet
    val open: (CabApp) -> Unit = { app ->
        model.rememberCab(app)
        model.record(Event.RIDE)
        when (openRide(context, app, trip)) {
            RideResult.OPENED_AND_COPIED -> {
                model.actionOpened(AppCategory.CAB)
                android.widget.Toast.makeText(context, R.string.address_copied, android.widget.Toast.LENGTH_LONG).show()
            }
            RideResult.OPENED -> model.actionOpened(AppCategory.CAB)
            RideResult.NOT_INSTALLED_OPENED_MAPS -> model.actionOpened(AppCategory.MAPS)
            RideResult.NOTHING_TO_OPEN -> Unit
        }
        model.leaveRide()
    }
    ScreenColumn {
        Text(
            "🚕  " + if (target != null) stringResource(R.string.go_to_x, target.address.substringBefore(',').trim()) else stringResource(R.string.go_home),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (hasPlace) {
            OutlinedPanel {
                Text(stringResource(if (target != null) R.string.place_destination else R.string.place_home), style = MaterialTheme.typography.titleMedium)
                Text((target ?: home).address, style = MaterialTheme.typography.bodyLarge)
                if (target != null) Text(stringResource(R.string.pickup_here), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasPlace && apps.isEmpty()) {
            OutlinedPanel(color = NexusColors.WarningContainer) {
                Text(stringResource(R.string.no_cab_app), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.no_cab_app_ask), style = MaterialTheme.typography.bodyLarge)
            }
            model.saved.people.firstOrNull { it.askFamily }?.let { person ->
                BigButton("💬  " + stringResource(R.string.ask_x, person.name), {
                    model.record(Event.ASK_FAMILY)
                    val text = if (target != null) context.getString(R.string.ride_help_prefill_dest, target.address) else context.getString(R.string.ride_help_prefill)
                    askFamilyByText(context, person.phone, text)
                })
            }
            if (target != null) {
                BigButton("🗺  " + stringResource(R.string.ride_maps_directions), { model.record(Event.RIDE); if (openMaps(context, trip)) model.actionOpened(AppCategory.MAPS); model.leaveRide() }, kind = ButtonKind.Secondary)
            }
        } else if (hasPlace) {
            BigButton(stringResource(R.string.ride_open_app, apps.first().label), { open(apps.first()) })
            if (cabHandoff(apps.first(), trip).copyText != null) {
                Text(stringResource(if (target != null) R.string.ride_will_copy_dest else R.string.ride_will_copy), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.ride_confirm_in_app), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (apps.size > 1) {
                Text(stringResource(R.string.ride_other_app), style = MaterialTheme.typography.titleLarge)
                apps.drop(1).forEach { app -> BigButton(app.label, { open(app) }, kind = ButtonKind.Secondary) }
            }
        }
        if (typing) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.somewhere_label)) },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton(stringResource(R.string.find_place), { model.findDestination(typed) }, enabled = typed.isNotBlank())
        } else {
            BigButton(stringResource(R.string.somewhere_else), { typing = true }, kind = ButtonKind.Secondary)
        }
        if (target != null && home.isSet) {
            BigButton(stringResource(R.string.go_home), { model.rideHome() }, kind = ButtonKind.Quiet)
        }
        BigButton(stringResource(R.string.back), { model.cancelled(if (model.rideTo == null) CommandType.RIDE_HOME else CommandType.RIDE_TO); model.leaveRide() }, kind = ButtonKind.Quiet)
    }
}

/**
 * Where to? Up to 3 places for what the parent said, each with a big "Go here", read back aloud
 * ("दीनानाथ मंगेशकर हॉस्पिटल, एरंडवणे — इथे जायचं?"). When the map finds nothing, the words go to the cab app.
 */
@Composable
fun DestinationScreen(model: NexusViewModel) {
    val hits = model.destHits
    ScreenColumn {
        Text("📍  " + stringResource(R.string.dest_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.heard_x, model.destQuery), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            hits == null -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.dest_searching, model.destQuery), style = MaterialTheme.typography.titleLarge)
            }
            hits.isEmpty() -> {
                Text(stringResource(R.string.dest_not_found, model.destQuery), style = MaterialTheme.typography.titleLarge)
                BigButton(stringResource(R.string.dest_use_words, model.destQuery), { model.useSpokenDestination() })
            }
            else -> hits.forEachIndexed { i, place ->
                OutlinedPanel {
                    Text(place.address, style = MaterialTheme.typography.headlineSmall)
                    BigButton(stringResource(R.string.dest_go_here), { model.chooseDestination(place) }, kind = if (i == 0) ButtonKind.Call else ButtonKind.Secondary)
                }
            }
        }
        if (hits != null && hits.isNotEmpty()) {
            BigButton(stringResource(R.string.dest_use_words, model.destQuery), { model.useSpokenDestination() }, kind = ButtonKind.Quiet)
        }
        BigButton(stringResource(R.string.back), { model.cancelled(if (model.rideTo == null) CommandType.RIDE_HOME else CommandType.RIDE_TO); model.leaveRide() }, kind = ButtonKind.Quiet)
    }
}
