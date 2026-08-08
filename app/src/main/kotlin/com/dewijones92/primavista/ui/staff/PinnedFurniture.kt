package com.dewijones92.primavista.ui.staff

import com.dewijones92.primavista.notation.LaidOutGlyph
import com.dewijones92.primavista.notation.SmuflGlyph
import com.dewijones92.primavista.notation.StaffSpaces
import com.dewijones92.primavista.notation.StaffSystem
import com.dewijones92.primavista.score.Ticks

/**
 * One measure's clef, key and time signature, moved to the head of the system so they can be drawn
 * over the scrolling music instead of leaving with it.
 */
public class FurnitureGroup internal constructor(
    internal val start: Ticks,
    public val glyphs: List<LaidOutGlyph>,
    public val width: StaffSpaces,
)

/**
 * The furniture in force at any position, taken from the layout rather than re-engraved.
 *
 * See CODE-NOTES for why a group is *translated* rather than laid out again, and for the one thing
 * this cannot do.
 */
public class PinnedFurniture private constructor(private val groups: List<FurnitureGroup>) {

    /** The widest strip any group needs, so a reserved gutter cannot change width mid-piece. */
    public val gutter: StaffSpaces = StaffSpaces(groups.maxOfOrNull { it.width.value } ?: 0.0)

    public fun at(position: Ticks): FurnitureGroup? =
        groups.lastOrNull { it.start <= position } ?: groups.firstOrNull()

    public companion object {
        public fun of(system: StaffSystem): PinnedFurniture {
            val originX = system.measureAnchors.firstOrNull()?.x
                ?: return PinnedFurniture(emptyList())
            val brace = system.glyphs.filter { it.glyph == SmuflGlyph.Brace }
            return PinnedFurniture(
                system.measureAnchors.mapNotNull { anchor ->
                    val own = system.glyphs.filter {
                        it.glyph != SmuflGlyph.Brace && it.x >= anchor.x && it.x < anchor.noteAreaX
                    }
                    if (own.isEmpty()) return@mapNotNull null
                    val shift = originX - anchor.x
                    FurnitureGroup(
                        start = anchor.start,
                        glyphs = brace + own.map { it.copy(x = it.x + shift) },
                        width = originX + (anchor.noteAreaX - anchor.x),
                    )
                },
            )
        }
    }
}
