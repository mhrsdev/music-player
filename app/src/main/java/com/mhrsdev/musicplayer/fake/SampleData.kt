package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.Album
import com.mhrsdev.musicplayer.domain.model.Artist
import com.mhrsdev.musicplayer.domain.model.LyricLine
import com.mhrsdev.musicplayer.domain.model.Lyrics
import com.mhrsdev.musicplayer.domain.model.Playlist
import com.mhrsdev.musicplayer.domain.model.Track

/** Realistic in-memory catalog for UI work and tests. Deterministic (no randomness, no clock). */
object SampleData {
    private const val DAY = 86_400_000L
    /** Fixed "now" so sorting by addedAtMs is deterministic. */
    const val NOW_MS: Long = 1_790_000_000_000L

    private data class AlbumSpec(
        val artist: String,
        val title: String,
        val year: Int,
        val tracks: List<Triple<String, Int, Boolean>>, // title, duration seconds, explicit
    )

    private val specs = listOf(
        AlbumSpec("NEFFEX", "Best of Me", 2018, listOf(
            Triple("Best of Me", 191, false), Triple("Grateful", 183, false), Triple("Fight Back", 205, false),
            Triple("Cold", 192, false), Triple("Rise", 188, false), Triple("Careless", 176, false),
            Triple("Destiny", 179, false), Triple("Never Give Up", 198, false),
        )),
        AlbumSpec("NEFFEX", "Anxiety", 2020, listOf(
            Triple("Anxiety", 187, false), Triple("Hate", 168, true), Triple("Light It Up", 181, false),
            Triple("Dream On", 194, false), Triple("Walk It Off", 172, false),
        )),
        AlbumSpec("Aurora Lane", "Paper Lanterns", 2023, listOf(
            Triple("Paper Lanterns", 214, false), Triple("Slow Tide", 233, false), Triple("Glass Garden", 201, false),
            Triple("Last Train Home", 247, false), Triple("Honey & Smoke", 189, false),
        )),
        AlbumSpec("Midnight Atlas", "Northbound", 2022, listOf(
            Triple("Northbound", 262, false), Triple("Static Bloom", 218, false), Triple("Cartographer", 244, false),
            Triple("Neon Rain", 207, true), Triple("Afterglow Drive", 229, false), Triple("Low Orbit", 196, false),
        )),
        AlbumSpec("Kasra", "Concrete Gardens", 2024, listOf(
            Triple("Concrete Gardens", 176, true), Triple("Rooftop Weather", 193, false), Triple("Blue Hour", 205, false),
            Triple("Tape Hiss", 158, true), Triple("Late Bus", 184, false),
        )),
        AlbumSpec("Velvet Static", "Soft Machines", 2021, listOf(
            Triple("Soft Machines", 223, false), Triple("Dial Tone Lullaby", 198, false), Triple("Chrome Heart", 211, false),
            Triple("Overexposed", 187, false),
        )),
        AlbumSpec("سهراب نوری", "شب‌های تهران", 2019, listOf(
            Triple("شب‌های تهران", 238, false), Triple("باران بی‌وقفه", 221, false), Triple("خانه‌ی کوچک", 204, false),
            Triple("آخرین ایستگاه", 252, false),
        )),
    )

    private fun slug(s: String) = s.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-')
        .ifEmpty { "x" }

    val artists: List<Artist>
    val albums: List<Album>
    val tracks: List<Track>

    init {
        val artistList = mutableListOf<Artist>()
        val albumList = mutableListOf<Album>()
        val trackList = mutableListOf<Track>()
        var addedOrder = 0
        specs.forEach { spec ->
            val artistId = "artist-${spec.artist.hashCode().toUInt().toString(16)}"
            val albumId = "album-${spec.artist.hashCode().toUInt().toString(16)}-${slug(spec.title).hashCode().toUInt().toString(16)}"
            val seed = spec.title.hashCode()
            spec.tracks.forEachIndexed { i, (title, secs, explicit) ->
                addedOrder++
                trackList += Track(
                    id = "track-$addedOrder",
                    title = title,
                    artistId = artistId,
                    artistName = spec.artist,
                    albumId = albumId,
                    albumTitle = spec.title,
                    durationMs = secs * 1000L,
                    placeholderSeed = seed,
                    isExplicit = explicit,
                    isLiked = false,
                    year = spec.year,
                    trackNumber = i + 1,
                    addedAtMs = NOW_MS - addedOrder * DAY / 2,
                )
            }
            albumList += Album(
                id = albumId, title = spec.title, artistId = artistId, artistName = spec.artist,
                year = spec.year, trackCount = spec.tracks.size, placeholderSeed = seed,
            )
            val existing = artistList.indexOfFirst { it.id == artistId }
            if (existing >= 0) {
                val a = artistList[existing]
                artistList[existing] = a.copy(albumCount = a.albumCount + 1, trackCount = a.trackCount + spec.tracks.size)
            } else {
                artistList += Artist(
                    id = artistId, name = spec.artist, albumCount = 1, trackCount = spec.tracks.size,
                    placeholderSeed = spec.artist.hashCode(),
                )
            }
        }
        artists = artistList
        albums = albumList
        tracks = trackList
    }

    /** Ids of tracks liked on first launch. */
    val initiallyLikedIds: Set<String> = setOf("track-1", "track-2", "track-9", "track-14", "track-20", "track-27")

    /** Seed playlists: id -> (name, track ids). */
    val seedPlaylists: List<Pair<Playlist, List<String>>> = listOf(
        Playlist(id = "playlist-gym", name = "Gym motivation", description = "High energy", placeholderSeed = 11) to
            listOf("track-1", "track-3", "track-4", "track-5", "track-9", "track-10", "track-12"),
        Playlist(id = "playlist-night", name = "Late night drive", description = "Synth and slow tempo", placeholderSeed = 22) to
            listOf("track-17", "track-22", "track-23", "track-24", "track-25", "track-29"),
        Playlist(id = "playlist-fa", name = "فارسی", description = "Persian favorites", placeholderSeed = 33) to
            listOf("track-34", "track-35", "track-36", "track-37"),
    )

    /** Track ids in the initial listening history, most recent first. */
    val seedHistoryIds: List<String> = listOf("track-2", "track-14", "track-1", "track-22", "track-9", "track-27", "track-35", "track-5")

    private val syncedSample = listOf(
        0L to "", 12_000L to "Streetlights flicker on the avenue",
        17_500L to "I carry the weight and I carry the view",
        23_000L to "Every step is a promise I make to myself",
        28_500L to "Nobody's counting but I still keep score",
        34_000L to "So turn it up, let the engine run loud",
        40_000L to "We're building a skyline out of the doubt",
        46_000L to "Hold the line, hold the line",
        52_000L to "The morning is coming, it's almost time",
        60_000L to "", 64_000L to "Footsteps echo in a quiet town",
        70_000L to "I wrote my name where the rain came down",
        76_000L to "Every scar is a map to where I've been",
        82_000L to "And I'm not afraid to begin again",
        90_000L to "Turn it up, let the engine run loud",
        96_000L to "We're building a skyline out of the doubt",
        102_000L to "Hold the line, hold the line",
        108_000L to "The morning is here and it's burning bright",
        118_000L to "",
    )

    private val persianSample = listOf(
        0L to "", 10_000L to "شب‌های تهران بوی باران می‌دهد",
        18_000L to "کوچه‌ها آرام و چراغ‌ها بیدار",
        26_000L to "من هنوز اینجا منتظر صبحم",
        34_000L to "با دلی که هیچ‌وقت خسته نمی‌شود",
        44_000L to "",
    )

    /** track-3 has plain (unsynced) lyrics, track-4 has none, Persian tracks have synced Persian lyrics. */
    fun lyricsFor(trackId: String): Lyrics? = when (trackId) {
        "track-4" -> null
        "track-3" -> Lyrics(
            trackId, syncedSample.filter { it.second.isNotEmpty() }.map { LyricLine(LyricLine.UNSYNCED, it.second) },
            isSynced = false, source = "Embedded",
        )
        "track-34", "track-35", "track-36", "track-37" ->
            Lyrics(trackId, persianSample.map { LyricLine(it.first, it.second) }, isSynced = true, source = "LRC file")
        else -> Lyrics(trackId, syncedSample.map { LyricLine(it.first, it.second) }, isSynced = true, source = "LRCLIB")
    }
}
