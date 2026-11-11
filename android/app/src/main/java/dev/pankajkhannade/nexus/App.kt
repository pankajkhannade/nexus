package dev.pankajkhannade.nexus

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat

@Composable
fun NexusApp(model: NexusViewModel) {
    val saved = model.saved
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
    }
    var micGate by remember { mutableStateOf(false) }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        model.contactsAnswered(ok)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micGate = false
        model.markMicAsked()
        model.permissionAnswered(Perm.MIC, ok)
        if (ok) model.startListening()
    }
    // Speak: if the mic is off, explain first (full screen), then ask. Never a bare system dialog.
    val onSpeak: () -> Unit = {
        if (granted(context, Manifest.permission.RECORD_AUDIO)) model.startListening() else micGate = true
    }
    val screen = model.screen
    val parentScreen = saved.setupDone && screen != Screen.Settings && screen != Screen.Metrics && screen != Screen.UploadConsent
    NexusTheme(mode = if (parentScreen) UiMode.Parent else UiMode.Child) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!model.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }
            BackHandler(enabled = !saved.setupDone && model.setupStep > 0) { model.setupStep -= 1 }
            BackHandler(enabled = saved.setupDone && (screen != Screen.Home || micGate)) {
                when {
                    micGate -> micGate = false
                    screen == Screen.Listening -> model.cancelListening()
                    screen == Screen.Privacy || screen == Screen.Metrics -> model.screen = Screen.Settings
                    screen == Screen.UploadConsent -> model.screen = Screen.Metrics
                    screen == Screen.Message -> model.cancelDraft()
                    screen is Screen.NeedHelp || screen is Screen.Install || screen == Screen.Emergency -> model.screen = Screen.NearHome
                    screen == Screen.Ride || screen == Screen.Destination -> model.leaveRide()
                    screen == Screen.MoneyGone -> model.leaveMoneyGone()
                    else -> { model.clearCheck(); model.tryingInSetup = false; model.screen = Screen.Home }
                }
            }
            Box(Modifier.systemBarsPadding()) {
              androidx.compose.runtime.CompositionLocalProvider(LocalOnSpeak provides onSpeak) {
                when {
                    !saved.setupDone -> SetupFlow(model)
                    micGate -> PermissionCard(
                        "🎤", stringResource(R.string.mic_title), stringResource(R.string.mic_why_parent),
                        stringResource(if (model.voiceMode == VoiceMode.ON_DEVICE) R.string.mic_on_device else R.string.mic_online),
                        onAllow = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onNotNow = { micGate = false },
                    )
                    screen is Screen.Listening -> ListeningScreen(model)
                    model.practiceIndex >= 0 -> PracticeScreen(model, onSpeak)
                    screen is Screen.Confirm -> ConfirmScreen(model, screen.person)
                    screen is Screen.NotUnderstood -> NotUnderstoodScreen(model, onSpeak)
                    screen is Screen.Calling -> CallingScreen(model, screen.person)
                    screen is Screen.ContactsPermission -> ContactsPermissionScreen(model, screen) {
                        contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                    screen is Screen.ChooseContact -> ChooseContactScreen(model, screen)
                    screen is Screen.ContactNotFound -> ContactNotFoundScreen(model, screen.name, onSpeak)
                    screen is Screen.Message -> MessageScreen(model)
                    model.tryingInSetup -> TryItScreen(model, onSpeak)
                    screen is Screen.Safety -> SafetyScreen(model)
                    screen is Screen.Help -> HelpScreen(model)
                    screen is Screen.MoneyGone -> MoneyGoneScreen(model)
                    screen is Screen.Ride -> RideScreen(model)
                    screen is Screen.Destination -> DestinationScreen(model)
                    screen is Screen.NearHome -> NearHomeScreen(model)
                    screen is Screen.NeedHelp -> NeedHelpScreen(model, screen.need)
                    screen is Screen.Install -> InstallScreen(model, screen.need, screen.app)
                    screen is Screen.Emergency -> EmergencyScreen(model)
                    screen is Screen.Settings -> SettingsScreen(model)
                    screen is Screen.Privacy -> PrivacyScreen(model) { model.screen = Screen.Settings }
                    screen is Screen.Metrics -> MetricsScreen(model)
                    screen is Screen.UploadConsent -> UploadConsentScreen(model)
                    else -> ParentHome(model, onSpeak, onSettings = { model.screen = Screen.Settings })
                }
              }
            }
        }
    }
}
