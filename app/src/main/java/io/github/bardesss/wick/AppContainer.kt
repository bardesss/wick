package io.github.bardesss.wick

import android.content.Context
import androidx.room.Room
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.GReaderApi
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.sync.MarkQueue
import io.github.bardesss.wick.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppContainer(val context: Context) {
    val db: WickDb by lazy {
        Room.databaseBuilder(context, WickDb::class.java, "wick.db").build()
    }
    val itemDao get() = db.itemDao()
    val feedDao get() = db.feedDao()
    val queueDao get() = db.queueDao()

    /** Set once the user has logged in; the API reads it on every call. */
    @Volatile
    var auth: Auth? = null

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val api: ReaderApi by lazy { GReaderApi(OkHttpClient()) { auth } }
    val markQueue: MarkQueue by lazy { MarkQueue(api, queueDao, itemDao, appScope) }
    val syncEngine: SyncEngine by lazy { SyncEngine(api, db, markQueue) }
}
