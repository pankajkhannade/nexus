package dev.pankajkhannade.nexus

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * S6 Listening. A big "Listening…", the words so far in large type as they come, a big Done and a Stop.
 * Pauses don't end it: Nexus keeps listening until Done, a whole command, a quiet spell or 60 seconds.
 * Says plainly whether speech stays on the phone or goes to the speech service.
 */
@Composable
fun ListeningScreen(model: NexusViewModel) {
    Centered {
        Text("🎤", fontSize = 72.sp)
        BigCentredText(stringResource(R.string.listening))
        LiveWords(model.heard)
        Text(
            stringResource(if (model.voiceMode == VoiceMode.ON_DEVICE) R.string.voice_on_device else R.string.voice_online),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton("✓  " + stringResource(R.string.listening_done), { model.doneListening() }, kind = ButtonKind.Call)
        BigButton(stringResource(R.string.stop), { model.cancelListening() }, kind = ButtonKind.Secondary)
    }
}

/** The words heard so far, large, read out by TalkBack as they change. A hint until the first word. */
@Composable
fun LiveWords(words: String) {
    OutlinedPanel(Modifier.testTag("listening_words")) {
        Text(
            if (words.isBlank()) stringResource(R.string.listening_hint) else "“$words”",
            style = MaterialTheme.typography.headlineMedium,
            color = if (words.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** S6 Confirm: shown when the name was a fuzzy match. Spoken aloud too. */
@Composable
fun ConfirmScreen(model: NexusViewModel, person: Person) {
    LaunchedEffect(person.id) { model.speak(R.string.confirm_call, person.name) }
    Centered {
        Avatar(person, model.saved.people, 120.dp)
        BigCentredText(stringResource(R.string.confirm_call, person.name))
        BigButton(stringResource(R.string.yes_call), { model.startCall(person) }, kind = ButtonKind.Call)
        BigButton(stringResource(R.string.no), { model.cancelled(CommandType.CALL); model.screen = Screen.Home }, kind = ButtonKind.Secondary)
    }
}

/**
 * S6 Not understood (UX sprint 1, fix #3). Never a dead end, and never a mystery: it shows what Nexus heard
 * ("ऐकलं: …", or "heard nothing"), one example of what to say, a big Speak again, and the people the words
 * may have been about first, each with a one-tap Call. It was already said aloud (heard + example).
 */
@Composable
fun NotUnderstoodScreen(model: NexusViewModel, onSpeak: () -> Unit) {
    val back = { model.tryingInSetup = false; model.screen = Screen.Home }
    ParentScaffold(onBack = back) {
        BigCentredText(stringResource(if (model.voiceFails >= 2) R.string.not_understood_again else R.string.not_understood))
        OutlinedPanel(Modifier.testTag("heard_line")) {
            Text(
                if (model.heard.isBlank()) stringResource(R.string.heard_nothing) else stringResource(R.string.heard_big, model.heard),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(stringResource(R.string.try_saying, stringResource(R.string.try_phrase, model.exampleName())), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BigButton("🎤  " + stringResource(R.string.speak_again), onSpeak)
        val people = model.candidatePeople()
        if (people.isNotEmpty()) {
            Text(stringResource(R.string.maybe_meant), style = MaterialTheme.typography.titleLarge)
            people.take(4).forEach { person ->
                OutlinedPanel {
                    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(person, model.saved.people, 56.dp)
                        Text(person.name, style = MaterialTheme.typography.titleLarge)
                    }
                    BigButton("📞 " + stringResource(R.string.found_call, person.name), {
                        model.tryingInSetup = false; model.tile(CommandType.CALL); model.startCall(person)
                    }, kind = ButtonKind.Call)
                }
            }
        }
    }
}

/** S7 Calling: a visible 3-second countdown with Stop, then the call is placed (or the dialer opens). */
@Composable
fun CallingScreen(model: NexusViewModel, person: Person) {
    val context = LocalContext.current
    var left by remember(person.id) { mutableIntStateOf(3) }
    LaunchedEffect(person.id) {
        model.speak(R.string.calling_x, person.name)
        while (left > 0) {
            delay(1000)
            left -= 1
        }
        model.record(Event.CALL)
        // Without direct calling, the dial pad opens: say which button to press before it does.
        if (!granted(context, android.Manifest.permission.CALL_PHONE)) model.speak(R.string.dialer_fallback)
        val direct = placeCall(context, person.phone)
        model.actionOpened(AppCategory.DIALER)
        Log.i("NexusCall", "placed direct=$direct")
        model.notice = if (direct) "" else context.getString(R.string.dialer_fallback)
        model.screen = Screen.Home
    }
    Centered {
        Avatar(person, model.saved.people, 120.dp)
        BigCentredText(stringResource(R.string.calling_x, person.name))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            CircularProgressIndicator(progress = { left / 3f }, modifier = Modifier.size(96.dp), strokeWidth = 8.dp)
            Text("$left", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        BigButton(stringResource(R.string.stop), { model.speaker.stop(); model.cancelled(CommandType.CALL); model.screen = Screen.Home }, kind = ButtonKind.Secondary)
    }
}

/** Phone book "Which one?": up to 3 big choices with the number's last digits, so two Rahuls differ. */
@Composable
fun ChooseContactScreen(model: NexusViewModel, choose: Screen.ChooseContact) {
    ParentScaffold(onBack = { model.cancelled(CommandType.FIND_CONTACT); model.screen = Screen.Home }) {
        BigCentredText(stringResource(R.string.which_one))
        Text(stringResource(R.string.heard_x, choose.name), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        choose.options.forEachIndexed { i, person -> ContactChoice(person, i) { model.chooseContact(person, choose.ask) } }
        BigButton(stringResource(R.string.none_of_these), { model.screen = Screen.ContactNotFound(choose.name) }, kind = ButtonKind.Secondary)
    }
}

/** One big tappable contact: initial, name, "Number ending 3210". */
@Composable
fun ContactChoice(person: Person, index: Int = 0, onClick: () -> Unit) {
    val ending = stringResource(R.string.number_ending, numberEnding(person.phone))
    OutlinedPanel(
        Modifier
            .heightIn(min = 96.dp)
            .clickable(onClickLabel = person.name, onClick = onClick)
            .semantics(mergeDescendants = true) { },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(person, emptyList(), 64.dp, fallbackIndex = index)
            Column {
                Text(person.name, style = MaterialTheme.typography.headlineSmall)
                Text(ending, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Phone book: nobody by that name. Never a dead end: the dial pad, speak again, or back. */
@Composable
fun ContactNotFoundScreen(model: NexusViewModel, name: String, onSpeak: () -> Unit) {
    val context = LocalContext.current
    ParentScaffold(onBack = { model.screen = Screen.Home }) {
        Text("📒", fontSize = 56.sp)
        BigCentredText(stringResource(R.string.contact_not_found, name))
        BigButton("📞  " + stringResource(R.string.open_dial_pad), { openDial(context, ""); model.actionOpened(AppCategory.DIALER); model.screen = Screen.Home }, kind = ButtonKind.Call)
        BigButton("🎤  " + stringResource(R.string.speak_again), onSpeak, kind = ButtonKind.Secondary)
    }
}

/** Phone book permission, asked at first need in the parent's language, before Android's own dialog. */
@Composable
fun ContactsPermissionScreen(model: NexusViewModel, ask: Screen.ContactsPermission, onAllow: () -> Unit) {
    LaunchedEffect(ask) { model.speak(R.string.contacts_why, ask.name) }
    PermissionCard(
        "📒", stringResource(R.string.contacts_title), stringResource(R.string.contacts_why, ask.name),
        stringResource(R.string.contacts_stays), onAllow = onAllow, onNotNow = { model.contactsAnswered(false) },
    )
}
