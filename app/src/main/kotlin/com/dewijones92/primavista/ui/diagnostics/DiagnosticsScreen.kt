package com.dewijones92.primavista.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dewijones92.primavista.BuildConfig
import com.dewijones92.primavista.common.Diag

/**
 * The report screen. docs/spec.md I7 says every invariant must be settleable from a report alone,
 * and this is where one comes from.
 *
 * Sharing goes through the system share sheet, which is the only route available — the app has no
 * `INTERNET` permission (spec I6). That constraint is what makes it safe for the report to be as
 * detailed as it is: nothing can leave except by Dewi tapping share.
 */
@Composable
public fun DiagnosticsScreen(diag: Diag, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(diag.report(reportHeader())) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Build ${BuildConfig.GIT_SHA} · ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { share(context, report) }, modifier = Modifier.testTag("share-report")) {
                Text("Share report")
            }
            TextButton(onClick = { report = diag.report(reportHeader()) }) { Text("Refresh") }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = report,
            // Monospace and horizontally scrollable rather than wrapped: the report's whole value is
            // that its aligned key=value fields can be read months later, and soft-wrapping a
            // 120-column decision line into three ragged ones destroys exactly that.
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
                .testTag("report-body"),
        )
    }
}

/**
 * Identity travels with every report, because a finding is only as current as the build it came
 * from — a report with no version is a photograph with no date, and Totum spent a session acting on
 * five findings of which two were already fixed.
 */
private fun reportHeader(): Map<String, String> = mapOf(
    "build" to BuildConfig.GIT_SHA,
    "version" to BuildConfig.VERSION_NAME,
    "versionCode" to BuildConfig.VERSION_CODE.toString(),
    "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
    "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
)

private fun share(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "PrimaVista diagnostics ${BuildConfig.GIT_SHA}")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
}
