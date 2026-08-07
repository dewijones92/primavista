package com.dewijones92.primavista.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The gate on docs/spec.md I4. Bumping the version without a migration, or turning the schema
 * export off, must fail here rather than on Dewi's phone where the only recovery is a reinstall.
 */
class SchemaAndMigrationTest {

    /**
     * The exported JSON is the evidence, because `@Database` has binary retention and cannot be
     * read reflectively. No file means `exportSchema` is off. See `.claude/CODE-NOTES.md`.
     */
    @Test
    fun theCurrentSchemaIsExportedSoAChangeIsReviewableInGit() {
        val exported = exportedSchema(DATABASE_VERSION)

        assertTrue("no exported schema at $exported: exportSchema is off or its location moved", exported.isFile)
    }

    @Test
    fun theExportedSchemaIsTheVersionTheConstantNames() {
        assertTrue(
            "the exported schema does not declare version $DATABASE_VERSION",
            exportedSchema(DATABASE_VERSION).readText().contains("\"version\": $DATABASE_VERSION"),
        )
    }

    /** Every version that has ever shipped keeps its schema, or its migration cannot be reviewed. */
    @Test
    fun everyVersionUpToTheCurrentOneHasItsSchemaOnDisk() {
        val missing = (1..DATABASE_VERSION).filterNot { exportedSchema(it).isFile }

        assertEquals(emptyList<Int>(), missing)
    }

    @Test
    fun everyStoredVersionCanReachTheCurrentOne() {
        assertEquals(
            "these stored versions have no migration path, so upgrading would strand their history",
            emptyList<Int>(),
            PrimaVistaMigrations.strandedVersions(),
        )
    }

    @Test
    fun aChainOfMigrationsStrandsNothing() {
        val chained = listOf(step(1, 2), step(2, 3))

        assertEquals(emptyList<Int>(), PrimaVistaMigrations.strandedVersions(target = 3, migrations = chained))
    }

    @Test
    fun aMultiVersionJumpCountsAsAPath() {
        assertEquals(
            emptyList<Int>(),
            PrimaVistaMigrations.strandedVersions(target = 3, migrations = listOf(step(1, 3), step(2, 3))),
        )
    }

    @Test
    fun aGapInTheChainNamesTheStrandedVersions() {
        assertEquals(
            listOf(1, 2),
            PrimaVistaMigrations.strandedVersions(target = 4, migrations = listOf(step(3, 4))),
        )
    }

    /** A version bump with no migration at all is the exact failure this file exists to catch. */
    @Test
    fun bumpingTheVersionWithNoMigrationStrandsEveryOlderVersion() {
        assertEquals(
            listOf(1, 2),
            PrimaVistaMigrations.strandedVersions(target = 3, migrations = emptyList()),
        )
    }

    private fun exportedSchema(version: Int): File =
        File(schemaDirectory(), "${PrimaVistaDatabase::class.java.name}/$version.json")

    private fun schemaDirectory(): File {
        val here = File(".").absoluteFile.normalize()
        return generateSequence(here) { it.parentFile }
            .flatMap { sequenceOf(File(it, "schemas"), File(it, "core/database/schemas")) }
            .firstOrNull { it.isDirectory }
            ?: error("no schemas directory found from $here")
    }

    private fun step(from: Int, to: Int): Migration = object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase): Unit = Unit
    }
}
