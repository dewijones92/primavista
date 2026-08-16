package com.dewijones92.primavista.practice

/**
 * How sound reaches the microphone. Kinds differ by roughly an order of magnitude in latency, so
 * treating one as another is not a rounding error. See .claude/CODE-NOTES.md.
 */
public enum class RouteKind(public val assumedLatencyMillis: Double) {
    BuiltIn(BUILT_IN_PATH_MILLIS),
    Wired(BUILT_IN_PATH_MILLIS),
    Usb(BUILT_IN_PATH_MILLIS),
    Bluetooth(RADIO_HOP_MILLIS),
    Unknown(BUILT_IN_PATH_MILLIS),
    ;

    public val isRadio: Boolean get() = this == Bluetooth
}

private const val BUILT_IN_PATH_MILLIS = 60.0
private const val RADIO_HOP_MILLIS = 180.0
private const val SEPARATOR = '/'

/**
 * One microphone path, named stably enough to find yesterday's measurement again.
 *
 * [id] is what gets stored, and it round-trips through [of] so a row written by another build is
 * read rather than crashed on.
 */
public data class AudioRoute(val kind: RouteKind, val name: String) {

    public val id: String get() = "$kind$SEPARATOR$name"

    override fun toString(): String = id

    public companion object {
        public val Unidentified: AudioRoute = AudioRoute(RouteKind.Unknown, "unidentified")

        /** An id this build does not recognise becomes [RouteKind.Unknown] rather than an exception. */
        public fun of(id: String): AudioRoute {
            val kind = RouteKind.entries.firstOrNull { id.startsWith("$it$SEPARATOR") }
                ?: return AudioRoute(RouteKind.Unknown, id)
            return AudioRoute(kind, id.substringAfter(SEPARATOR))
        }
    }
}

/** One stored measurement. Per route, because a headset and the built-in mic are not one path. */
public data class RouteLatency(
    val route: AudioRoute,
    val latency: InputLatency,
    val measuredAtEpochMillis: Long,
)

/** The latency that applies right now, and the sentence explaining why it is that one. */
public data class AppliedLatency(
    val route: AudioRoute,
    val latency: InputLatency,
    val why: String,
) {
    public val isMeasured: Boolean
        get() = latency.provenance == InputLatency.Provenance.Measured

    /** What every mic verdict in this state is worth, in one line a report can carry. */
    override fun toString(): String =
        "route=${route.id} lat=${latency.millis}ms src=${latency.provenance} why=$why"
}

/**
 * What the app knows about each path's latency.
 *
 * Read and write are one port because they are one piece of knowledge, and the mic source needs
 * both: it applies a figure when a session opens and records one when calibration succeeds. The
 * implementation lives in `:app`, which is the only module that can see both the microphone and
 * the database — so `:core:audio` stays free of storage and `:core:database` free of hardware.
 */
public interface RouteLatencies {
    public suspend fun applied(route: AudioRoute): AppliedLatency

    public suspend fun record(route: AudioRoute, latency: InputLatency)

    /** Nothing is stored, so every route falls back to its kind's assumption. */
    public companion object Unknown : RouteLatencies {
        override suspend fun applied(route: AudioRoute): AppliedLatency =
            LatencyPolicy.decide(route, stored = null, nowEpochMillis = 0L)

        override suspend fun record(route: AudioRoute, latency: InputLatency): Unit = Unit
    }
}

/**
 * Decides which latency figure applies to a route. The one place that decision is made, so a
 * Bluetooth headset can never quietly inherit the built-in mic's figure — the failure this whole
 * area exists to prevent (docs/todos/measure-audio-latency.md). See .claude/CODE-NOTES.md.
 */
public object LatencyPolicy {

    public fun decide(route: AudioRoute, stored: RouteLatency?, nowEpochMillis: Long): AppliedLatency {
        val usable = stored?.takeIf { it.route == route && it.latency.provenance == InputLatency.Provenance.Measured }
        return when {
            usable != null -> AppliedLatency(
                route = route,
                latency = usable.latency,
                why = "measured on this route ${ageOf(usable, nowEpochMillis)}",
            )

            stored != null && stored.route != route -> assume(
                route,
                "the only stored figure is for ${stored.route.id}, which is a different path",
            )

            stored != null -> assume(route, "the stored figure was itself ${stored.latency.provenance}, not measured")

            else -> assume(route, "nothing has ever been measured on this path")
        }
    }

    private fun assume(route: AudioRoute, because: String): AppliedLatency = AppliedLatency(
        route = route,
        latency = InputLatency(route.kind.assumedLatencyMillis, InputLatency.Provenance.Assumed),
        why = "$because, so assuming what a ${route.kind} path usually costs",
    )

    private fun ageOf(stored: RouteLatency, nowEpochMillis: Long): String {
        val days = (nowEpochMillis - stored.measuredAtEpochMillis) / MILLIS_PER_DAY
        return when {
            days < 0L -> "at an epoch later than now, so this device's clock has moved"
            days == 0L -> "today"
            days == 1L -> "yesterday"
            else -> "$days days ago"
        }
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}
