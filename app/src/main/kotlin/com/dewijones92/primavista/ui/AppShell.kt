package com.dewijones92.primavista.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
public fun AppShell(
    practise: @Composable () -> Unit,
    repertoire: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    diagnostics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by rememberSaveable { mutableStateOf(Destination.Practise) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = { current = destination },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        modifier = Modifier.testTag("tab-${destination.name}"),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Destination.Practise -> practise()
                Destination.Repertoire -> repertoire()
                Destination.Progress -> progress()
                Destination.Diagnostics -> diagnostics()
            }
        }
    }
}
