package com.dewijones92.primavista.database

import com.dewijones92.primavista.common.Diag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Recording : Diag {
    val lines: MutableList<String> = mutableListOf()
    val counts: MutableMap<String, Int> = mutableMapOf()

    override fun event(tag: String, message: String) {
        lines += message
    }

    override fun counted(tag: String, key: String, increment: Int) {
        counts[key] = (counts[key] ?: 0) + increment
    }

    override fun state(tag: String, snapshot: () -> String): Unit = Unit

    override fun report(header: Map<String, String>): String = lines.joinToString("\n")
}

class UnreadableRowLogTest {
    private val diag = Recording()

    @Test
    fun aRowIsNamedOnceHoweverOftenItComesBack() {
        val log = UnreadableRowLog(diag, TAG, KEY)

        repeat(5) { log.report("row-1", "row-1 is unreadable") }

        assertEquals(listOf("row-1 is unreadable"), diag.lines)
        assertEquals(5, diag.counts[KEY])
    }

    /**
     * The dedupe set is the same unbounded-growth problem the type exists to solve: a corrupt
     * file can hold a distinct id per row, and `states()` re-reads them all on every session.
     */
    @Test
    fun theSetOfNamedRowsStopsGrowingAtItsLimit() {
        val log = UnreadableRowLog(diag, TAG, KEY, nameLimit = 4)

        repeat(1_000) { log.report("row-$it", "row-$it is unreadable") }

        assertEquals(4 + 1, diag.lines.size)
        assertEquals(1_000, diag.counts[KEY])
    }

    @Test
    fun reachingTheLimitSaysSoOnceRatherThanFallingSilent() {
        val log = UnreadableRowLog(diag, TAG, KEY, nameLimit = 2)

        repeat(20) { log.report("row-$it", "row-$it is unreadable") }

        val cap = diag.lines.last()
        assertTrue(cap, cap.contains("more than 2 distinct rows"))
        assertTrue(cap, cap.contains("counted"))
        assertEquals(1, diag.lines.count { it.contains("no longer named") })
    }

    /** Capping must not stop an already-named row being counted, or the tally understates. */
    @Test
    fun anAlreadyNamedRowIsStillCountedAfterTheCap() {
        val log = UnreadableRowLog(diag, TAG, KEY, nameLimit = 1)

        log.report("row-1", "row-1 is unreadable")
        repeat(3) { log.report("row-2", "row-2 is unreadable") }
        repeat(3) { log.report("row-1", "row-1 is unreadable") }

        assertEquals(2, diag.lines.size)
        assertEquals(7, diag.counts[KEY])
    }

    private companion object {
        const val TAG = "db.test"
        const val KEY = "rowsUnreadable"
    }
}
