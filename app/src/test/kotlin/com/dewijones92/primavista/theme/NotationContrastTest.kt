package com.dewijones92.primavista.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The note you are about to read must be the clearest mark on the staff.
 *
 * It was not. An upcoming notehead is drawn in `upcoming`, and in the dark palette that was
 * `#8B84A3` against staff lines of `#8A82A0` — the same colour to within a rounding error, so the
 * thing to read was exactly as visible as the ruling behind it. The light palette had the same
 * fault the other way up: the note was *lighter* than the lines, and so less prominent than them.
 *
 * This is the app's whole purpose expressed as a colour, which is why it is a test rather than an
 * eye judgement: everything else can be beautiful and the app still fails if the notes are the
 * hardest thing on the page to see.
 */
class NotationContrastTest {

    @Test
    fun `an upcoming note is more prominent than the staff lines behind it in both themes`() {
        for ((name, colors) in palettes) {
            val note = contrast(colors.upcoming, colors.paper)
            val lines = contrast(colors.staffLine, colors.paper)

            assertTrue(
                "$name: a note at $note is no clearer than its staff lines at $lines",
                note > lines * CLEARLY_MORE,
            )
        }
    }

    /** And it must be legible in absolute terms, not merely better than a faint line. */
    @Test
    fun `an upcoming note carries real contrast against the paper`() {
        for ((name, colors) in palettes) {
            assertTrue("$name", contrast(colors.upcoming, colors.paper) >= READABLE)
        }
    }

    /** Still short of a judged note, so a verdict landing is a change and not a repetition. */
    @Test
    fun `an upcoming note is not as strong as fully inked`() {
        for ((name, colors) in palettes) {
            assertTrue("$name", contrast(colors.upcoming, colors.paper) < contrast(colors.ink, colors.paper))
        }
    }

    /** A missed note is a verdict, so it must not be mistaken for one merely not reached yet. */
    @Test
    fun `missed and upcoming are told apart`() {
        for ((name, colors) in palettes) {
            val apart = abs(luminance(colors.missed) - luminance(colors.upcoming))

            assertTrue("$name: missed and upcoming differ by only $apart", apart > TELL_APART)
        }
    }

    private val palettes = listOf("dark" to DarkNotationColors, "light" to LightNotationColors)

    /** WCAG relative luminance, which is what "how visible is this" actually means. */
    private fun luminance(color: Color): Double =
        LUMA_R * channel(color.red) + LUMA_G * channel(color.green) + LUMA_B * channel(color.blue)

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= LINEAR_LIMIT) v / LINEAR_DIVISOR else Math.pow((v + OFFSET) / (1 + OFFSET), GAMMA)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (high, low) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (high + AMBIENT) / (low + AMBIENT)
    }

    private companion object {
        const val CLEARLY_MORE = 1.3
        const val READABLE = 3.0
        const val TELL_APART = 0.08
        const val LUMA_R = 0.2126
        const val LUMA_G = 0.7152
        const val LUMA_B = 0.0722
        const val LINEAR_LIMIT = 0.03928
        const val LINEAR_DIVISOR = 12.92
        const val OFFSET = 0.055
        const val GAMMA = 2.4
        const val AMBIENT = 0.05
    }
}
