package com.mhrsdev.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Minimal Room placeholder so KSP + schema export are exercised by the build.
 * T5 replaces this with the real schema (tracks, playlists, history, likes). Not wired into DI yet.
 */
@Entity(tableName = "liked_track")
data class LikedTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAtMs: Long,
)

@Dao
interface LikedTrackDao {
    @Query("SELECT * FROM liked_track ORDER BY likedAtMs DESC")
    fun observeAll(): Flow<List<LikedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LikedTrackEntity)

    @Query("DELETE FROM liked_track WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}

@Database(entities = [LikedTrackEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedTrackDao(): LikedTrackDao
}
