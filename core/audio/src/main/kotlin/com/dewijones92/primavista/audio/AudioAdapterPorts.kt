package com.dewijones92.primavista.audio

/**
 * When a played sound was presented, and how wrong that could be.
 *
 * [uncertaintyMillis] travels with the moment because a loopback measurement is only as good as
 * its output anchor: an anchor a render buffer early silently over-states input latency by up to
 * that buffer, and an over-stated latency is an unmeasured bias wearing a measured label
 * (docs/todos/measure-audio-latency.md). See .claude/CODE-NOTES.md.
 */
public data class PlaybackMoment(val nanos: Long, val uncertaintyMillis: Double)

/** A player that can say when its last sound actually left the speaker. */
public interface PlaybackAnchor {
    public fun lastPlayback(): PlaybackMoment?
}
