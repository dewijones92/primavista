package com.dewijones92.primavista.practice

import kotlin.math.roundToLong

internal object Nanos {
    const val PER_MILLI: Long = 1_000_000L
    const val PER_MINUTE: Long = 60_000_000_000L

    fun toMillis(nanos: Long): Double = nanos.toDouble() / PER_MILLI

    fun ofMillis(millis: Double): Long = (millis * PER_MILLI).roundToLong()
}
