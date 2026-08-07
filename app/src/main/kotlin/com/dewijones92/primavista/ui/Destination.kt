package com.dewijones92.primavista.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Four destinations, so a navigation library would be more moving parts than the problem has. A
 * sealed type plus one piece of saved state does the whole job and cannot get into an invalid state.
 * If deep links or a back stack per tab ever become real requirements, that is the point to revisit.
 */
public enum class Destination(public val label: String, public val icon: ImageVector) {
    Practise("Practise", Icons.Rounded.MusicNote),
    Repertoire("Repertoire", Icons.Rounded.LibraryMusic),
    Progress("Progress", Icons.Rounded.TrendingUp),
    Diagnostics("Diagnostics", Icons.Rounded.BugReport),
}
