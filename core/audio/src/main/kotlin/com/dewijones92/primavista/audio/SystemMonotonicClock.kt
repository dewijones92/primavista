package com.dewijones92.primavista.audio

/** The one production [MonotonicClock]. */
public object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}
