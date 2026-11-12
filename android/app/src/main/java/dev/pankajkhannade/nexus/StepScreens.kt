package dev.pankajkhannade.nexus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Multi-step (2026-11-12): the next step of a plan, waiting on home after a handoff ("पुढचं (2/2): पंकजला मेसेज
 * करायचं?"). One big yes, and an easy skip or cancel-all. Nothing runs without the parent's tap.
 */
@Composable
fun NextStepCard(model: NexusViewModel, step: PlannedStep) {
    val plan = model.saved.plan ?: return
    OutlinedPanel(Modifier.testTag("next_step"), color = NexusColors.WarningContainer) {
        Text(stringResource(R.string.plan_title, plan.next + 1, plan.steps.size), style = MaterialTheme.typography.titleMedium)
        Text(model.stepLabel(step), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("✓  " + stringResource(R.string.plan_yes), { model.acceptNext() }, Modifier.weight(1.4f).testTag("next_step_yes"), kind = ButtonKind.Call)
            BigButton(stringResource(R.string.plan_skip), { model.skipNext() }, Modifier.weight(1f), kind = ButtonKind.Secondary)
        }
        BigButton("✕  " + stringResource(R.string.plan_cancel), { model.cancelPlan() }, Modifier.fillMaxWidth(), kind = ButtonKind.Quiet)
    }
}

/**
 * The guard said no: why, in plain words, and "📞 Pankaj ला विचारा" (the usual call countdown) instead.
 * The message itself is not shown again or kept.
 */
@Composable
fun BlockedScreen(model: NexusViewModel, reason: GuardReason) {
    val helper = model.helpers().firstOrNull()
    ParentScaffold(onBack = { model.leaveBlocked() }) {
        BigCentredText("⛔  " + stringResource(R.string.guard_title))
        OutlinedPanel(Modifier.testTag("guard_blocked"), color = NexusColors.DangerContainer) {
            Text(
                stringResource(
                    when (reason) {
                        GuardReason.LONG_NUMBER -> R.string.guard_card_body
                        GuardReason.LOCATION -> R.string.guard_location_body
                        else -> R.string.guard_secret_body
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (helper != null) {
            BigButton("📞  " + stringResource(R.string.guard_ask, shownName(helper)), { model.tile(CommandType.CALL); model.startCall(helper) }, Modifier.testTag("guard_ask"), kind = ButtonKind.Call)
        }
        BigButton(stringResource(R.string.guard_ok), { model.leaveBlocked() }, kind = ButtonKind.Secondary)
    }
}
