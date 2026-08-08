package com.dewijones92.primavista.database

import com.dewijones92.primavista.common.Diag

/** Keeps every diagnostics line so a test can assert what was said, not only what happened. */
internal class RecordingDiag : Diag {
    val lines: MutableList<String> = mutableListOf()
    val counts: MutableMap<String, Int> = mutableMapOf()

    override fun event(tag: String, message: String) {
        lines += "$tag $message"
    }

    override fun counted(tag: String, key: String, increment: Int) {
        counts[key] = (counts[key] ?: 0) + increment
    }

    override fun state(tag: String, snapshot: () -> String): Unit = Unit

    override fun report(header: Map<String, String>): String = lines.joinToString("\n")
}
