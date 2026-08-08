package com.dewijones92.primavista.ui.practice

import com.dewijones92.primavista.practice.RefusalReason
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/spec.md I3 says the app refuses **and says why**. A refusal that merely declines is the same
 * dead end as a wrong verdict, so the wording is asserted rather than left to the screen.
 */
class RefusalWordingTest {

    @Test
    fun `the polyphony refusal names the bar and the input that cannot hear it`() {
        val detail = refusalDetail(RefusalReason.PolyphonicScoreOnMonoInput(3, "mic"))

        assertTrue("the bar is not named: $detail", detail.contains("Bar 3"))
        assertTrue("the input is not named: $detail", detail.contains("mic"))
        assertTrue("no way out is offered: $detail", detail.contains("one hand at a time"))
    }

    @Test
    fun `an empty score is refused by name rather than shown as an empty staff`() {
        val reason = RefusalReason.EmptyScore("Study in C")

        assertTrue(refusalHeadline(reason).contains("Nothing to read"))
        assertTrue(refusalDetail(reason).contains("Study in C"))
    }
}
