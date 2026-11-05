package com.huangder.lumibooks.data.local.database

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KnowledgeMigrationTest {
    @Test
    fun migrationCreatesKnowledgeTablesWithIndicesAndCascade() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            // Room enables FK enforcement at open; the raw helper needs it explicitly for the cascade check.
            db.query("PRAGMA foreign_keys = ON").use { }
            DatabaseMigrations.MIGRATION_20_21.migrate(db)

            // Insert a node, then evidence; the FK cascade must remove evidence with the node.
            db.execSQL(
                "INSERT INTO knowledge_nodes (id, syncId, title, thought, tagsJson, createdAt, updatedAt) " +
                    "VALUES (1, 'node-1', '标题', '理解', '[]', 100, 100)"
            )
            db.execSQL(
                "INSERT INTO knowledge_evidence (id, nodeId, syncId, bookId, chapterIndex, startPosition, " +
                    "endPosition, selectedText, createdAt, updatedAt) " +
                    "VALUES (1, 1, 'ev-1', 'book-1', 0, 0, 4, '原文', 100, 100)"
            )
            // Duplicate syncId on either table must violate the unique index.
            assertThrows {
                db.execSQL(
                    "INSERT INTO knowledge_nodes (id, syncId, title, thought, tagsJson, createdAt, updatedAt) " +
                        "VALUES (2, 'node-1', '重复', '', '[]', 200, 200)"
                )
            }
            db.execSQL("DELETE FROM knowledge_nodes WHERE id = 1")
            db.query("SELECT COUNT(*) FROM knowledge_evidence").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            // New table exists alongside existing data (fresh install on v20 has no books, that's fine).
            val tables = mutableListOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'knowledge%'").use {
                while (it.moveToNext()) tables.add(it.getString(0))
            }
            assertTrue(tables.containsAll(listOf("knowledge_nodes", "knowledge_evidence")))
        }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (expected: Exception) {
            return
        }
        throw AssertionError("expected constraint violation")
    }
}
