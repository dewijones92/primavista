package com.dewijones92.primavista.database

import com.dewijones92.primavista.common.Diag
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * What a read of the practice history turned out to be. See `.claude/CODE-NOTES.md`.
 *
 * The third sealed result in this module, alongside [DatabaseOpening] and [SkillKeyReading],
 * and for the same reason: a refusal and an empty history are opposite statements about Dewi's
 * practice, so they must not be the same value (docs/spec.md I4).
 */
public sealed interface StoredReading<out T> {
    public data class Readable<out T>(val value: T) : StoredReading<T>

    /** [what] names the read; [reason] is what to show and what a diagnostics report carries. */
    public data class Unreadable(val what: String, val reason: String) : StoredReading<Nothing>
}

public inline fun <T, R> StoredReading<T>.map(transform: (T) -> R): StoredReading<R> = when (this) {
    is StoredReading.Readable -> StoredReading.Readable(transform(value))
    is StoredReading.Unreadable -> this
}

/** Null on a refusal, so use it only where the caller has already shown or logged the refusal. */
public fun <T> StoredReading<T>.valueOrNull(): T? = when (this) {
    is StoredReading.Readable -> value
    is StoredReading.Unreadable -> null
}

/** A `@TypeConverter` fails the whole cursor, so the alternative is a crash. See CODE-NOTES. */
internal suspend inline fun <T> Diag.readOrRefuse(tag: String, what: String, read: () -> T): StoredReading<T> {
    val outcome = runCatching(read)
    val failure = outcome.exceptionOrNull() ?: return StoredReading.Readable(outcome.getOrThrow())
    if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
    val reason = if (failure is CancellationException) {
        "the database closed underneath the read: ${failure.message ?: failure}"
    } else {
        failure.message ?: failure.toString()
    }
    event(tag, "$what could not be read at all; rows left on disk, nothing shown: $reason")
    return StoredReading.Unreadable(what, reason)
}
