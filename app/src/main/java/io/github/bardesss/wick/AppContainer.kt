package io.github.bardesss.wick

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.bardesss.wick.api.GReaderApi
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.data.KeystoreCredentialStore
import io.github.bardesss.wick.data.KeystoreTokenCipher
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.sync.MarkQueue
import io.github.bardesss.wick.sync.SyncEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class AppContainer(val context: Context) {
    val db: WickDb by lazy {
        Room.databaseBuilder(context, WickDb::class.java, "wick.db").build()
    }
    val itemDao get() = db.itemDao()
    val feedDao get() = db.feedDao()
    val queueDao get() = db.queueDao()
    val snapshotDao get() = db.snapshotDao()

    val creds: CredentialStore = KeystoreCredentialStore(context, KeystoreTokenCipher())
    val settings: SettingsStore by lazy { SettingsStore.create(context) }

    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.e("Wick", "uncaught error in app scope", e) },
    )

    val http: OkHttpClient by lazy { OkHttpClient() }

    val api: ReaderApi by lazy { GReaderApi(http) { creds.auth() } }
    val markQueue: MarkQueue by lazy { MarkQueue(api, queueDao, itemDao, appScope, inTransaction = { block -> db.withTransaction { block() } })
    }
    val syncEngine: SyncEngine by lazy { SyncEngine(api, db, markQueue) }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.05).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("favicons").toOkioPath())
                    .maxSizeBytes(16L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
