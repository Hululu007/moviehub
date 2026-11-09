package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val normKey: String,
    val name: String,
    val pic: String,
    val remarks: String,
    val sourceId: String,
    val sourceName: String,
    val vodId: String,
    val lineIndex: Int,
    val epIndex: Int,
    val epTitle: String,
    val epUrl: String,
    val positionSec: Long,
    val durationSec: Long,
    val multiSourceCount: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "music_playlist")
data class PlaylistTrackEntity(
    @PrimaryKey val uniqueKey: String,
    val id: String,
    val platform: String,
    val name: String,
    val singer: String,
    val album: String,
    val duration: Int,
    val pic: String,
    val hash: String,
    val albumId: String,
    val rid: String,
    val directUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val keyword: String,
    val scope: String = "vod", // "vod" or "music"
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "category_cache")
data class CategoryCacheEntity(
    @PrimaryKey val srcId: String,
    val treeJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vod_favorites")
data class FavoriteVodEntity(
    @PrimaryKey val normKey: String,
    val name: String,
    val pic: String,
    val remarks: String,
    val year: String,
    val sourceId: String,
    val sourceName: String,
    val vodId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface MovieHubDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT 20")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchHistory(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE normKey = :normKey")
    suspend fun deleteWatchHistory(normKey: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllWatchHistory()

    @Query("SELECT * FROM vod_favorites ORDER BY addedAt DESC")
    fun getFavoriteVods(): Flow<List<FavoriteVodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavoriteVod(item: FavoriteVodEntity)

    @Query("DELETE FROM vod_favorites WHERE normKey = :normKey")
    suspend fun deleteFavoriteVod(normKey: String)

    @Query("SELECT * FROM music_playlist ORDER BY addedAt ASC")
    fun getPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM music_playlist WHERE uniqueKey = :uniqueKey")
    suspend fun deletePlaylistTrack(uniqueKey: String)

    @Query("DELETE FROM music_playlist")
    suspend fun clearPlaylist()

    @Query("SELECT * FROM recent_searches WHERE scope = :scope ORDER BY updatedAt DESC LIMIT 12")
    fun getRecentSearches(scope: String = "vod"): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE scope = :scope")
    suspend fun clearRecentSearches(scope: String = "vod")

    @Query("SELECT * FROM category_cache WHERE srcId = :srcId LIMIT 1")
    suspend fun getCategoryCache(srcId: String): CategoryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCategoryCache(cache: CategoryCacheEntity)
}

@Database(
    entities = [
        WatchHistoryEntity::class,
        FavoriteVodEntity::class,
        PlaylistTrackEntity::class,
        RecentSearchEntity::class,
        CategoryCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieHubDao(): MovieHubDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moviehub_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
