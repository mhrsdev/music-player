package com.mhrsdev.musicplayer.data.scan

import androidx.room.withTransaction
import com.mhrsdev.musicplayer.data.db.AlbumEntity
import com.mhrsdev.musicplayer.data.db.AppDatabase
import com.mhrsdev.musicplayer.data.db.ArtistEntity
import com.mhrsdev.musicplayer.data.db.TrackEntity
import com.mhrsdev.musicplayer.data.search.TextNormalizer

object ArtworkUris {
    /** Album-art URI served by MediaStore (works with Coil / ContentResolver on all supported API levels). */
    fun albumArt(mediaStoreAlbumId: Long): String? =
        if (mediaStoreAlbumId > 0) "content://media/external/audio/albumart/$mediaStoreAlbumId" else null
}

object TrackMapper {
    const val UNKNOWN_ARTIST = "Unknown artist"
    const val UNKNOWN_ALBUM = "Unknown album"
    private val EXPLICIT_MARK = Regex("[(\\[]\\s*explicit\\s*[)\\]]", RegexOption.IGNORE_CASE)

    fun toEntity(a: ScannedAudio): TrackEntity {
        val title = a.title?.takeIf { it.isNotBlank() }
            ?: a.displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: "Track ${a.mediaStoreId}"
        val artist = cleanName(a.artist, UNKNOWN_ARTIST)
        val album = cleanName(a.album, UNKNOWN_ALBUM)
        val artistId = if (a.artistId > 0) "ms-artist-${a.artistId}" else "ms-artist-n-${TextNormalizer.normalize(artist)}"
        val albumId = if (a.albumId > 0) "ms-album-${a.albumId}" else "ms-album-n-${TextNormalizer.normalize(album)}"
        return TrackEntity(
            id = "ms-${a.mediaStoreId}",
            title = title,
            artistId = artistId,
            artistName = artist,
            albumId = albumId,
            albumTitle = album,
            durationMs = a.durationMs,
            artworkUri = ArtworkUris.albumArt(a.albumId),
            mediaUri = a.contentUri,
            isExplicit = a.isExplicit || EXPLICIT_MARK.containsMatchIn(title),
            year = a.year.takeIf { it in 1000..3000 },
            trackNumber = (a.track % 1000).takeIf { it > 0 },
            addedAtMs = a.dateAddedMs,
            modifiedAtMs = a.dateModifiedMs,
            titleKey = TextNormalizer.normalize(title),
            searchKey = TextNormalizer.normalize("$title $artist $album"),
        )
    }

    private fun cleanName(raw: String?, fallback: String): String {
        val v = raw?.trim().orEmpty()
        return if (v.isEmpty() || v.equals("<unknown>", ignoreCase = true)) fallback else v
    }
}

/**
 * Applies a scan result to the database incrementally: only new/modified files are rewritten, missing
 * files are removed, then derived album/artist rows are rebuilt if anything changed. One transaction.
 */
class LibrarySyncer(private val db: AppDatabase) {
    suspend fun sync(scanned: List<ScannedAudio>): SyncResult = db.withTransaction {
        val tracks = db.trackDao()
        val existing = tracks.stamps().associate { it.id to it.modifiedAtMs }
        val incoming = scanned.map(TrackMapper::toEntity).distinctBy { it.id }
        val incomingIds = incoming.mapTo(HashSet()) { it.id }

        val toWrite = incoming.filter { existing[it.id] != it.modifiedAtMs }
        val added = toWrite.count { it.id !in existing }
        val updated = toWrite.size - added
        val removedIds = existing.keys.filter { it !in incomingIds }

        if (toWrite.isNotEmpty()) tracks.upsert(toWrite)
        removedIds.chunked(500).forEach { tracks.deleteByIds(it) }

        val result = SyncResult(added, updated, removedIds.size, incoming.size)
        if (result.changed) rebuildDerived(tracks.getAll())
        result
    }

    private suspend fun rebuildDerived(all: List<TrackEntity>) {
        val albumGroups = all.groupBy { it.albumId }
        val albums = albumGroups.map { (id, ts) ->
            val first = ts.first()
            val artist = ts.groupingBy { it.artistId }.eachCount().maxByOrNull { it.value }!!.key
            val artistName = ts.first { it.artistId == artist }.artistName
            AlbumEntity(
                id = id,
                title = first.albumTitle,
                artistId = artist,
                artistName = artistName,
                year = ts.mapNotNull { it.year }.maxOrNull(),
                trackCount = ts.size,
                artworkUri = ts.firstNotNullOfOrNull { it.artworkUri },
                titleKey = TextNormalizer.normalize(first.albumTitle),
                searchKey = TextNormalizer.normalize("${first.albumTitle} $artistName"),
            )
        }
        val artists = all.groupBy { it.artistId }.map { (id, ts) ->
            ArtistEntity(
                id = id,
                name = ts.first().artistName,
                albumCount = ts.map { it.albumId }.distinct().size,
                trackCount = ts.size,
                imageUri = ts.firstNotNullOfOrNull { it.artworkUri },
                nameKey = TextNormalizer.normalize(ts.first().artistName),
            )
        }
        db.albumDao().clear()
        db.artistDao().clear()
        db.albumDao().insertAll(albums)
        db.artistDao().insertAll(artists)
    }
}
