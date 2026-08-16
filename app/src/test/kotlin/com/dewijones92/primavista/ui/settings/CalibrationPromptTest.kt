package com.dewijones92.primavista.ui.settings

import com.dewijones92.primavista.audio.InputLatencyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIRM_CONFIDENCE = 0.92
private const val ROUGH_CONFIDENCE = 0.6
private const val SMEARED_CONFIDENCE = 0.2
private const val MEASURED_MILLIS = 41.5

/**
 * The button and the sentence under it come from one place, so they cannot disagree. A live
 * "Measure it" beside "the microphone is off" is exactly the kind of thing that ships.
 */
class CalibrationPromptTest {

    @Test
    fun `without the microphone the button is dead and the sentence explains why`() {
        val prompt = calibrationPrompt(Calibration.Idle, micGranted = false)

        assertFalse(prompt.enabled)
        assertTrue(prompt.detail, prompt.detail.contains("microphone"))
    }

    /** Even a finished run stays disabled while the permission is off — the mic gate wins. */
    @Test
    fun `the microphone gate outranks whatever the last attempt did`() {
        val finished = Calibration.Finished(InputLatencyResult.Measured(MEASURED_MILLIS, FIRM_CONFIDENCE))

        assertFalse(calibrationPrompt(finished, micGranted = false).enabled)
    }

    @Test
    fun `a run in flight cannot be started again`() {
        val prompt = calibrationPrompt(Calibration.Running, micGranted = true)

        assertFalse(prompt.enabled)
        assertTrue(prompt.detail, prompt.detail.contains("click"))
    }

    @Test
    fun `a measurement reports its figure and that timing is corrected from now on`() {
        val prompt = calibrationPrompt(
            Calibration.Finished(InputLatencyResult.Measured(MEASURED_MILLIS, FIRM_CONFIDENCE)),
            micGranted = true,
        )

        assertTrue(prompt.enabled)
        assertTrue(prompt.detail, prompt.detail.contains("42ms"))
        assertTrue(prompt.detail, prompt.detail.contains("corrected"))
    }

    /** A number without its confidence is the thing this whole area exists to stop. */
    @Test
    fun `a loosely located click is presented as a guess and a tight one is not hedged`() {
        val firm = calibrationPrompt(
            Calibration.Finished(InputLatencyResult.Measured(MEASURED_MILLIS, FIRM_CONFIDENCE)),
            micGranted = true,
        )
        val rough = calibrationPrompt(
            Calibration.Finished(InputLatencyResult.Measured(MEASURED_MILLIS, ROUGH_CONFIDENCE)),
            micGranted = true,
        )
        val smeared = calibrationPrompt(
            Calibration.Finished(InputLatencyResult.Measured(MEASURED_MILLIS, SMEARED_CONFIDENCE)),
            micGranted = true,
        )

        assertFalse(firm.detail, firm.detail.contains("roughly"))
        assertTrue(rough.detail, rough.detail.contains("roughly"))
        assertTrue(smeared.detail, smeared.detail.contains("first guess"))
    }

    /** A refusal reaches Dewi word for word; a silent failure is indistinguishable from a hang. */
    @Test
    fun `a refusal carries its own reason through to the screen`() {
        val reason = "peak 0.004 is only 1.2x the noise floor"
        val prompt = calibrationPrompt(
            Calibration.Finished(InputLatencyResult.Unmeasurable(reason)),
            micGranted = true,
        )

        assertTrue(prompt.enabled)
        assertTrue(prompt.detail, prompt.detail.contains(reason))
        assertTrue("a failure must not read as if Play it stopped working", prompt.detail.contains("still works"))
    }

    @Test
    fun `the button says measure again once something has been measured`() {
        val first = calibrationPrompt(Calibration.Idle, micGranted = true)
        val second = calibrationPrompt(
            Calibration.Finished(InputLatencyResult.Measured(MEASURED_MILLIS, FIRM_CONFIDENCE)),
            micGranted = true,
        )

        assertEquals("Measure it", first.action)
        assertEquals("Measure again", second.action)
    }
}
