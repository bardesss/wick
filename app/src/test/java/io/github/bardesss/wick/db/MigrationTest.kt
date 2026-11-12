package io.github.bardesss.wick.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a version 1 database from its exported schema, as an installed app has it, and opens it
 * with the current version. room-testing is not a dependency, so this stands in for its helper.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createVersion(version: Int, extra: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/io.github.bardesss.wick.db.WickDb/$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            extra(db)
            db.version = version
        }
    }

    private fun createVersion1() = createVersion(1) { db ->
        db.execSQL("INSERT INTO FeedEntity (id, title, folder, htmlUrl, iconUrl) VALUES ('feed/1', 'News', 'Tech', 'https://n.example', 'https://n.example/i.png')")
        db.execSQL("INSERT INTO ItemEntity (id, feedId, title, url, publishedSec, html, read, manualUnread) VALUES ('a', 'feed/1', 'A', NULL, 100, NULL, 1, 0)")
        db.execSQL("INSERT INTO PendingMarkEntity (itemId, read, queuedAt) VALUES ('a', 1, 7)")
        db.execSQL("INSERT INTO PendingMarkAllEntity (streamRaw, olderThanUsec) VALUES ('user/-/label/Tech', 5000000)")
    }

    @Test
    fun version_1_keeps_its_queue_and_feeds() = runTest {
        createVersion1()
        val db = Room.databaseBuilder(context, WickDb::class.java, name).allowMainThreadQueries().build()
        try {
            assertEquals(listOf(PendingMarkEntity("a", read = true, queuedAt = 7)), db.queueDao().pending())
            assertEquals(listOf("user/-/label/Tech"), db.queueDao().pendingAll().map { it.streamRaw })
            assertEquals(listOf(FeedEntity("feed/1", "News", "Tech", "https://n.example/i.png")), db.feedDao().feedsNow())
            assertEquals(setOf("a"), db.itemDao().readAmong(listOf("a")))
            // version 3 does not know when older items were read, so none of them count as recent
            assertEquals(emptyList<String>(), db.itemDao().recentlyRead(10))
        } finally {
            db.close()
        }
    }

    @Test
    fun migrates_3_to_4_keeping_queued_marks() = runTest {
        createVersion(3) { db ->
            db.execSQL("INSERT INTO PendingMarkEntity (itemId, read, queuedAt) VALUES ('a', 1, 7)")
        }
        val db = Room.databaseBuilder(context, WickDb::class.java, name).allowMainThreadQueries().build()
        try {
            assertEquals(listOf(PendingMarkEntity("a", read = true, queuedAt = 7, attempted = false)), db.queueDao().pending())
        } finally {
            db.close()
        }
    }
}
