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
