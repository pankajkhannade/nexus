package dev.pankajkhannade.nexus

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A scrolling screen column with standard padding and gaps. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun OutlinedPanel(modifier: Modifier = Modifier, color: Color = NexusColors.SurfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = CardShape,
        color = color,
        contentColor = NexusColors.OnSurface,
        modifier = modifier
            .fillMaxWidth()
            .border(CardBorder, CardShape),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Full-screen permission explainer (design: permission pattern). Never an AlertDialog. */
@Composable
fun PermissionCard(icon: String, title: String, why: String, stays: String, onAllow: () -> Unit, onNotNow: () -> Unit) {
    ScreenColumn {
        Text(icon, fontSize = 56.sp, modifier = Modifier.clearAndSetSemantics { })
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(why, style = MaterialTheme.typography.bodyLarge)
        if (stays.isNotBlank()) Text(stays, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton(stringResource(R.string.allow), onAllow)
        BigButton(stringResource(R.string.not_now), onNotNow, kind = ButtonKind.Secondary)
    }
}

@StringRes
fun relationText(relation: String): Int = when (relation) {
    Relation.SON -> R.string.relation_son
    Relation.DAUGHTER -> R.string.relation_daughter
    Relation.SPOUSE -> R.string.relation_spouse
    else -> R.string.relation_other
}

@StringRes
fun verdictTitle(level: Level): Int = when (level) {
    Level.DANGER -> R.string.verdict_danger
    Level.CAREFUL -> R.string.verdict_careful
    Level.NONE -> R.string.verdict_none
}

@StringRes
fun reasonText(reason: Reason): Int = when (reason) {
    Reason.ASKS_OTP_PIN -> R.string.reason_otp
    Reason.UPI_COLLECT -> R.string.reason_upi_collect
    Reason.REMOTE_APP -> R.string.reason_remote_app
    Reason.APP_FILE -> R.string.reason_app_file
    Reason.LOOKALIKE_LINK -> R.string.reason_lookalike
    Reason.POLICE_THREAT -> R.string.reason_police
    Reason.KYC -> R.string.reason_kyc
    Reason.ACCOUNT_BLOCKED -> R.string.reason_blocked
    Reason.SHORT_LINK -> R.string.reason_short_link
    Reason.UNKNOWN_LINK -> R.string.reason_link
    Reason.PRIZE -> R.string.reason_prize
    Reason.JOB_TASK -> R.string.reason_job
    Reason.REFUND -> R.string.reason_refund
    Reason.NEW_NUMBER_MONEY -> R.string.reason_new_number
    Reason.URGENCY -> R.string.reason_urgency
}

/** Icon + word + colour, never colour alone. "No warning signs" is grey, never green. */
@Composable
fun VerdictBanner(level: Level) {
    val (container, ink, icon) = when (level) {
        Level.DANGER -> Triple(NexusColors.DangerContainer, NexusColors.OnDangerContainer, "⛔")
        Level.CAREFUL -> Triple(NexusColors.WarningContainer, NexusColors.OnWarningContainer, "⚠️")
        Level.NONE -> Triple(NexusColors.NeutralContainer, NexusColors.OnSurface, "ℹ️")
    }
    Surface(
        color = container,
        contentColor = ink,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(CardBorder, RoundedCornerShape(20.dp)),
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 32.sp, modifier = Modifier.clearAndSetSemantics { })
            Text(stringResource(verdictTitle(level)), style = MaterialTheme.typography.displaySmall, color = ink)
        }
    }
}

/** Big centred title used by the voice and calling screens. */
@Composable
fun BigCentredText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        content = content,
    )
}

/** What "बोला / Speak" does on any parent screen. NexusApp provides it (with the mic explainer); tests may too. */
val LocalOnSpeak = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Same place for the same thing (UX sprint 1, principle): every parent screen ends with one bar, मागे on the
 * left and बोला on the right, each at least 64dp tall, so Back is never a small link that moves around.
 * [above] is pinned just over it (the safe action on a scam verdict), outside the scrolling content.
 */
@Composable
fun ParentScaffold(onBack: () -> Unit, above: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val onSpeak = LocalOnSpeak.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Surface(color = NexusColors.Background, shadowElevation = 8.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                above?.invoke(this)
                Row(Modifier.fillMaxWidth().testTag("bottom_bar"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(stringResource(R.string.back), onBack, Modifier.weight(1f).heightIn(min = 64.dp), kind = ButtonKind.Secondary)
                    BigButton("🎤 " + stringResource(R.string.speak), onSpeak, Modifier.weight(1f).heightIn(min = 64.dp), kind = ButtonKind.Primary)
                }
            }
        }
    }
}
