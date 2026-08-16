package com.dewijones92.primavista.ui.practice

/**
 * How much of the library has been read.
 *
 * The scheduler cannot choose until every piece is parsed — it has to know what is on offer before
 * it can prefer a real song to a generated drill — and on a cold start that is seconds. So the wait
 * is real and this is what makes it legible rather than shorter.
 */
public data class ReadingProgress(val read: Int, val expected: Int) {

    public val settled: Boolean get() = expected <= 0 || read >= expected

    public val fraction: Float get() = if (expected <= 0) 1f else (read.toFloat() / expected).coerceIn(0f, 1f)

    public companion object {
        /** Nothing to wait for, which is what a preview and every test that does not care get. */
        public val Settled: ReadingProgress = ReadingProgress(read = 0, expected = 0)
    }
}

/**
 * What the wait says. Counting is the honest version: "choosing" alone gives no sign of progress,
 * and a screen that cannot show movement is indistinguishable from one that has hung.
 */
internal fun waitingText(loading: Boolean, reading: ReadingProgress): String = when {
    !loading -> "Nothing loaded"
    reading.settled -> "Choosing something to read…"
    else -> "Reading your songs… ${reading.read} of ${reading.expected}"
}
