package com.mhrsdev.musicplayer.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scanned audio file. Rebuilt/updated by the MediaStore scanner; user data (likes, history, playlist
 * membership) lives in separate tables keyed by [id] WITHOUT foreign keys to this table so a rescan that
 * temporarily drops a file never wipes the user's data. Joins filter out dangling ids.
 *
 * [id] is `ms-<MediaStore _ID>`. `*Key` columns hold [com.mhrsdev.musicplayer.data.search.TextNormalizer] output.
 */
@Entity(
    tableName = "track",
    indices = [Index("albumId"), Index("artistId"), Index("addedAtMs")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String,
    val albumTitle: String,
    val durationMs: Long,
    val artworkUri: String?,
    val mediaUri: String,
    val isExplicit: Boolean,
    val year: Int?,
    val trackNumber: Int?,
    val addedAtMs: Long,
    /** MediaStore DATE_MODIFIED (ms) — used for incremental rescans. */
    val modifiedAtMs: Long,
    val titleKey: String,
    val searchKey: String,
)

@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val year: Int?,
    val trackCount: Int,
    val artworkUri: String?,
    val titleKey: String,
    val searchKey: String,
)

@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val imageUri: String?,
    val nameKey: String,
)

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val createdAtMs: Long,
    val nameKey: String,
)

@Entity(
    tableName = "playlist_track",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAtMs: Long,
)

@Entity(tableName = "liked_track")
data class LikedTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAtMs: Long,
)

@Entity(tableName = "play_history", indices = [Index("trackId"), Index("playedAtMs")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAtMs: Long,
)

/** Track joined with its like flag. */
data class TrackRow(
    @Embedded val track: TrackEntity,
    val isLiked: Boolean,
)

data class PlaylistRow(
    val id: String,
    val name: String,
    val description: String?,
    val trackCount: Int,
)

data class PlaylistCoverRow(
    val playlistId: String,
    val artworkUri: String,
)

data class TrackStamp(val id: String, val modifiedAtMs: Long)
