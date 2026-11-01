package io.github.bardesss.wick

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.bardesss.wick.api.FaviconAuth
import io.github.bardesss.wick.api.GReaderApi
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.data.KeystoreCredentialStore
import io.github.bardesss.wick.data.KeystoreTokenCipher
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.fake.FakeServerMode
import io.github.bardesss.wick.sync.MarkQueue
import io.github.bardesss.wick.sync.SyncEngine
import io.github.bardesss.wick.ui.settings.AccountSwitch
import io.github.bardesss.wick.ui.settings.wipeAccount
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit

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

    // a cap on the whole call, so a server that trickles its answer cannot hold a sync forever
    val http: OkHttpClient by lazy { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }

    val api: ReaderApi by lazy {
        if (BuildConfig.FAKE_ENABLED && FakeServerMode.active) FakeServerMode.Api() else GReaderApi(http) { creds.auth() }
    }
    val markQueue: MarkQueue by lazy { MarkQueue(api, queueDao, itemDao, appScope, inTransaction = { block -> db.withTransaction { block() } })
    }
    val syncEngine: SyncEngine by lazy { SyncEngine(api, db, markQueue) }

    val accountSwitch: AccountSwitch by lazy {
        AccountSwitch(settings) { wipeAccount(markQueue, db, settings, clearImages = ::clearImages) }
    }

    /** The view shown last time, read for each session so the first frame can have the right header. */
    fun startView(): String? = runBlocking { settings.lastView() }

    val imageLoader: ImageLoader by lazy {
        // shares the connection pool with the API client, but only favicon requests get the key
        val client = http.newBuilder()
            .addInterceptor(FaviconAuth { creds.auth() })
            // lead images come from other hosts, which learn no more than that this is an Android app
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", IMAGE_USER_AGENT).build()) }
            .build()
        // the directory keeps its old name so an update reuses the cache
        buildImageLoader(context, client, context.cacheDir.resolve("favicons"))
    }

    /** Drops every cached image (favicons and lead images), in memory and on disk. Blocks on the disk, so call it off the main thread. */
    fun clearImages() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }
}

private const val IMAGE_USER_AGENT = "Mozilla/5.0 (Android) Wick"

/** Coil loader for favicons and lead images. Memory and disk caches are keyed by the plain image URL. */
fun buildImageLoader(context: Context, client: OkHttpClient, diskDir: File): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.05).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(diskDir.toOkioPath())
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .build()
