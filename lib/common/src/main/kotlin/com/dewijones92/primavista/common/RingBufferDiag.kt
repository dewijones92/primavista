package com.dewijones92.primavista.common

import java.util.ArrayDeque

/**
 * The production [Diag]. Thread-safe because audio callbacks, the UI and the judge all write to it.
 *
 * See .claude/CODE-NOTES.md for why `counted` flushes on a threshold and why `state` stores the
 * lambda rather than its result.
 */
public class RingBufferDiag(
    private val capacity: Int = Diag.DEFAULT_CAPACITY,
    private val countedFlushEvery: Int = DEFAULT_FLUSH_EVERY,
    private val nanoTime: () -> Long = System::nanoTime,
) : Diag {

    private val lock = Any()
    private val events = ArrayDeque<Entry>(capacity)
    private val tallies = LinkedHashMap<String, Long>()
    private val sinceFlush = HashMap<String, Int>()
    private val states = LinkedHashMap<String, () -> String>()
    private val startedAtNanos = nanoTime()
    private var dropped = 0L

    override fun event(tag: String, message: String) {
        synchronized(lock) { append(Entry(nanoTime() - startedAtNanos, tag, message)) }
    }

    override fun counted(tag: String, key: String, increment: Int) {
        if (increment <= 0) return
        synchronized(lock) {
            val id = "$tag/$key"
            val total = (tallies[id] ?: 0L) + increment
            tallies[id] = total
            val pending = (sinceFlush[id] ?: 0) + increment
            if (pending >= countedFlushEvery) {
                sinceFlush[id] = 0
                append(Entry(nanoTime() - startedAtNanos, tag, "$key x$total (running total)"))
            } else {
                sinceFlush[id] = pending
            }
        }
    }

    override fun state(tag: String, snapshot: () -> String) {
        synchronized(lock) { states[tag] = snapshot }
    }

    override fun report(header: Map<String, String>): String = synchronized(lock) {
        buildString {
            appendLine("=== PrimaVista diagnostics ===")
            header.forEach { (k, v) -> appendLine("$k: $v") }
            appendLine("uptime: ${millisOf(nanoTime() - startedAtNanos)}ms")
            appendLine("events: ${events.size}/$capacity kept, $dropped dropped")

            if (states.isNotEmpty()) {
                appendLine()
                appendLine("--- state ---")
                states.forEach { (tag, snapshot) ->
                    val rendered = runCatching(snapshot).getOrElse { "<threw ${it::class.simpleName}>" }
                    appendLine("[$tag] $rendered")
                }
            }

            if (tallies.isNotEmpty()) {
                appendLine()
                appendLine("--- counted ---")
                tallies.forEach { (id, total) -> appendLine("$id: $total") }
            }

            appendLine()
            appendLine("--- events (oldest first) ---")
            events.forEach { appendLine("+${millisOf(it.atNanos)}ms [${it.tag}] ${it.message}") }
        }
    }

    private fun append(entry: Entry) {
        if (events.size >= capacity) {
            events.removeFirst()
            dropped++
        }
        events.addLast(entry)
    }

    private fun millisOf(nanos: Long): Long = nanos / NANOS_PER_MILLI

    private data class Entry(val atNanos: Long, val tag: String, val message: String)

    public companion object {
        public const val DEFAULT_FLUSH_EVERY: Int = 250
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
