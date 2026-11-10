package dev.pankajkhannade.nexus

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Composable
fun NexusApp(model: NexusViewModel) {
    val saved = model.saved
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
    }
    // Setup is done by the child (child scale); the daily screen uses the larger parent scale.
    NexusTheme(mode = if (saved.setupDone) UiMode.Parent else UiMode.Child) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!model.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }
            var privacy by rememberSaveable { mutableStateOf(false) }
            Box(Modifier.systemBarsPadding()) {
                when {
                    !saved.setupDone -> SetupScreen(onDone = model::finishSetup)
                    privacy -> PrivacyScreen { privacy = false }
                    else -> HomeScreen(model, onPrivacy = { privacy = true })
                }
            }
        }
    }
}

@Composable
private fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyLarge)
        BigButton(stringResource(R.string.back), onBack, kind = ButtonKind.Secondary)
    }
}
