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
class KnowledgeRelationMigrationTest {
    @Test
    fun migrationCreatesRelationsTableWithIndicesAndCascadeFromBothEnds() {
        withMigratedDatabase { db ->
            db.execSQL(
                "INSERT INTO knowledge_nodes (id, syncId, title, thought, tagsJson, createdAt, updatedAt) " +
                    "VALUES (1, 'node-1', '甲', '', '[]', 100, 100), (2, 'node-2', '乙', '', '[]', 100, 100)"
            )
            db.execSQL(
                "INSERT INTO knowledge_relations (id, sourceNodeId, targetNodeId, syncId, relationType, createdAt, updatedAt) " +
                    "VALUES (1, 1, 2, 'rel-1', 'explains', 100, 100)"
            )
            // Direction matters: the reverse edge is a different row and must be allowed.
            db.execSQL(
                "INSERT INTO knowledge_relations (id, sourceNodeId, targetNodeId, syncId, relationType, createdAt, updatedAt) " +
                    "VALUES (2, 2, 1, 'rel-2', 'explains', 100, 100)"
            )

            // Deleting the target node removes the edge that points at it.
            db.execSQL("DELETE FROM knowledge_nodes WHERE id = 2")
            assertEquals(0, countRelations(db))

            // And the source side cascades too.
            db.execSQL(
                "INSERT INTO knowledge_nodes (id, syncId, title, thought, tagsJson, createdAt, updatedAt) " +
                    "VALUES (3, 'node-3', '丙', '', '[]', 100, 100)"
            )
            db.execSQL(
                "INSERT INTO knowledge_relations (id, sourceNodeId, targetNodeId, syncId, relationType, createdAt, updatedAt) " +
                    "VALUES (3, 1, 3, 'rel-3', 'supports', 100, 100)"
            )
            db.execSQL("DELETE FROM knowledge_nodes WHERE id = 1")
            assertEquals(0, countRelations(db))

            val tables = mutableListOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'knowledge_relations'").use {
                while (it.moveToNext()) tables.add(it.getString(0))
            }
            assertEquals(listOf("knowledge_relations"), tables)

            val indices = mutableListOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'knowledge_relations'").use {
                while (it.moveToNext()) indices.add(it.getString(0))
            }
            assertTrue(indices.contains("index_knowledge_relations_sourceNodeId"))
            assertTrue(indices.contains("index_knowledge_relations_targetNodeId"))
            assertTrue(indices.contains("index_knowledge_relations_sourceNodeId_targetNodeId_relationType"))
            assertTrue(indices.contains("index_knowledge_relations_syncId"))
        }
    }

    @Test
    fun duplicateSourceTargetTypeIsRejected() {
        withMigratedDatabase { db ->
            db.execSQL(
                "INSERT INTO knowledge_nodes (id, syncId, title, thought, tagsJson, createdAt, updatedAt) " +
                    "VALUES (1, 'node-1', '甲', '', '[]', 100, 100), (2, 'node-2', '乙', '', '[]', 100, 100)"
            )
            db.execSQL(
                "INSERT INTO knowledge_relations (sourceNodeId, targetNodeId, syncId, relationType, createdAt, updatedAt) " +
                    "VALUES (1, 2, 'rel-1', 'explains', 100, 100)"
            )
            assertThrows {
                db.execSQL(
                    "INSERT INTO knowledge_relations (sourceNodeId, targetNodeId, syncId, relationType, createdAt, updatedAt) " +
                        "VALUES (1, 2, 'rel-2', 'explains', 100, 100)"
                )
            }
        }
    }

    @Test
    fun duplicateSyncIdIsRejected() {
        withMigratedDatabase { db ->
            db.execSQL(
                "INSERT INTO knowledge_nodes (id, syncId, title, thought, tagsJson, createdAt, updatedAt) " +
                    "VALUES (1, 'node-1', '甲', '', '[]', 100, 100), (2, 'node-2', '乙', '', '[]', 100, 100)"
            )
            db.execSQL(
                "INSERT INTO knowledge_relations (sourceNodeId, targetNodeId, syncId, relationType, createdAt, updatedAt) " +
                    "VALUES (1, 2, 'rel-1', 'explains', 100, 100)"
            )
            assertThrows {
                db.execSQL(
                    "INSERT INTO knowledge_relations (sourceNodeId, targetNodeId, syncId, relationType, createdAt, updatedAt) " +
                        "VALUES (2, 1, 'rel-1', 'supports', 100, 100)"
                )
            }
        }
    }

    /** Opens a v21-shaped database (knowledge nodes/evidence present) and applies 21 -> 22. */
    private fun withMigratedDatabase(block: (SupportSQLiteDatabase) -> Unit) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            // Room enables FK enforcement at open; the raw helper needs it explicitly.
            db.query("PRAGMA foreign_keys = ON").use { }
            DatabaseMigrations.MIGRATION_20_21.migrate(db)
            DatabaseMigrations.MIGRATION_21_22.migrate(db)
            block(db)
        }
    }

    private fun countRelations(db: SupportSQLiteDatabase): Int =
        db.query("SELECT COUNT(*) FROM knowledge_relations").use {
            it.moveToFirst()
            it.getInt(0)
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
