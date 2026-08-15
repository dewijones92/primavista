package com.dewijones92.primavista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.theme.PrimaVistaTheme
import com.dewijones92.primavista.ui.AppShell
import com.dewijones92.primavista.ui.Destination
import com.dewijones92.primavista.ui.diagnostics.DiagnosticsScreen
import com.dewijones92.primavista.ui.journey.JourneyRoute
import com.dewijones92.primavista.ui.onboarding.IntroEntry
import com.dewijones92.primavista.ui.onboarding.IntroGate
import com.dewijones92.primavista.ui.onboarding.IntroductionRoute
import com.dewijones92.primavista.ui.onboarding.rememberIntroGate
import com.dewijones92.primavista.ui.practice.PractiseRoute
import com.dewijones92.primavista.ui.progress.ProgressRoute
import com.dewijones92.primavista.ui.repertoire.RepertoireRoute

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PrimaVistaApp).container

        setContent {
            PrimaVistaTheme {
                PrimaVista(container)
            }
        }
    }
}

/**
 * The introduction sits **above** the shell rather than inside a tab, because while it is running
 * there is nothing else to do — and because the path re-enters it by either door, so it cannot be
 * something only a fresh install ever sees.
 */
@Composable
private fun PrimaVista(container: AppContainer) {
    val gate = rememberIntroGate(container)
    var introducing: IntroEntry? by remember { mutableStateOf(null) }
    var settled by remember { mutableStateOf(false) }

    val entry = introducing ?: IntroEntry.Beginning.takeIf { gate == IntroGate.Show && !settled }
    if (entry != null) {
        IntroductionRoute(
            container = container,
            entry = entry,
            onDone = {
                introducing = null
                settled = true
            },
        )
        return
    }
    if (gate == IntroGate.Reading) return

    AppShell(
        path = { open ->
            JourneyRoute(
                container = container,
                onIntroduction = { introducing = IntroEntry.Beginning },
                onPlacement = { introducing = IntroEntry.PlacementOnly },
                onDiagnostics = { open(Destination.Diagnostics) },
            )
        },
        practise = { PractiseRoute(container) },
        repertoire = { RepertoireRoute(container) },
        progress = { ProgressRoute(container) },
        diagnostics = { DiagnosticsScreen(container.diag) },
        modifier = Modifier,
    )
}
