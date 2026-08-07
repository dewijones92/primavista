package com.dewijones92.primavista.score

import com.dewijones92.primavista.common.Diag

class RecordingDiag : Diag {
    val events = mutableListOf<String>()
    val counts = mutableMapOf<String, Int>()

    override fun event(tag: String, message: String) {
        events += "$tag $message"
    }

    override fun counted(tag: String, key: String, increment: Int) {
        counts[key] = (counts[key] ?: 0) + increment
    }

    override fun state(tag: String, snapshot: () -> String) = Unit

    override fun report(header: Map<String, String>): String = events.joinToString("\n")
}
