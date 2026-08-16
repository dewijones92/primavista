package com.dewijones92.primavista.score

private const val MARK = "#"
private const val SPAN = "+"
private const val PARTS = 2

/** Which window of which piece a passage is. */
public data class PassageRef(val parent: ScoreId, val fromIndex: Int, val bars: Int)

/**
 * The one place a passage's identity is spelled.
 *
 * It carries **indices**, not printed bar numbers, and that separation is the whole reason this
 * type exists. A passage has two numbers and they are different facts: where it starts in the
 * piece, and what the engraving prints above its first bar. Sixteen of the forty-one shipped songs
 * open on a pickup written `<measure number="0">`, so the two differ by one for every bar of those
 * pieces — and an id built from the printed number cannot be turned back into a window.
 *
 * The title says the printed bars, because that is what Dewi reads off the page in front of him.
 * The id says the indices, because that is what rebuilds the passage for a diagnostics report.
 */
public object PassageId {
    public fun of(parent: ScoreId, fromIndex: Int, bars: Int): ScoreId =
        ScoreId("${parent.value}$MARK$fromIndex$SPAN$bars")

    /** Null when this is an ordinary score rather than a passage of one. */
    public fun read(id: ScoreId): PassageRef? {
        val mark = id.value.lastIndexOf(MARK)
        if (mark <= 0) return null
        val span = id.value.substring(mark + 1).split(SPAN)
        val fromIndex = span.getOrNull(0)?.toIntOrNull()
        val bars = span.getOrNull(1)?.toIntOrNull()
        val readable = span.size == PARTS && fromIndex != null && bars != null && fromIndex >= 0 && bars > 0
        return if (readable) PassageRef(ScoreId(id.value.substring(0, mark)), fromIndex, bars) else null
    }
}
