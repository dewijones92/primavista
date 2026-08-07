package com.dewijones92.primavista.audio

import com.dewijones92.primavista.common.Diag

/**
 * Deliberate twin of the unit-test `RecordingDiag`: androidTest and test are separate
 * compilations, and sharing one would mean adding a testFixtures source set to the build.
 */
class RecordingDiag : Diag {
    val events: MutableList<String> = mutableListOf()
    val counts: MutableMap<String, Int> = mutableMapOf()
    val states: MutableMap<String, String> = mutableMapOf()

    override fun event(tag: String, message: String) {
        events += "$tag $message"
    }

    override fun counted(tag: String, key: String, increment: Int) {
        counts["$tag.$key"] = (counts["$tag.$key"] ?: 0) + increment
    }

    override fun state(tag: String, snapshot: () -> String) {
        states[tag] = snapshot()
    }

    override fun report(header: Map<String, String>): String =
        (header.entries.map { "${it.key}=${it.value}" } + events + counts.map { "${it.key}=${it.value}" })
            .joinToString("\n")
}
