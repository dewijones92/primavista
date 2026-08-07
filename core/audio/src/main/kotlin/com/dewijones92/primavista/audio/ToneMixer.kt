package com.dewijones92.primavista.audio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Sums the sounding [ToneVoice]s into successive buffers and keeps the absolute frame count.
 *
 * Pure JVM on purpose: this is where the loopback's output anchor is decided, and an anchor that
 * is a render buffer out silently over-states measured input latency. See .claude/CODE-NOTES.md.
 */
public class ToneMixer(public val sampleRate: Int, private val ceiling: Float = DEFAULT_CEILING) {
    init {
        require(sampleRate > 0) { "sample rate must be positive, was $sampleRate" }
    }

    private val lock = ReentrantLock()
    private val voices = mutableListOf<ToneVoice>()

    @Volatile
    private var rendered = 0L

    @Volatile
    private var anchor: Long? = null

    public val framesRendered: Long get() = rendered

    /** Absolute frame at which the most recently added voices first sound, or null. */
    public val anchorFrame: Long? get() = anchor

    public val soundingCount: Int get() = lock.withLock { voices.size }

    /** Adds voices and returns the exact frame their first sample lands on. */
    public fun add(added: List<ToneVoice>): Long = lock.withLock {
        voices += added
        anchor = rendered
        rendered
    }

    /** Fills [out] with the next buffer and advances the frame count. Returns frames rendered. */
    public fun render(out: FloatArray): Int = lock.withLock {
        out.fill(0f)
        if (voices.isNotEmpty()) {
            voices.forEach { it.mixInto(out, out.size) }
            voices.removeAll { it.isFinished }
            softClip(out)
        }
        rendered += out.size
        out.size
    }

    /** Ramps every sounding voice down; returns how many were ramped. */
    public fun beginReleaseAll(): Int = lock.withLock {
        voices.forEach { it.beginRelease() }
        voices.size
    }

    public fun clear() {
        lock.withLock {
            voices.clear()
            anchor = null
        }
    }

    private fun softClip(buffer: FloatArray) {
        for (index in buffer.indices) {
            buffer[index] = max(-ceiling, min(ceiling, buffer[index]))
        }
    }

    public companion object {
        public const val DEFAULT_CEILING: Float = 0.98f
    }
}
