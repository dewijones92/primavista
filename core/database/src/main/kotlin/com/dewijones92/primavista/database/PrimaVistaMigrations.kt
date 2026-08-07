package com.dewijones92.primavista.database

import androidx.room.migration.Migration

/**
 * Every schema change lands with the [Migration] that carries the existing rows forward.
 * See `.claude/CODE-NOTES.md` — there is no destructive default, deliberately.
 */
public object PrimaVistaMigrations {
    public val ALL: List<Migration> = emptyList()

    /**
     * Stored versions that cannot reach [target] by any chain of [migrations]. Empty is the
     * invariant; a non-empty result names the versions whose history this build would strand.
     */
    public fun strandedVersions(
        target: Int = DATABASE_VERSION,
        migrations: List<Migration> = ALL,
    ): List<Int> {
        val reachable = mutableSetOf(target)
        while (migrations.any { it.endVersion in reachable && reachable.add(it.startVersion) }) {
            // widen until stable
        }
        return (1 until target).filterNot { it in reachable }
    }
}
