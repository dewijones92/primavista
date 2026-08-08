package com.dewijones92.primavista.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dewijones92.primavista.common.Diag
import com.dewijones92.primavista.common.NoOpDiag

public const val DATABASE_VERSION: Int = 2

/**
 * What opening the practice history produced.
 *
 * A sealed result rather than an exception or a silent wipe: docs/spec.md I4 says what was
 * practised is not lost, and the two ways to break that are deleting the file and pretending
 * a failure did not happen. See `.claude/CODE-NOTES.md`.
 */
public sealed interface DatabaseOpening {
    public data class Opened(val database: PrimaVistaDatabase) : DatabaseOpening

    /** The file is still on disk, untouched. [reason] is what to show and what to log. */
    public data class Unreadable(val reason: String) : DatabaseOpening
}

@Database(
    entities = [
        SessionEntity::class,
        NoteVerdictEntity::class,
        SkillStateEntity::class,
        RepertoireEntity::class,
        SettingsEntity::class,
        AudioRouteLatencyEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(PrimaVistaConverters::class)
public abstract class PrimaVistaDatabase : RoomDatabase() {
    public abstract fun sessions(): SessionDao

    public abstract fun noteVerdicts(): NoteVerdictDao

    public abstract fun skillStates(): SkillStateDao

    public abstract fun repertoire(): RepertoireDao

    public abstract fun settings(): SettingsDao

    public abstract fun routeLatency(): AudioRouteLatencyDao

    public companion object {
        /** Named in app/src/main/res/xml/backup_rules.xml; changing it orphans the backup. */
        public const val FILE_NAME: String = "primavista.db"

        private const val TAG = "db.open"

        /**
         * Every builder must add this, tests included. See `.claude/CODE-NOTES.md`.
         */
        public val ForeignKeysOn: Callback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        /**
         * Opens the practice history, touching the file so a migration failure surfaces here
         * rather than at whichever query happened to run first. See `.claude/CODE-NOTES.md`.
         */
        public fun open(context: Context, diag: Diag = NoOpDiag): DatabaseOpening {
            val database = build(context)
            return runCatching { database.openHelper.writableDatabase }.fold(
                onSuccess = {
                    diag.event(
                        TAG,
                        "opened db=$FILE_NAME v=$DATABASE_VERSION migrations=${PrimaVistaMigrations.ALL.size} " +
                            "path=${it.path}",
                    )
                    DatabaseOpening.Opened(database)
                },
                onFailure = { failure ->
                    val reason = "${failure::class.java.simpleName}: ${failure.message}"
                    diag.event(
                        TAG,
                        "db=$FILE_NAME could not be opened at v=$DATABASE_VERSION, history left on disk: $reason",
                    )
                    DatabaseOpening.Unreadable(reason)
                },
            )
        }

        /**
         * Deletes the practice history and starts again. Only ever called because Dewi chose it
         * after being told what it costs — see `.claude/CODE-NOTES.md`.
         */
        public fun resetDiscardingHistory(context: Context, diag: Diag, reason: String): PrimaVistaDatabase {
            diag.event(TAG, "DISCARDING practice history: deleting db=$FILE_NAME on purpose, reason=$reason")
            context.deleteDatabase(FILE_NAME)
            val database = build(context)
            database.openHelper.writableDatabase
            diag.event(TAG, "db=$FILE_NAME recreated empty at v=$DATABASE_VERSION")
            return database
        }

        private fun build(context: Context): PrimaVistaDatabase =
            PrimaVistaMigrations.ALL
                .fold(Room.databaseBuilder(context, PrimaVistaDatabase::class.java, FILE_NAME)) { builder, step ->
                    builder.addMigrations(step)
                }
                .addCallback(ForeignKeysOn)
                .build()
    }
}
