package com.dewijones92.primavista.common

/**
 * The app's diagnostics buffer. See CLAUDE.md's diagnostics rule and docs/spec.md I7 —
 * a change is not done until a report from Dewi's phone can settle whether it worked.
 *
 * Three deliberately different verbs, because Totum lost sixteen minutes of history to
 * treating them as one:
 *
 *  - [event] for a decision. Bounded; the oldest is dropped when full.
 *  - [counted] for anything that can fire many times a second. Accumulated into a
 *    tally and emitted periodically, never per occurrence and never dropped.
 *  - [state] for a snapshot that should read as current rather than historical. Replaced
 *    in place, so a report shows the latest value once instead of a thousand times.
 */
public interface Diag {
    public fun event(tag: String, message: String)

    public fun counted(tag: String, key: String, increment: Int = 1)

    public fun state(tag: String, snapshot: () -> String)

    /** A shareable report. [header] carries build/device identity; see docs/todos/diagnostics-report.md. */
    public fun report(header: Map<String, String> = emptyMap()): String

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 600
    }
}

/** Discards everything. For tests and previews that have no interest in diagnostics. */
public object NoOpDiag : Diag {
    override fun event(tag: String, message: String): Unit = Unit
    override fun counted(tag: String, key: String, increment: Int): Unit = Unit
    override fun state(tag: String, snapshot: () -> String): Unit = Unit
    override fun report(header: Map<String, String>): String = "(diagnostics disabled)"
}
