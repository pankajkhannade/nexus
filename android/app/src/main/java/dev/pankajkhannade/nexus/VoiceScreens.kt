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
        Avatar(person.name, 120.dp)
        BigCentredText(stringResource(R.string.confirm_call, person.name))
        BigButton(stringResource(R.string.yes_call), { model.startCall(person) }, kind = ButtonKind.Call)
        BigButton(stringResource(R.string.no), { model.cancelled(CommandType.CALL); model.screen = Screen.Home }, kind = ButtonKind.Secondary)
    }
}

/** S6 Not understood: never a dead end. Faces to tap, or speak again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotUnderstoodScreen(model: NexusViewModel, onSpeak: () -> Unit) {
    Centered {
        BigCentredText(stringResource(if (model.voiceFails >= 2) R.string.not_understood_again else R.string.not_understood))
        if (model.heard.isNotBlank()) Text(stringResource(R.string.heard_x, model.heard), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 2) {
            model.saved.people.forEach { person ->
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 140.dp)
                        .clickable { model.tryingInSetup = false; model.tile(CommandType.CALL); model.startCall(person) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(person.name, 88.dp)
                    Text(person.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        BigButton("🎤  " + stringResource(R.string.speak_again), onSpeak)
        BigButton(stringResource(R.string.back), { model.tryingInSetup = false; model.screen = Screen.Home }, kind = ButtonKind.Quiet)
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
        val direct = placeCall(context, person.phone)
        model.actionOpened(AppCategory.DIALER)
        Log.i("NexusCall", "placed direct=$direct")
        model.notice = if (direct) "" else context.getString(R.string.dialer_fallback)
        model.screen = Screen.Home
    }
    Centered {
        Avatar(person.name, 120.dp)
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
    ScreenColumn {
        BigCentredText(stringResource(R.string.which_one))
        Text(stringResource(R.string.heard_x, choose.name), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        choose.options.forEach { person -> ContactChoice(person) { model.chooseContact(person, choose.ask) } }
        BigButton(stringResource(R.string.none_of_these), { model.screen = Screen.ContactNotFound(choose.name) }, kind = ButtonKind.Secondary)
        BigButton(stringResource(R.string.back), { model.cancelled(CommandType.FIND_CONTACT); model.screen = Screen.Home }, kind = ButtonKind.Quiet)
    }
}

/** One big tappable contact: initial, name, "Number ending 3210". */
@Composable
fun ContactChoice(person: Person, onClick: () -> Unit) {
    val ending = stringResource(R.string.number_ending, numberEnding(person.phone))
    OutlinedPanel(
        Modifier
            .heightIn(min = 96.dp)
            .clickable(onClickLabel = person.name, onClick = onClick)
            .semantics(mergeDescendants = true) { },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(person.name, 64.dp)
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
    Centered {
        Text("📒", fontSize = 56.sp)
        BigCentredText(stringResource(R.string.contact_not_found, name))
        BigButton("📞  " + stringResource(R.string.open_dial_pad), { openDial(context, ""); model.actionOpened(AppCategory.DIALER); model.screen = Screen.Home }, kind = ButtonKind.Call)
        BigButton("🎤  " + stringResource(R.string.speak_again), onSpeak, kind = ButtonKind.Secondary)
        BigButton(stringResource(R.string.back), { model.screen = Screen.Home }, kind = ButtonKind.Quiet)
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
