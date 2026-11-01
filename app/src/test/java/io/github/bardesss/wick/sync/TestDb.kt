package io.github.bardesss.wick.sync

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import io.github.bardesss.wick.api.ReaderApi
import kotlinx.coroutines.CoroutineScope
import androidx.test.core.app.ApplicationProvider
import io.github.bardesss.wick.db.WickDb

fun WickDb.newQueue(api: ReaderApi, scope: CoroutineScope) =
    MarkQueue(api, queueDao(), itemDao(), scope, inTransaction = { block -> withTransaction { block() } })

fun inMemoryDb(): WickDb {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, WickDb::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
}

/** What a test checks of a stored item. */
data class StoredItem(val id: String, val title: String, val read: Boolean, val manualUnread: Boolean)

/** The stored items among [ids], in that order; ids with no row are left out. */
fun WickDb.storedItems(ids: List<String>): List<StoredItem> {
    val byId = HashMap<String, StoredItem>()
    openHelper.readableDatabase.query("SELECT id, title, read, manualUnread FROM ItemEntity").use { c ->
        while (c.moveToNext()) {
            byId[c.getString(0)] = StoredItem(c.getString(0), c.getString(1), c.getInt(2) != 0, c.getInt(3) != 0)
        }
    }
    return ids.mapNotNull { byId[it] }
}
