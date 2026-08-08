package com.dewijones92.primavista.ui.staff

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * How one notehead should look this frame.
 *
 * [color] of null means the staff's ordinary ink. [scale] and [halo] carry a verdict *landing*;
 * neither may ever be used to interpolate between two verdict colours. See CODE-NOTES.
 */
@Immutable
public data class NoteAppearance(
    val color: Color? = null,
    val scale: Float = 1f,
    val halo: Float = 0f,
) {
    public companion object {
        public val PLAIN: NoteAppearance = NoteAppearance()
    }
}
