package com.dewijones92.primavista.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject

private const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"

/**
 * Rebuilds the database exactly as the build that shipped [version] left it, from that version's
 * committed schema JSON. See `.claude/CODE-NOTES.md`.
 */
internal fun createDatabaseAtVersion(
    context: Context,
    name: String,
    version: Int,
    seed: SQLiteDatabase.() -> Unit = {},
) {
    val schema = JSONObject(schemaJson(version)).getJSONObject("database")
    context.deleteDatabase(name)
    val file = context.getDatabasePath(name)
    file.parentFile?.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
        schema.getJSONArray("entities").objects().forEach { db.createTable(it) }
        schema.getJSONArray("setupQueries").strings().forEach { db.execSQL(it) }
        db.version = version
        db.seed()
    }
}

private fun SQLiteDatabase.createTable(entity: JSONObject) {
    val table = entity.getString("tableName")
    execSQL(entity.getString("createSql").replace(TABLE_NAME_PLACEHOLDER, table))
    entity.optJSONArray("indices").objects().forEach {
        execSQL(it.getString("createSql").replace(TABLE_NAME_PLACEHOLDER, table))
    }
}

private fun schemaJson(version: Int): String =
    InstrumentationRegistry.getInstrumentation().context.assets
        .open("${PrimaVistaDatabase::class.java.name}/$version.json")
        .bufferedReader()
        .use { it.readText() }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
