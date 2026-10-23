package com.mhrsdev.musicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Single Room database for the local library and user data.
 *
 * Migration strategy: schemas are exported to `app/schemas/` (see `room.schemaLocation` in build.gradle.kts)
 * and committed. Every version bump MUST add a [Migration] to [MIGRATIONS]; destructive fallback is NOT enabled
 * because likes, playlists and history are user data. Scanner-derived tables (track/album/artist) could be
 * recreated by a rescan, but their ids are referenced by user data, so we migrate rather than drop.
 */
@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        LikedTrackEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun likeDao(): LikeDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "music_player.db"

        /** Append new migrations here (oldest first). Empty while the schema is at version 1. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
