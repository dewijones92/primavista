package com.dewijones92.primavista.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dewijones92.primavista.PrimaVistaApp
import com.dewijones92.primavista.database.DatabaseOpening
import com.dewijones92.primavista.database.PracticeSettings
import com.dewijones92.primavista.database.RouteLatency
import com.dewijones92.primavista.database.StoredReading
import com.dewijones92.primavista.database.map
import com.dewijones92.primavista.di.AppContainer
import com.dewijones92.primavista.ui.UnreadablePanel
import kotlinx.coroutines.launch

/**
 * Reaches the container through the `Application` rather than being handed one, which is what lets
 * the Settings destination exist without editing `MainActivity`. See `.claude/CODE-NOTES.md`.
 */
@Composable
public fun SettingsRoute(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? PrimaVistaApp
    if (app == null) {
        Panel("Settings unavailable", "Settings need the running app; this is a preview.", modifier)
        return
    }
    SettingsRoute(app.container, modifier)
}

@Composable
public fun SettingsRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val store = container.settingsStore
    if (store == null) {
        val reason = (container.databaseOpening as? DatabaseOpening.Unreadable)?.reason
            ?: "the practice database could not be opened"
        UnreadablePanel("your settings", reason, modifier)
        return
    }
    val scope = rememberCoroutineScope()
    // Null until the row is read.
    val settings by remember(store) { store.observe() }
        .collectAsStateWithLifecycle<PracticeSettings?>(null)
    val latencies by produceState<StoredReading<List<RouteLatency>>?>(null, store) {
        value = store.latencies()
    }
    val sessions by produceState<StoredReading<SessionCount>?>(null, container) {
        value = container.sessionStore?.recent(HISTORY_PROBE_LIMIT)
            ?.map { SessionCount(it.size, capped = it.size >= HISTORY_PROBE_LIMIT) }
            ?: StoredReading.Unreadable(HISTORY, "the practice database could not be opened")
    }
    val stored = settings
    if (stored == null) {
        Panel(
            title = "Reading your settings…",
            detail = "Shown once they are read, so a control cannot save a default over them.",
            modifier = modifier,
        )
        return
    }
    SettingsScreen(
        settings = stored,
        latencies = latencies,
        storedSessions = sessions,
        onSettings = { scope.launch { store.save(it) } },
        modifier = modifier,
    )
}

@Composable
private fun Panel(title: String, detail: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(SCREEN_PADDING), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(GAP))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val HISTORY_PROBE_LIMIT = 200

internal const val HISTORY: String = "your practice history"

private val SCREEN_PADDING = 24.dp
private val GAP = 8.dp
