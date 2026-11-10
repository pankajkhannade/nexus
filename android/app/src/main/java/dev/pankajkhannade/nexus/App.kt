package dev.pankajkhannade.nexus

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Composable
fun NexusApp(model: NexusViewModel) {
    val saved = model.saved
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !saved.themeDark
    }
    NexusTheme(dark = saved.themeDark) {
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
    androidx.compose.foundation.layout.Column(Modifier.padding(20.dp)) {
        Text("Privacy", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Nexus stores your country, home, office, and the people you save on this phone. That data is not uploaded and is excluded from Android backup.\n\nPlace suggestions send the text you type to a map search service so the address can be filled in.\n\nContacts are read only after you agree, and only to find a name you asked to call. Nexus does not read the screens of Uber, Ola, or Rapido, and it cannot see their fares.",
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Back")
        }
    }
}
