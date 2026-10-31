package io.github.bardesss.wick

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.bardesss.wick.sync.SyncWork
import io.github.bardesss.wick.sync.SyncWorkerFactory
import kotlinx.coroutines.launch

class WickApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                SyncWorkerFactory(
                    auth = { container.creds.auth() },
                    queue = { container.markQueue },
                    engine = { container.syncEngine },
                ),
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val wm = WorkManager.getInstance(this)
        SyncWork.schedule(wm)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.appScope.launch { SyncWork.enqueueFlushIfPending(wm, container.queueDao) }
            }
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}
