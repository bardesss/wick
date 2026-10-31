package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertFeeds(feeds: List<FeedEntity>)

    @Query("DELETE FROM FeedEntity")
    protected abstract suspend fun clearFeeds()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCounts(counts: List<CountEntity>)

    @Query("DELETE FROM CountEntity")
    protected abstract suspend fun clearCounts()

    @Query("SELECT * FROM CountEntity")
    protected abstract fun countRows(): Flow<List<CountEntity>>

    @Query("SELECT * FROM FeedEntity ORDER BY title COLLATE NOCASE")
    abstract fun all(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM FeedEntity")
    abstract suspend fun feedsNow(): List<FeedEntity>

    fun counts(): Flow<Map<String, Int>> =
        countRows().map { rows -> rows.associate { it.streamId to it.count } }

    @Transaction
    open suspend fun replaceAll(feeds: List<FeedEntity>) {
        clearFeeds()
        insertFeeds(feeds)
    }

    @Transaction
    open suspend fun replaceCounts(list: List<CountEntity>) {
        clearCounts()
        insertCounts(list)
    }
}
