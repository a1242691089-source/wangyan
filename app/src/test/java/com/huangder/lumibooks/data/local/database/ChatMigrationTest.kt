package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * exportSchema=false means Room never verifies MIGRATION_22_23 against the
 * entities, so this test is the only guard against silent schema drift:
 * column names/nullability/defaults are asserted via PRAGMA table_info.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChatMigrationTest {
    @Test
    fun migrationCreatesSessionsTableMatchingEntityShape() {
        withMigratedDatabase { db ->
            val columns = tableInfo(db, "chat_sessions")
            assertEquals(
                listOf("id", "title", "entryBookId", "createdAt", "updatedAt", "lastMessageAt"),
                columns.map { it.name }
            )
            columns.forEach { column ->
                when (column.name) {
                    "id", "createdAt", "updatedAt", "lastMessageAt" ->
                        assertTrue("$column must be NOT NULL", column.notNull)
                    "title" -> assertTrue("$column must be NOT NULL", column.notNull)
                    "entryBookId" -> assertNull("entryBookId must be nullable", column.notNull.takeIf { it })
                }
            }
            assertTrue(columns.first { it.name == "id" }.isPrimaryKey)

            val indices = mutableListOf<String>()
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'chat_sessions'"
            ).use {
                while (it.moveToNext()) indices.add(it.getString(0))
            }
            assertTrue(indices.contains("index_chat_sessions_lastMessageAt"))
            assertTrue(indices.contains("index_chat_sessions_entryBookId"))
        }
    }

    @Test
    fun migrationCreatesMessagesTableWithDefaultsAndCascade() {
        withMigratedDatabase { db ->
            val columns = tableInfo(db, "chat_messages")
            assertEquals(
                listOf(
                    "id", "sessionId", "role", "content", "citationsJson",
                    "sourceCount", "status", "errorCode", "createdAt"
                ),
                columns.map { it.name }
            )
            assertEquals("'[]'", columns.first { it.name == "citationsJson" }.defaultValue)
            assertEquals("0", columns.first { it.name == "sourceCount" }.defaultValue)
            assertEquals("'complete'", columns.first { it.name == "status" }.defaultValue)
            assertNull(columns.first { it.name == "errorCode" }.defaultValue)

            // Omitted columns fall back to their DEFAULT values.
            db.execSQL(
                "INSERT INTO chat_sessions (id, title, createdAt, updatedAt, lastMessageAt) " +
                    "VALUES (1, '测试', 100, 100, 100)"
            )
            db.execSQL(
                "INSERT INTO chat_messages (sessionId, role, content, createdAt) " +
                    "VALUES (1, 'user', '你好', 100)"
            )
            db.query("SELECT citationsJson, sourceCount, status FROM chat_messages").use {
                assertTrue(it.moveToFirst())
                assertEquals("[]", it.getString(0))
                assertEquals(0, it.getInt(1))
                assertEquals("complete", it.getString(2))
            }

            // Deleting the session cascades to its messages.
            db.execSQL("DELETE FROM chat_sessions WHERE id = 1")
            db.query("SELECT COUNT(*) FROM chat_messages").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    private class ColumnInfo(
        val name: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val isPrimaryKey: Boolean
    )

    private fun tableInfo(db: SupportSQLiteDatabase, table: String): List<ColumnInfo> {
        val rows = mutableListOf<ColumnInfo>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            val notNullIdx = cursor.getColumnIndex("notnull")
            val dfltIdx = cursor.getColumnIndex("dflt_value")
            val pkIdx = cursor.getColumnIndex("pk")
            while (cursor.moveToNext()) {
                rows.add(
                    ColumnInfo(
                        name = cursor.getString(nameIdx),
                        notNull = cursor.getInt(notNullIdx) == 1,
                        defaultValue = if (cursor.isNull(dfltIdx)) null else cursor.getString(dfltIdx),
                        isPrimaryKey = cursor.getInt(pkIdx) > 0
                    )
                )
            }
        }
        return rows
    }

    /** Opens a v22-shaped database (knowledge tables present) and applies 22 -> 23. */
    private fun withMigratedDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            // Room enables FK enforcement at open; the raw helper needs it explicitly.
            db.query("PRAGMA foreign_keys = ON").use { }
            DatabaseMigrations.MIGRATION_22_23.migrate(db)
            block(db)
        }
    }
}
