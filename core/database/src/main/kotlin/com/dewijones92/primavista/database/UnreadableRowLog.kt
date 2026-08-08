package com.dewijones92.primavista.database

import com.dewijones92.primavista.common.Diag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A row this build cannot read is deliberately left on disk, so it comes back on every query.
 * Logging it every time is how one discarded skill key empties a bounded buffer — see
 * `.claude/CODE-NOTES.md` and CLAUDE.md's diagnostics rule.
 */
internal class UnreadableRowLog(
    private val diag: Diag,
    private val tag: String,
    private val countKey: String,
    private val nameLimit: Int = DEFAULT_NAME_LIMIT,
) {
    private val named = ConcurrentHashMap.newKeySet<String>()
    private val capped = AtomicBoolean(false)

    fun report(id: String, message: String) {
        diag.counted(tag, countKey)
        when {
            id in named -> Unit
            named.size >= nameLimit -> reportCap()
            named.add(id) -> diag.event(tag, message)
        }
    }

    private fun reportCap() {
        if (capped.compareAndSet(false, true)) {
            diag.event(
                tag,
                "$countKey: more than $nameLimit distinct rows are unreadable, so further ones are " +
                    "counted but no longer named; the tally is still exact",
            )
        }
    }

    private companion object {
        const val DEFAULT_NAME_LIMIT = 32
    }
}
