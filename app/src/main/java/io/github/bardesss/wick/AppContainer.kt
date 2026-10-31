package io.github.bardesss.wick

import android.content.Context
import androidx.room.Room
import io.github.bardesss.wick.db.WickDb

class AppContainer(val context: Context) {
    val db: WickDb by lazy {
        Room.databaseBuilder(context, WickDb::class.java, "wick.db").build()
    }
    val itemDao get() = db.itemDao()
    val feedDao get() = db.feedDao()
    val queueDao get() = db.queueDao()
}
