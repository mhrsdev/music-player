package com.mhrsdev.musicplayer.fake;

import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic in-memory catalog for UI work, tests and screenshots: 26 real, freely licensed tracks (CC0, CC BY, CC BY-SA
 * or public domain) by 8 artists on 9 albums, incl. two Persian items. Only the metadata and the cover art are used (no
 * audio ships); licences and attribution: docs/CREDITS.md, audio re-download + checksums: tools/sample-data/fetch.py.
 * Covers are debug-only assets served by the debug {@code SampleCoverProvider}; without it (release) they fall back
 * to the placeholder gradient.
 */
public final class SampleData {
    private static final long DAY = 86_400_000L;
    /** Fixed "now" so sorting by addedAtMs is deterministic (the added order interleaves albums: order * 11 mod 29). */
    public static final long NOW_MS = 1_790_000_000_000L;
    /** Authority of the debug-only provider that serves app/src/debug/assets/sample-covers/. */
    private static final String COVER_BASE = "content://com.mhrsdev.musicplayer.samplecovers/";

    /** Album spec: tracks are "title|seconds|trackNumber" (trackNumber 0 = untagged); cover is a file in sample-covers/. */
    private record AlbumSpec(String artist, String title, int year, String cover, String... tracks) { }

    private static final List<AlbumSpec> SPECS = List.of(
            new AlbumSpec("Josh Woodward", "Reject Bin", 2024, "reject-bin.jpg", "Texas|260|1", "1000 Words|193|2",
                    "Keep In Touch|275|3", "IOU|247|4", "Skyline of the Sea|256|19"),
            new AlbumSpec("Chris Zabriskie", "O Great Queen Electric, What Do You Have Waiting for Me?", 2008,
                    "o-great-queen-electric.jpg", "I Can Be Your Love|244|2", "We Start the Cure in Paris|354|3",
                    "If Only She Knew|266|5", "God's Will|249|7"),
            new AlbumSpec("Kimiko Ishizaka", "The Open Goldberg Variations", 2012, "goldberg.jpg", "Aria|300|1",
                    "Variatio 1 a 1 Clav.|116|2", "Variatio 2 a 1 Clav.|124|3"),
            new AlbumSpec("Loyalty Freak Music", "Instrumental R&B Beats to Sing or Rap On", 2018, "rnb-beats.jpg",
                    "Sunglasses|170|1", "Sensual Melancholia|275|3", "Dirty Shoes Blues|184|4", "The Candle|248|5"),
            new AlbumSpec("Loyalty Freak Music", "Positive Attitude!", 2017, "positive-attitude.jpg",
                    "Go to the Picnic|187|1", "Hug Convoy|201|2"),
            new AlbumSpec("Broke For Free", "Directionless EP", 2011, "directionless-ep.jpg", "Night Owl|194|1",
                    "My Always Mood|189|2", "Day Bird|223|3"),
            new AlbumSpec("Kevin MacLeod", "Royalty Free", 2010, "royalty-free.jpg", "Sneaky Snitch|137|0",
                    "Carefree|205|0", "Wallpaper|220|0"),
            new AlbumSpec("آریا طبیب‌زاده", "دختران قوچان", 2023, "dokhtaran-quchan.jpg", "دختران قوچان|180|0"),
            new AlbumSpec("روح‌الله خالقی", "ای ایران", 2017, "ey-iran.jpg", "ای ایران (سنتور)|120|0"));

    public static final List<Artist> ARTISTS;
    public static final List<Album> ALBUMS;
    public static final List<Track> TRACKS;

    static {
        List<Artist> artists = new ArrayList<>();
        List<Album> albums = new ArrayList<>();
        List<Track> tracks = new ArrayList<>();
        int order = 0;
        for (AlbumSpec spec : SPECS) {
            String artistId = "artist-" + Integer.toHexString(spec.artist().hashCode());
            String albumId = "album-" + Integer.toHexString(spec.artist().hashCode()) + "-"
                    + Integer.toHexString(spec.title().hashCode());
            String cover = COVER_BASE + spec.cover();
            int seed = spec.title().hashCode();
            for (String trackSpec : spec.tracks()) {
                String[] t = trackSpec.split("\\|");
                order++;
                tracks.add(new Track("track-" + order, t[0], artistId, spec.artist(), albumId, spec.title(),
                        Integer.parseInt(t[1]) * 1000L, cover, seed, null, false, false,
                        spec.year(), Integer.parseInt(t[2]), NOW_MS - order * 11 % 29 * DAY / 2));
            }
            albums.add(new Album(albumId, spec.title(), artistId, spec.artist(), spec.year(),
                    spec.tracks().length, cover, seed));
            int existing = -1;
            for (int i = 0; i < artists.size(); i++) if (artists.get(i).id().equals(artistId)) existing = i;
            if (existing >= 0) {
                Artist a = artists.get(existing);
                artists.set(existing, new Artist(a.id(), a.name(), a.albumCount() + 1,
                        a.trackCount() + spec.tracks().length, a.imageUri(), a.placeholderSeed()));
            } else {
                artists.add(new Artist(artistId, spec.artist(), 1, spec.tracks().length, cover,
                        spec.artist().hashCode()));
            }
        }
        ARTISTS = Collections.unmodifiableList(artists);
        ALBUMS = Collections.unmodifiableList(albums);
        TRACKS = Collections.unmodifiableList(tracks);
    }

    /** Ids of tracks liked on first launch. */
    public static final Set<String> INITIALLY_LIKED_IDS =
            Set.of("track-1", "track-2", "track-6", "track-10", "track-13", "track-19", "track-25");

    /** Seed playlists in display order: playlist -> its track ids. */
    public static final Map<Playlist, List<String>> SEED_PLAYLISTS;

    static {
        Map<Playlist, List<String>> m = new LinkedHashMap<>();
        put(m, "playlist-focus", "Piano & beats for focus", "Calm and instrumental", 11,
                "track-10", "track-11", "track-12", "track-14", "track-16", "track-24");
        put(m, "playlist-road", "Sunday road trip", "Easy songs for the open road", 22,
                "track-1", "track-3", "track-4", "track-5", "track-17", "track-19", "track-23");
        put(m, "playlist-night", "Late night listening", "Slow, warm and a little dark", 33,
                "track-6", "track-7", "track-8", "track-9", "track-15", "track-20");
        put(m, "playlist-fa", "فارسی", "Persian favorites", 44, "track-25", "track-26");
        SEED_PLAYLISTS = Collections.unmodifiableMap(m);
    }

    /** Track ids in the initial listening history, most recent first. */
    public static final List<String> SEED_HISTORY_IDS =
            List.of("track-2", "track-6", "track-1", "track-14", "track-10", "track-19", "track-25", "track-22");

    /** Adds a playlist whose mosaic cover is the (up to 4) distinct album covers of its tracks. */
    private static void put(Map<Playlist, List<String>> m, String id, String name, String description, int seed,
                            String... trackIds) {
        Set<String> covers = new LinkedHashSet<>();
        for (String trackId : trackIds) {
            for (Track t : TRACKS) if (t.id().equals(trackId)) covers.add(t.artworkUri());
        }
        m.put(new Playlist(id, name, 0, description, new ArrayList<>(covers).subList(0, Math.min(4, covers.size())), seed),
                List.of(trackIds));
    }

    private static final String[] ENGLISH_TIMES_AND_LINES = {
            "0|", "12000|Streetlights flicker on the avenue", "17500|I carry the weight and I carry the view",
            "23000|Every step is a promise I make to myself", "28500|Nobody's counting but I still keep score",
            "34000|So turn it up, let the engine run loud", "40000|We're building a skyline out of the doubt",
            "46000|Hold the line, hold the line", "52000|The morning is coming, it's almost time",
            "60000|", "64000|Footsteps echo in a quiet town", "70000|I wrote my name where the rain came down",
            "76000|Every scar is a map to where I've been", "82000|And I'm not afraid to begin again",
            "90000|Turn it up, let the engine run loud", "96000|We're building a skyline out of the doubt",
            "102000|Hold the line, hold the line", "108000|The morning is here and it's burning bright", "118000|"};

    private static final String[] PERSIAN_TIMES_AND_LINES = {
            "0|", "10000|از کوه‌ها صدای باد می‌آید", "18000|دخترانی با گل‌ها و آفتاب",
            "26000|راه دور است و دل ما روشن", "34000|آواز ما تا صبح بیدار می‌ماند", "44000|"};

    private static List<LyricLine> lines(String[] timesAndLines, boolean synced, boolean skipBlank) {
        List<LyricLine> out = new ArrayList<>();
        for (String s : timesAndLines) {
            int bar = s.indexOf('|');
            String text = s.substring(bar + 1);
            if (skipBlank && text.isEmpty()) continue;
            out.add(new LyricLine(synced ? Long.parseLong(s.substring(0, bar)) : LyricLine.UNSYNCED, text));
        }
        return out;
    }

    /** Instrumentals: the piano (10-12), beats (13-16), Kevin MacLeod (22-24) and santoor (26) tracks. */
    private static final Set<String> INSTRUMENTAL_IDS = Set.of("track-10", "track-11", "track-12", "track-13",
            "track-14", "track-15", "track-16", "track-22", "track-23", "track-24", "track-26");

    /**
     * Placeholder lyrics written for the UI (they are not the songs' real lyrics): track-3 has plain (unsynced) lyrics,
     * instrumentals have none, the Persian song (track-25) has synced Persian lyrics, every other track has synced
     * English lyrics.
     */
    public static Lyrics lyricsFor(String trackId) {
        if (INSTRUMENTAL_IDS.contains(trackId)) return Lyrics.none(trackId);
        switch (trackId) {
            case "track-3":
                return new Lyrics(trackId, lines(ENGLISH_TIMES_AND_LINES, false, true), false, "Embedded");
            case "track-25":
                return new Lyrics(trackId, lines(PERSIAN_TIMES_AND_LINES, true, false), true, "LRC file");
            default:
                return new Lyrics(trackId, lines(ENGLISH_TIMES_AND_LINES, true, false), true, "LRCLIB");
        }
    }

    private SampleData() { }
}
