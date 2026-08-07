package com.dewijones92.primavista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import com.dewijones92.primavista.theme.PrimaVistaTheme
import com.dewijones92.primavista.ui.AppShell
import com.dewijones92.primavista.ui.diagnostics.DiagnosticsScreen
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
                AppShell(
                    practise = { PractiseRoute(container) },
                    repertoire = { RepertoireRoute(container) },
                    progress = { ProgressRoute(container) },
                    diagnostics = { DiagnosticsScreen(container.diag) },
                    modifier = Modifier,
                )
            }
        }
    }
}
