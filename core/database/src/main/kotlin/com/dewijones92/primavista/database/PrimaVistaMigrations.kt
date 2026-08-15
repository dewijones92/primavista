package com.dewijones92.primavista.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change lands with the [Migration] that carries the existing rows forward.
 * See `.claude/CODE-NOTES.md` — there is no destructive default, deliberately.
 */
public object PrimaVistaMigrations {
    /**
     * `SkillState.repetition` was held only in memory, so every reload put a mature skill back on
     * the bottom rung. Existing rows start at 0 — see `.claude/CODE-NOTES.md`.
     */
    public val AddSkillRepetition: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE skill_states ADD COLUMN repetition INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * The journey's two tables. Nothing existing is touched, so every session, verdict and skill
     * row carries over untouched — see `.claude/CODE-NOTES.md`.
     */
    public val AddJourney: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `stage_progress` (`stageNumber` INTEGER NOT NULL, " +
                    "`firstReachedAtEpochMillis` INTEGER NOT NULL, `firstPassedAtEpochMillis` INTEGER, " +
                    "PRIMARY KEY(`stageNumber`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `placement_reads` (`takenAtEpochMillis` INTEGER NOT NULL, " +
                    "`outcomeKind` TEXT NOT NULL, `probesTaken` INTEGER NOT NULL, " +
                    "`seededSkills` INTEGER NOT NULL, `summary` TEXT NOT NULL, " +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
            )
        }
    }

    /**
     * Reading ahead. Existing rows default to 0, which is off — the same behaviour they had, since
     * a preference nobody set should not start covering the music.
     */
    public val AddReadingLead: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE settings ADD COLUMN readingLeadBeats INTEGER NOT NULL DEFAULT 0")
        }
    }

    public val ALL: List<Migration> = listOf(AddSkillRepetition, AddJourney, AddReadingLead)

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
