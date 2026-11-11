package dev.pankajkhannade.nexus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** India's National Cyber Crime Reporting Portal and its helpline (both run by the Ministry of Home Affairs). */
const val CYBER_HELPLINE = "1930"
const val CYBER_PORTAL = "https://cybercrime.gov.in"

/**
 * UX sprint 2, principle 12 (the child is the support channel): help, one tap from home. For each helper a big
 * Call (through the usual countdown) and a WhatsApp / SMS with "I need help with Nexus" typed in. The parent
 * still presses Send in WhatsApp; Nexus never sends anything by itself (no SEND_SMS).
 */
@Composable
fun HelpScreen(model: NexusViewModel) {
    val context = LocalContext.current
    ParentScaffold(onBack = { model.screen = Screen.Home }) {
        BigCentredText("🙋  " + stringResource(R.string.help_title))
        Text(stringResource(R.string.help_body), style = MaterialTheme.typography.bodyLarge)
        model.helpers().forEach { person ->
            val name = shownName(person)
            OutlinedPanel {
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(person, model.saved.people, 56.dp)
                    Text(name, style = MaterialTheme.typography.titleLarge)
                }
                BigButton("📞  " + stringResource(R.string.found_call, name), { model.tile(CommandType.CALL); model.startCall(person) }, kind = ButtonKind.Call)
                BigButton("💬  " + stringResource(R.string.help_message_x, name), {
                    model.record(Event.ASK_FAMILY)
                    if (isInstalled(context, "com.whatsapp")) model.speak(R.string.coach_whatsapp_send) else model.speak(R.string.coach_sms_send)
                    if (askFamilyByText(context, person.phone, context.getString(R.string.help_prefill))) {
                        model.actionOpened(if (isInstalled(context, "com.whatsapp")) AppCategory.WHATSAPP else AppCategory.SMS)
                        model.screen = Screen.Home
                    }
                }, kind = ButtonKind.Secondary)
            }
        }
        BigButton("⚠️  " + stringResource(R.string.money_gone), { model.openMoneyGone() }, kind = ButtonKind.Quiet)
    }
}

/**
 * UX sprint 2, principles 1 and 2: back from a call, ride, message or map, home asks "did it work?" with two big
 * answers. Yes says a kind line; No opens Help. The answer is logged as yes / no plus the app kind, nothing else.
 */
@Composable
fun ReturnCheckCard(model: NexusViewModel, category: AppCategory) {
    OutlinedPanel(Modifier.testTag("return_check"), color = NexusColors.WarningContainer) {
        Text(stringResource(model.returnQuestion(category)), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("✓  " + stringResource(R.string.check_yes), { model.answerReturnCheck(true) }, Modifier.weight(1f), kind = ButtonKind.Call)
            BigButton("✕  " + stringResource(R.string.check_no), { model.answerReturnCheck(false) }, Modifier.weight(1f), kind = ButtonKind.Secondary)
        }
    }
}

/**
 * UX sprint 2, roadmap N3 "पैसे गेले": money already gone. Calm, numbered steps, each with one tap, in the order
 * that saves the most money: 1930 first (the earlier the call, the more can be frozen), then the bank, then the
 * cybercrime.gov.in complaint, then tell the child. Dial pads only (never an automatic call), no new permissions.
 */
@Composable
fun MoneyGoneScreen(model: NexusViewModel) {
    val context = LocalContext.current
    val helper = model.helpers().firstOrNull()
    ParentScaffold(onBack = { model.leaveMoneyGone() }) {
        BigCentredText(stringResource(R.string.money_gone_title))
        Text(stringResource(R.string.money_gone_calm), style = MaterialTheme.typography.bodyLarge)
        Step(1, R.string.money_step_1930_title, R.string.money_step_1930_body) {
            BigButton("📞  " + stringResource(R.string.money_call_1930), {
                model.speak(R.string.dialer_fallback)
                openDial(context, CYBER_HELPLINE)
                model.actionOpened(AppCategory.DIALER)
            }, Modifier.testTag("money_1930"), kind = ButtonKind.Call)
        }
        Step(2, R.string.money_step_bank_title, R.string.money_step_bank_body) {
            BigButton("📞  " + stringResource(R.string.open_dial_pad), {
                model.speak(R.string.money_bank_tts)
                openDial(context, "")
                model.actionOpened(AppCategory.DIALER)
            }, kind = ButtonKind.Secondary)
        }
        Step(3, R.string.money_step_portal_title, R.string.money_step_portal_body) {
            BigButton("🌐  " + stringResource(R.string.money_open_portal), {
                if (openWeb(context, CYBER_PORTAL)) model.actionOpened(AppCategory.BROWSER)
            }, kind = ButtonKind.Secondary)
        }
        if (helper != null) {
            val name = shownName(helper)
            Step(4, R.string.money_step_tell_title, R.string.money_step_tell_body, name) {
                BigButton("📞  " + stringResource(R.string.found_call, name), { model.tile(CommandType.CALL); model.startCall(helper) }, kind = ButtonKind.Secondary)
                BigButton("💬  " + stringResource(R.string.help_message_x, name), {
                    model.record(Event.ASK_FAMILY)
                    if (askFamilyByText(context, helper.phone, context.getString(R.string.money_prefill))) {
                        model.actionOpened(if (isInstalled(context, "com.whatsapp")) AppCategory.WHATSAPP else AppCategory.SMS)
                    }
                }, kind = ButtonKind.Secondary)
            }
        }
        OutlinedPanel(color = NexusColors.DangerContainer) {
            Text(stringResource(R.string.money_never_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.money_never_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Step(n: Int, title: Int, body: Int, arg: String? = null, actions: @Composable () -> Unit) {
    OutlinedPanel(Modifier.testTag("money_step_$n")) {
        Text("$n.  " + (if (arg != null) stringResource(title, arg) else stringResource(title)), style = MaterialTheme.typography.titleLarge)
        Text(if (arg != null) stringResource(body, arg) else stringResource(body), style = MaterialTheme.typography.bodyLarge)
        actions()
    }
}
