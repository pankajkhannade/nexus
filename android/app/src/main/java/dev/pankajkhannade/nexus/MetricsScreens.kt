package dev.pankajkhannade.nexus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
private fun ratioText(r: Ratio): String {
    val pct = r.percent
    return when {
        r.den == 0 -> stringResource(R.string.metrics_none_yet)
        pct == null -> stringResource(R.string.metrics_ratio, r.num, r.den)
        else -> stringResource(R.string.metrics_ratio_pct, r.num, r.den, pct)
    }
}

@Composable
private fun yesNo(v: Boolean?): String = stringResource(
    when (v) {
        true -> R.string.metrics_yes
        false -> R.string.metrics_no
        null -> R.string.metrics_not_yet
    },
)

private fun langLabel(lang: Lang): Int = when (lang) {
    Lang.MR -> R.string.metrics_lang_mr
    Lang.HI -> R.string.metrics_lang_hi
    Lang.EN -> R.string.metrics_lang_en
    Lang.OTHER -> R.string.metrics_lang_other
}

private fun commandLabel(c: CommandType): Int = when (c) {
    CommandType.CALL, CommandType.FIND_CONTACT -> R.string.metrics_cmd_call
    CommandType.MESSAGE, CommandType.WHATSAPP -> R.string.metrics_cmd_message
    CommandType.RIDE_HOME, CommandType.RIDE_TO -> R.string.metrics_cmd_ride
    CommandType.NEAR_HOME -> R.string.metrics_cmd_near
    CommandType.SAFETY -> R.string.metrics_cmd_safety
    CommandType.VIDEO_CALL -> R.string.metrics_cmd_video
    CommandType.UNKNOWN -> R.string.metrics_cmd_other
}

private fun testerLabel(t: TesterSource): Int = when (t) {
    TesterSource.UNSET -> R.string.tester_unset
    TesterSource.FAMILY -> R.string.tester_family
    TesterSource.FRIEND -> R.string.tester_friend
    TesterSource.STRANGER -> R.string.tester_stranger
}

@Composable
private fun Card(title: String, lines: List<String>) {
    OutlinedPanel(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

/** How Nexus is doing (child scale, inside Family settings). Numbers in plain words, raw counts next to every %. */
@Composable
fun MetricsScreen(model: NexusViewModel) {
    val context = LocalContext.current
    val report = remember(model.metricsVersion) { model.metrics() }
    var confirmDelete by remember { mutableStateOf(false) }
    ScreenColumn {
        Text(stringResource(R.string.metrics_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.metrics_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            "📅  " + stringResource(R.string.metrics_use_title),
            listOf(
                stringResource(R.string.metrics_days_last7, report.daysUsedLast7),
                stringResource(R.string.metrics_active_days, report.activeDays),
                stringResource(R.string.metrics_retention, yesNo(report.d1), yesNo(report.d7), yesNo(report.d30)),
            ),
        )

        val firstTry = buildList {
            add(stringResource(R.string.metrics_first_try, ratioText(report.firstTry)))
            report.firstTryByLang.forEach { (lang, r) -> add("· " + stringResource(langLabel(lang)) + ": " + ratioText(r)) }
            report.firstTryByCommand.entries
                .groupBy({ commandLabel(it.key) }, { it.value })
                .forEach { (label, rs) -> add("· " + stringResource(label) + ": " + ratioText(Ratio(rs.sumOf { it.num }, rs.sumOf { it.den }))) }
            add(stringResource(R.string.metrics_not_understood, ratioText(report.notUnderstood)))
            add(stringResource(R.string.metrics_retries, ratioText(report.retries)))
            add(stringResource(R.string.metrics_cancels, ratioText(report.cancels)))
            add(stringResource(R.string.metrics_wrong, ratioText(report.wrongActions)))
            add(stringResource(R.string.metrics_pickers, report.pickers.num))
            add(stringResource(R.string.metrics_cutoffs, ratioText(report.cutOffs)))
        }
        Card("🎤  " + stringResource(R.string.metrics_voice_title), firstTry)

        val setup = report.setup
        Card(
            "🚀  " + stringResource(R.string.metrics_start_title),
            listOfNotNull(
                when {
                    setup.finished && setup.durationMs != null -> stringResource(R.string.metrics_setup_took, ((setup.durationMs + 59_999) / 60_000).toInt())
                    setup.finished -> stringResource(R.string.metrics_setup_done)
                    setup.started -> stringResource(R.string.metrics_setup_stopped, (setup.dropOffStep ?: 0) + 1)
                    else -> null
                },
                stringResource(R.string.metrics_activated, yesNo(report.activated)),
                report.timeToFirstValueMs?.let { stringResource(R.string.metrics_first_value, ((it + 59_999) / 60_000).toInt()) },
            ),
        )

        Card(
            "👆  " + stringResource(R.string.metrics_how_title),
            listOf(stringResource(R.string.metrics_voice_vs_tile, report.voiceTotal, report.tileTotal)) +
                report.voiceVsTile.entries.groupBy({ commandLabel(it.key) }, { it.value }).map { (label, ps) ->
                    "· " + stringResource(label) + ": " + stringResource(R.string.metrics_voice_vs_tile_line, ps.sumOf { it.first }, ps.sumOf { it.second })
                },
        )

        Card(
            "📱  " + stringResource(R.string.metrics_health_title),
            listOf(
                stringResource(R.string.metrics_health, report.sessions, report.crashes),
                stringResource(R.string.metrics_crash_free, ratioText(report.crashFree)),
            ),
        )

        Text(stringResource(R.string.tester_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.tester_why), color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(TesterSource.FAMILY, TesterSource.FRIEND, TesterSource.STRANGER).forEach { t ->
            val on = model.testerSource == t
            BigButton((if (on) "✓ " else "") + stringResource(testerLabel(t)), { model.chooseTesterSource(t) },
                kind = if (on) ButtonKind.Primary else ButtonKind.Secondary)
        }

        BigButton("📤  " + stringResource(R.string.metrics_share), { shareText(context, model.summary()) }, kind = ButtonKind.Secondary)
        Text(stringResource(R.string.metrics_share_note), color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.upload_toggle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = model.uploadOn,
                    onCheckedChange = { on -> if (on) model.screen = Screen.UploadConsent else model.setUploadConsent(false) },
                    modifier = Modifier.testTag("upload_switch"),
                )
            }
            Text(stringResource(if (model.uploadOn) R.string.upload_on_note else R.string.upload_off_note), style = MaterialTheme.typography.bodyLarge)
        }

        Card("✅  " + stringResource(R.string.collected_title), listOf(stringResource(R.string.collected_body)))
        Card("🚫  " + stringResource(R.string.never_title), listOf(stringResource(R.string.never_body)))

        BigButton("🗑  " + stringResource(R.string.delete_usage), { confirmDelete = true }, kind = ButtonKind.Quiet)
        BigButton(stringResource(R.string.back), { model.screen = Screen.Settings }, kind = ButtonKind.Secondary)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_usage_q)) },
            text = { Text(stringResource(R.string.delete_usage_body)) },
            confirmButton = { TextButton({ model.deleteUsageData(); confirmDelete = false }) { Text(stringResource(R.string.delete_usage_yes)) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.delete_usage_no)) } },
        )
    }
}

/** Consent for the opt-in daily upload. Off until the child says yes here; one tap turns it off again. */
@Composable
fun UploadConsentScreen(model: NexusViewModel) {
    ScreenColumn {
        Text("📤  " + stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.consent_intro), style = MaterialTheme.typography.bodyLarge)
        Card("✅  " + stringResource(R.string.consent_sent_title), listOf(stringResource(R.string.consent_sent)))
        Card("🚫  " + stringResource(R.string.never_title), listOf(stringResource(R.string.never_body)))
        Card("🌐  " + stringResource(R.string.consent_where_title), listOf(stringResource(R.string.consent_where)))
        BigButton(stringResource(R.string.consent_yes), { model.setUploadConsent(true); model.screen = Screen.Metrics })
        BigButton(stringResource(R.string.consent_no), { model.setUploadConsent(false); model.screen = Screen.Metrics }, kind = ButtonKind.Secondary)
    }
}
