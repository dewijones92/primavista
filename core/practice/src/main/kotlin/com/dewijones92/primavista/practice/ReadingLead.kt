package com.dewijones92.primavista.practice

import com.dewijones92.primavista.score.Ticks
import com.dewijones92.primavista.score.TimeSignature

/**
 * How far ahead of the playhead the page is covered — the difference between reading music and
 * decoding it.
 *
 * A decoder looks at the note they are playing. A reader has already looked at it and is looking
 * further on, which is why fluent sight-readers can keep going through a page they have never seen.
 * The oldest exercise for it is a card held over the music at the point of playing, so the notes
 * are gone by the time you need them and you must have taken them in earlier.
 *
 * This is that card, expressed as a musical distance rather than a screen distance: a beat is a
 * beat whatever the tempo, whatever the zoom and whatever the piece. [Off] is the default, because
 * it is a drill rather than a way to read, and it is punishing before the notes are familiar.
 */
@JvmInline
public value class ReadingLead(public val beats: Int) {
    init {
        require(beats >= 0) { "a lead of $beats beats would cover music already played" }
    }

    public val isOn: Boolean get() = beats > 0

    /**
     * Where the cover starts: everything at or behind this musical position is hidden, so a note is
     * gone once it comes within [beats] of the playhead.
     *
     * Deliberately *not* clamped to the start of the piece. During the count-in [position] is
     * negative, so the cover sits at or before bar one and the opening is readable — which is the
     * whole point of a count-in — and it slides onto the page exactly as the music starts.
     */
    public fun coversUpTo(position: Ticks, time: TimeSignature): Ticks =
        position + Ticks(beats.toLong() * beatTicks(time).value)

    private fun beatTicks(time: TimeSignature): Ticks = Ticks(time.measureTicks.value / time.beats)

    override fun toString(): String = if (isOn) "$beats beats ahead" else "off"

    public companion object {
        public val Off: ReadingLead = ReadingLead(0)

        /** One beat: the notes vanish as they arrive. The gentlest version, and where to start. */
        public val OneBeat: ReadingLead = ReadingLead(1)

        /**
         * A bar of common time — the classic card-over-the-music drill on paper, and **not
         * offered**.
         *
         * Verified on the api35 emulator rather than assumed: a phone viewport holds roughly a bar
         * and a half of a generated exercise, so a bar of lead covers all of it and the page goes
         * blank. The exercise is real; the screen is too small for it. Kept as a named value
         * because the arithmetic is worth testing and a tablet may yet earn it.
         */
        public val OneBar: ReadingLead = ReadingLead(4)

        /** What the practice screen offers, easiest first. See [OneBar] for why it stops at two. */
        public val choices: List<ReadingLead> = listOf(Off, OneBeat, ReadingLead(2))
    }
}
