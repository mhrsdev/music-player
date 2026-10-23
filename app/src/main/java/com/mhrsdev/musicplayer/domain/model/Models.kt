package com.mhrsdev.musicplayer.domain.model

/**
 * Pure Kotlin domain models. No Android / Compose imports allowed in `domain/`.
 *
 * Artwork: [artworkUri] is a content/file/http URI (nullable). When it is null the UI renders a generated
 * gradient placeholder derived from [placeholderSeed] (stable per album/artist), so every model that shows
 * artwork carries a seed.
 */
data class Track(
    val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String,
    val albumTitle: String,
    val durationMs: Long,
    val artworkUri: String? = null,
    val placeholderSeed: Int = 0,
    /** Playable URI (content://, file://, https://). Null for fakes. */
    val mediaUri: String? = null,
    val isExplicit: Boolean = false,
    val isLiked: Boolean = false,
    val year: Int? = null,
    val trackNumber: Int? = null,
    /** Epoch millis when the track was added to the library (drives "Recently saved"/"New releases"). */
    val addedAtMs: Long = 0L,
)

data class Album(
    val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val year: Int? = null,
    val trackCount: Int = 0,
    val artworkUri: String? = null,
    val placeholderSeed: Int = 0,
)

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val imageUri: String? = null,
    val placeholderSeed: Int = 0,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
    val description: String? = null,
    /** Up to 4 artwork URIs for a mosaic cover; may be empty (use [placeholderSeed]). */
    val artworkUris: List<String> = emptyList(),
    val placeholderSeed: Int = 0,
)

/** One lyric line. For unsynced lyrics [timeMs] is [UNSYNCED]. */
data class LyricLine(val timeMs: Long, val text: String) {
    companion object {
        const val UNSYNCED: Long = -1L
    }
}

data class Lyrics(
    val trackId: String,
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    /** Free text such as "Embedded", "LRC file", "LRCLIB". */
    val source: String? = null,
) {
    /**
     * Index of the line that is active at [positionMs] (last line whose timeMs <= positionMs),
     * or -1 before the first line / for unsynced lyrics.
     */
    fun activeLineIndex(positionMs: Long): Int {
        if (!isSynced) return -1
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

enum class RepeatMode { OFF, ALL, ONE }

sealed interface SleepTimer {
    data object Off : SleepTimer

    /** Stops playback when [remainingMs] reaches 0. */
    data class Countdown(val remainingMs: Long, val totalMs: Long) : SleepTimer

    /** Stops playback when the current track ends. */
    data object EndOfTrack : SleepTimer
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    /** Index of [currentTrack] in [queue], -1 when the queue is empty. */
    val currentIndex: Int = -1,
    val sleepTimer: SleepTimer = SleepTimer.Off,
    /** Human label for the queue origin, e.g. "NEFFEX" or "Liked songs" (the "Playing from" chip). */
    val playingFrom: String? = null,
    /** True right after [com.mhrsdev.musicplayer.domain.repository.PlayerController.shuffleQueue]; drives the Undo snackbar. */
    val canUndoShuffle: Boolean = false,
) {
    val hasNext: Boolean get() = currentIndex in 0 until queue.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
    /** Sum of the durations of tracks after the current one plus remaining time of the current one. */
    val remainingQueueMs: Long
        get() = if (currentIndex < 0) 0L else
            (durationMs - positionMs).coerceAtLeast(0L) +
                queue.drop(currentIndex + 1).sumOf { it.durationMs }
}

enum class HomeSectionStyle { HERO_CAROUSEL, CAROUSEL, LIST }

sealed interface HomeItem {
    data class OfTrack(val track: Track) : HomeItem
    data class OfAlbum(val album: Album) : HomeItem
    data class OfPlaylist(val playlist: Playlist) : HomeItem
}

data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val style: HomeSectionStyle,
    val items: List<HomeItem>,
)

/** Chips on the Search screen. [ALL] = no filter. */
enum class SearchFilter { ALL, SONGS, ALBUMS, ARTISTS, PLAYLISTS }

sealed interface SearchResult {
    data class OfTrack(val track: Track) : SearchResult
    data class OfAlbum(val album: Album) : SearchResult
    data class OfArtist(val artist: Artist) : SearchResult
    data class OfPlaylist(val playlist: Playlist) : SearchResult
}
