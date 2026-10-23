package com.mhrsdev.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

private const val TRACK_ROW =
    "SELECT t.*, (l.trackId IS NOT NULL) AS isLiked FROM track t LEFT JOIN liked_track l ON l.trackId = t.id"

private const val PLAYLIST_ROW =
    "SELECT p.id, p.name, p.description, " +
        "(SELECT COUNT(*) FROM playlist_track pt JOIN track t ON t.id = pt.trackId WHERE pt.playlistId = p.id) AS trackCount " +
        "FROM playlist p"

@Dao
interface TrackDao {
    @Query("$TRACK_ROW ORDER BY t.titleKey")
    fun observeAll(): Flow<List<TrackRow>>

    @Query("$TRACK_ROW WHERE l.trackId IS NOT NULL ORDER BY l.likedAtMs DESC")
    fun observeLiked(): Flow<List<TrackRow>>

    @Query("$TRACK_ROW ORDER BY t.addedAtMs DESC, t.titleKey LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<TrackRow>>

    @Query(
        "$TRACK_ROW JOIN (SELECT trackId, MAX(id) AS lastPlay FROM play_history GROUP BY trackId) h " +
            "ON h.trackId = t.id ORDER BY h.lastPlay DESC LIMIT :limit",
    )
    fun observeHistory(limit: Int): Flow<List<TrackRow>>

    @Query(
        "$TRACK_ROW JOIN (SELECT trackId, COUNT(*) AS plays FROM play_history GROUP BY trackId) h " +
            "ON h.trackId = t.id ORDER BY h.plays DESC, t.titleKey LIMIT :limit",
    )
    fun observeMostPlayed(limit: Int): Flow<List<TrackRow>>

    @Query("$TRACK_ROW WHERE t.albumId = :albumId ORDER BY t.trackNumber IS NULL, t.trackNumber, t.titleKey")
    fun observeByAlbum(albumId: String): Flow<List<TrackRow>>

    @Query("$TRACK_ROW WHERE t.artistId = :artistId ORDER BY t.albumTitle, t.trackNumber IS NULL, t.trackNumber, t.titleKey")
    fun observeByArtist(artistId: String): Flow<List<TrackRow>>

    @Query("$TRACK_ROW JOIN playlist_track pt ON pt.trackId = t.id WHERE pt.playlistId = :playlistId ORDER BY pt.position")
    fun observeByPlaylist(playlistId: String): Flow<List<TrackRow>>

    @Query("SELECT id, modifiedAtMs FROM track")
    suspend fun stamps(): List<TrackStamp>

    @Query("SELECT * FROM track")
    suspend fun getAll(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("DELETE FROM track WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album ORDER BY titleKey")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album WHERE id = :id")
    fun observe(id: String): Flow<AlbumEntity?>

    @Query("DELETE FROM album")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<AlbumEntity>)
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artist ORDER BY nameKey")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artist WHERE id = :id")
    fun observe(id: String): Flow<ArtistEntity?>

    @Query("DELETE FROM artist")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<ArtistEntity>)
}

@Dao
interface PlaylistDao {
    @Query("$PLAYLIST_ROW ORDER BY p.createdAtMs DESC, p.name")
    fun observeAll(): Flow<List<PlaylistRow>>

    @Query("$PLAYLIST_ROW WHERE p.id = :id")
    fun observe(id: String): Flow<PlaylistRow?>

    /** Artwork of playlist members in playlist order (callers take the first distinct 4 per playlist). */
    @Query(
        "SELECT pt.playlistId AS playlistId, t.artworkUri AS artworkUri FROM playlist_track pt " +
            "JOIN track t ON t.id = pt.trackId WHERE t.artworkUri IS NOT NULL ORDER BY pt.playlistId, pt.position",
    )
    fun observeCovers(): Flow<List<PlaylistCoverRow>>

    @Query("SELECT nameKey FROM playlist")
    fun observeNameKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(ref: PlaylistTrackCrossRef): Long

    @Query("SELECT COUNT(*) FROM playlist WHERE id = :id")
    suspend fun exists(id: String): Int

    @Transaction
    suspend fun addTrack(playlistId: String, trackId: String, nowMs: Long) {
        if (exists(playlistId) == 0) return
        insertTrack(PlaylistTrackCrossRef(playlistId, trackId, nextPosition(playlistId), nowMs))
    }
}

@Dao
interface LikeDao {
    @Query("SELECT COUNT(*) FROM liked_track WHERE trackId = :trackId")
    suspend fun isLiked(trackId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(entity: LikedTrackEntity)

    @Query("DELETE FROM liked_track WHERE trackId = :trackId")
    suspend fun unlike(trackId: String)

    @Transaction
    suspend fun toggle(trackId: String, nowMs: Long) {
        if (isLiked(trackId) > 0) unlike(trackId) else like(LikedTrackEntity(trackId, nowMs))
    }
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: PlayHistoryEntity)

    /** Keeps only the newest [keep] plays. */
    @Query("DELETE FROM play_history WHERE id NOT IN (SELECT id FROM play_history ORDER BY id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Transaction
    suspend fun record(trackId: String, nowMs: Long, keep: Int = 5000) {
        insert(PlayHistoryEntity(trackId = trackId, playedAtMs = nowMs))
        prune(keep)
    }
}
