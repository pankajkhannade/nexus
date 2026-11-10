package dev.pankajkhannade.nexus

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

fun needIcon(need: Need): String = when (need) {
    Need.GROCERY -> "🥛"
    Need.MEDICINE -> "💊"
    Need.DOCTOR -> "🩺"
    Need.RIDE -> "🛺"
    Need.HOME_SERVICE -> "🔧"
    Need.EMERGENCY -> "🚑"
}

@StringRes
fun needLabel(need: Need): Int = when (need) {
    Need.GROCERY -> R.string.need_grocery
    Need.MEDICINE -> R.string.need_medicine
    Need.DOCTOR -> R.string.need_doctor
    Need.RIDE -> R.string.need_ride
    Need.HOME_SERVICE -> R.string.need_home_service
    Need.EMERGENCY -> R.string.need_emergency
}

/** S15: up to 6 big need tiles in the parent's language, two per row. Not an app grid: the app name is a small line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearHomeScreen(model: NexusViewModel) {
    val saved = model.saved
    ParentScaffold(onBack = { model.screen = Screen.Home }) {
        Text("📍  " + stringResource(R.string.near_home), style = MaterialTheme.typography.headlineMedium)
        if (saved.pincode.isNotBlank()) {
            Text(stringResource(R.string.near_services_for, saved.pincode), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
            visibleNeeds(saved).forEach { need ->
                val app = serviceApp(need, model.pickedPackage(need))
                val sub = when (need) {
                    Need.EMERGENCY -> emergencyNumber(saved.countryCode)
                    Need.RIDE -> null
                    else -> app?.label ?: stringResource(R.string.near_on_maps)
                }
                NeedTile(Modifier.weight(1f).fillMaxRowHeight(), need, sub) { model.nearTile(need); model.openNeed(need) }
            }
        }
    }
}

@Composable
private fun NeedTile(modifier: Modifier, need: Need, sub: String?, onClick: () -> Unit) {
    val label = stringResource(needLabel(need))
    val danger = need == Need.EMERGENCY
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (danger) NexusColors.DangerContainer else NexusColors.SurfaceContainer,
        contentColor = if (danger) NexusColors.OnDangerContainer else NexusColors.OnSurface,
        modifier = modifier
            .heightIn(min = 148.dp)
            .border(CardBorder, CardShape)
            .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button },
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            Text(needIcon(need), fontSize = 44.sp, modifier = Modifier.clearAndSetSemantics { })
            Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** S15 Medicines / Doctor with a picked app: the app first, then nearby places on Maps. */
@Composable
fun NeedHelpScreen(model: NexusViewModel, need: Need) {
    val app = serviceApp(need, model.pickedPackage(need))
    ParentScaffold(onBack = { model.screen = Screen.NearHome }) {
        Text(needIcon(need) + "  " + stringResource(needLabel(need)), style = MaterialTheme.typography.headlineMedium)
        app?.let {
            val res = if (need == Need.DOCTOR) R.string.near_book_on else R.string.near_order_on
            BigButton(stringResource(res, it.label), { model.openNeedApp(need, it) })
        }
        BigButton(
            "🗺  " + stringResource(if (need == Need.DOCTOR) R.string.near_doctors else R.string.near_medical_stores),
            { model.openNeedMaps(need) },
            kind = ButtonKind.Secondary,
        )
        Text(stringResource(R.string.near_call_in_maps), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** S15: the picked app was removed. One big Install button to its Play Store page. */
@Composable
fun InstallScreen(model: NexusViewModel, need: Need, app: ServiceApp) {
    val context = LocalContext.current
    ParentScaffold(onBack = { model.screen = Screen.NearHome }) {
        Text(needIcon(need), fontSize = 56.sp, modifier = Modifier.clearAndSetSemantics { })
        Text(stringResource(R.string.install_title, app.label), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.install_body), style = MaterialTheme.typography.bodyLarge)
        BigButton(stringResource(R.string.install_x, app.label), { openPlay(context, app.pkg); model.playRedirect(AppCategory.of(need)); model.screen = Screen.Home })
        model.saved.people.firstOrNull { it.askFamily }?.let { person ->
            BigButton("💬  " + stringResource(R.string.ask_x, person.name), {
                model.record(Event.ASK_FAMILY)
                askFamilyByText(context, person.phone, context.getString(R.string.install_help_prefill, app.label))
            }, kind = ButtonKind.Secondary)
        }
    }
}

/** S15 Emergency: dial pad with 108 (never an automatic call), family calls, nearest hospital. */
@Composable
fun EmergencyScreen(model: NexusViewModel) {
    val context = LocalContext.current
    val number = emergencyNumber(model.saved.countryCode)
    ParentScaffold(onBack = { model.screen = Screen.NearHome }) {
        Text("🚑  " + stringResource(R.string.need_emergency), style = MaterialTheme.typography.headlineMedium)
        Surface(
            onClick = { openDial(context, number); model.actionOpened(AppCategory.EMERGENCY) },
            shape = CardShape,
            color = NexusColors.Danger,
            contentColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("📞  " + stringResource(R.string.emergency_call, number), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
        }
        Text(stringResource(R.string.emergency_dial_note, number), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.saved.people.isNotEmpty()) {
            Text(stringResource(R.string.emergency_family), style = MaterialTheme.typography.titleLarge)
            model.saved.people.forEach { person ->
                BigButton("📞 " + stringResource(R.string.found_call, person.name), { model.startCall(person) }, kind = ButtonKind.Call)
            }
        }
        BigButton("🏥  " + stringResource(R.string.emergency_hospital), { model.openNeedMaps(Need.EMERGENCY) }, kind = ButtonKind.Secondary)
    }
}

/** Bumps when the activity resumes, so "Installed" tags refresh after the child comes back from Play. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick += 1 }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

/**
 * Child-facing picker for one need: an exposed dropdown of the top apps, each tagged "Installed" or
 * with an "Install" action that opens the Play Store. "None" hides the tile (Doctor and Medicines fall back to Maps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicePicker(need: Need, picked: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val apps = appsFor(need)
    val installed = remember(tick) { apps.associate { it.pkg to isInstalled(context, it.pkg) } }
    var open by remember { mutableStateOf(false) }
    val current = apps.firstOrNull { it.pkg == picked }
    val installedTag = stringResource(R.string.tag_installed)
    val noneText = stringResource(R.string.service_none)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            OutlinedTextField(
                value = current?.let { it.label + if (installed[it.pkg] == true) " · $installedTag" else "" } ?: noneText,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(needIcon(need) + "  " + stringResource(needLabel(need))) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag("picker_${need.key}"),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                apps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { onPick(app.pkg); open = false },
                        trailingIcon = {
                            if (installed[app.pkg] == true) {
                                InstalledTag(Modifier.testTag("installed_${app.pkg}"))
                            } else {
                                TextButton({ openPlay(context, app.pkg) }, Modifier.testTag("install_${app.pkg}")) {
                                    Text(stringResource(R.string.action_install))
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
                DropdownMenuItem(text = { Text(noneText) }, onClick = { onPick(""); open = false }, modifier = Modifier.heightIn(min = 56.dp))
            }
        }
        if (current != null && installed[current.pkg] != true) {
            BigButton(stringResource(R.string.install_x, current.label), { openPlay(context, current.pkg) }, kind = ButtonKind.Secondary)
        }
    }
}

@Composable
private fun InstalledTag(modifier: Modifier = Modifier) {
    Surface(color = NexusColors.SuccessContainer, contentColor = NexusColors.OnSuccessContainer, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text("✓ " + stringResource(R.string.tag_installed), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** The PIN field and every need picker. Used by setup (S15 step) and Family settings. */
@Composable
fun NearHomePickers(pincode: String, onPincode: (String) -> Unit, pickedFor: (Need) -> String, onPick: (Need, String) -> Unit, showRide: Boolean) {
    OutlinedTextField(
        pincode, { v -> onPincode(v.filter { it.isDigit() }.take(6)) }, Modifier.fillMaxWidth().testTag("pincode"),
        label = { Text(stringResource(R.string.near_pincode)) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        supportingText = {
            Text(stringResource(if (cleanPincode(pincode) != null) R.string.near_pincode_note else R.string.near_pincode_missing))
        },
    )
    Need.PICKABLE.filter { showRide || it != Need.RIDE }.forEach { need ->
        ServicePicker(need, pickedFor(need)) { onPick(need, it) }
    }
}
