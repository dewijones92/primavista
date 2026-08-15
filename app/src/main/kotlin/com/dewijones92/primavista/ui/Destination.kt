package com.dewijones92.primavista.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Six destinations and five tabs, so a navigation library would still be more moving parts than the
 * problem has. A sealed type plus one piece of saved state does the whole job.
 *
 * **The path is home.** It is where Dewi is on the journey and where the next session starts, so it
 * leads; Practise stays beside it for "just give me something to read", which is a different intent
 * and one he will still have. Diagnostics is [inBar] false deliberately: a sixth tab to fit a
 * developer tool would crowd the five things this app is actually for, and it is one tap from the
 * bottom of the path — where a "something is wrong" tool belongs.
 *
 * Declaration order is the on-screen order, and the shell reads each entry's ordinal to decide which
 * way a transition travels. See `.claude/CODE-NOTES.md`.
 */
public enum class Destination(
    public val label: String,
    public val icon: ImageVector,
    public val inBar: Boolean = true,
) {
    Path("Path", Icons.Rounded.Route),
    Practise("Practise", Icons.Rounded.MusicNote),
    Repertoire("Repertoire", Icons.Rounded.LibraryMusic),
    Progress("Progress", Icons.AutoMirrored.Rounded.TrendingUp),
    Settings("Settings", Icons.Rounded.Tune),
    Diagnostics("Diagnostics", Icons.Rounded.BugReport, inBar = false),
    ;

    public companion object {
        /** What the bottom bar shows, in order. */
        public val Tabs: List<Destination> = entries.filter { it.inBar }
    }
}
