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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * S5: the parent's daily screen, on one screen (UX sprint 1, fix #2). On a 360x640dp phone at default text
 * size it shows, without scrolling: up to 4 faces in a 2x2 grid each with its own Call, the labelled tiles
 * (Is this safe?, Go home, Near home, WhatsApp), and the sticky Speak bar. Bigger text scrolls, but Speak
 * never moves. The feedback card sits after the tiles, so it never pushes the family down. There is no
 * visible settings hint: the child holds the date for 2 seconds (taught on the handover screen).
 */
@Composable
fun ParentHome(model: NexusViewModel, onSpeak: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val saved = model.saved
    val hasWhatsApp = remember { isInstalled(context, "com.whatsapp") }
    var choosingWhatsApp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val date = LocalDate.ofEpochDay(model.today).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.greeting), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    HoldToOpen(date, onSettings)
                }
                if (choosingWhatsApp) {
                    Text(stringResource(R.string.whatsapp_who), style = MaterialTheme.typography.titleLarge)
                }
                FaceGrid(saved.people) { person ->
                    if (choosingWhatsApp) {
                        BigButton(stringResource(R.string.whatsapp), {
                            choosingWhatsApp = false
                            model.tile(CommandType.WHATSAPP); model.openWhatsAppFor(person)
                        }, Modifier.semantics { contentDescription = context.getString(R.string.found_message, person.name) }, kind = ButtonKind.Call)
                    } else {
                        BigButton("📞 " + stringResource(R.string.call), { model.tile(CommandType.CALL); model.startCall(person) },
                            Modifier.semantics { contentDescription = context.getString(R.string.found_call, person.name) }, kind = ButtonKind.Call)
                    }
                }
                if (choosingWhatsApp) {
                    BigButton(stringResource(R.string.back), { choosingWhatsApp = false }, kind = ButtonKind.Secondary)
                } else {
                    val tiles = buildList<Triple<String, String, () -> Unit>> {
                        add(Triple("🛡", context.getString(R.string.check_message)) { model.tile(CommandType.SAFETY); model.clearCheck(); model.screen = Screen.Safety })
                        if (saved.home.isSet) add(Triple("🚕", context.getString(R.string.go_home)) { model.tile(CommandType.RIDE_HOME); model.screen = Screen.Ride })
                        add(Triple("📍", context.getString(R.string.near_home)) { model.screen = Screen.NearHome })
                        if (hasWhatsApp && saved.people.isNotEmpty()) add(Triple("💬", context.getString(R.string.whatsapp)) { choosingWhatsApp = true })
                    }
                    TileGrid(tiles)
                }
                if (model.notice.isNotEmpty()) Text(model.notice, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("home_notice"))
                model.feedback.due(saved.installDay, model.today)?.let { round -> FeedbackCard(model, round) }
            }
        }
        SpeakBar(onSpeak)
    }
}

/** Up to 4 faces, two per row, each a card with a big face, the full first name and one action. */
@Composable
private fun FaceGrid(people: List<Person>, action: @Composable (Person) -> Unit) {
    people.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { person ->
                Surface(
                    shape = CardShape,
                    color = NexusColors.SurfaceContainer,
                    contentColor = NexusColors.OnSurface,
                    modifier = Modifier
                        .weight(1f)
                        .border(CardBorder, CardShape),
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(person, people, 48.dp)
                            Text(firstName(person.name), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        }
                        action(person)
                    }
                }
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

/** The person's first name in full ("Pankaj", not "P"): the face's label on small screens. */
fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { name.trim() }

/** Labelled tiles, two per row: an icon and always a word (never an icon alone). */
@Composable
private fun TileGrid(tiles: List<Triple<String, String, () -> Unit>>) {
    tiles.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (icon, label, onClick) ->
                OutlinedButtonTile(icon, label, onClick, Modifier.weight(1f).fillMaxHeight())
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun OutlinedButtonTile(icon: String, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = NexusColors.Background,
        contentColor = NexusColors.OnSurface,
        modifier = modifier
            .heightIn(min = 64.dp)
            .border(androidx.compose.foundation.BorderStroke(2.dp, NexusColors.Outline), CardShape),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics { })
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SpeakBar(onSpeak: () -> Unit) {
    Surface(color = NexusColors.Background, shadowElevation = 8.dp) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Surface(
                onClick = onSpeak,
                shape = CardShape,
                color = NexusColors.Primary,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .testTag("speak_bar"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎤  " + stringResource(R.string.speak), style = MaterialTheme.typography.displaySmall)
                }
            }
        }
    }
}

/**
 * The date in the corner. A 2-second press opens Family settings (the child learns this at handover); the
 * parent sees only a date, no "for family" hint. TalkBack users get it as a named action.
 */
@Composable
private fun HoldToOpen(date: String, onOpen: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.family_settings)
    Text(
        date,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("settings_hold")
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
            .padding(vertical = 10.dp),
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
    val placeName = trip.dropoff?.label?.ifBlank { null } ?: (target ?: home).address.substringBefore(',').trim()
    val open: (CabApp) -> Unit = { app ->
        model.rememberCab(app)
        model.record(Event.RIDE)
        // Coach before the handoff: either "it's filled in, check the fare" or the paste steps, aloud.
        val handoff = cabHandoff(app, trip)
        if (isInstalled(context, app.pkg)) {
            if (handoff.copyText != null) model.speak(R.string.coach_ride_paste, app.label)
            else model.speak(R.string.coach_ride_link, app.label, placeName)
        }
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
    val back = { model.cancelled(if (model.rideTo == null) CommandType.RIDE_HOME else CommandType.RIDE_TO); model.leaveRide() }
    ParentScaffold(onBack = back) {
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
                Text(stringResource(R.string.coach_ride_paste_note), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.ride_will_fill), style = MaterialTheme.typography.bodyLarge)
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
    }
}

/**
 * Where to? Up to 3 places for what the parent said, each with a big "Go here", read back aloud
 * ("दीनानाथ मंगेशकर हॉस्पिटल, एरंडवणे — इथे जायचं?"). When the map finds nothing, the words go to the cab app.
 */
@Composable
fun DestinationScreen(model: NexusViewModel) {
    val hits = model.destHits
    ParentScaffold(onBack = { model.cancelled(if (model.rideTo == null) CommandType.RIDE_HOME else CommandType.RIDE_TO); model.leaveRide() }) {
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
    }
}
