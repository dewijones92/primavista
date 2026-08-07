package com.dewijones92.primavista.practice

/**
 * A [NanoClock] that only moves when told to. Lives in main rather than test so previews and
 * screenshot tooling can drive a conductor without waiting for real seconds to pass.
 */
public class FakeClock(startNanos: Long = 0L) : NanoClock {
    private var current: Long = startNanos

    override fun nowNanos(): Long = current

    public fun advance(nanos: Long) {
        require(nanos >= 0) { "a monotonic clock cannot be wound back by $nanos ns" }
        current += nanos
    }

    public fun advanceMillis(millis: Double): Unit = advance(Nanos.ofMillis(millis))
}
