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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic in-memory catalog for UI work and tests (37 tracks, 7 artists incl. NEFFEX and a Persian one). */
public final class SampleData {
    private static final long DAY = 86_400_000L;
    /** Fixed "now" so sorting by addedAtMs is deterministic. */
    public static final long NOW_MS = 1_790_000_000_000L;

    /** Album spec: tracks are "title|seconds" with an optional "|x" suffix for explicit. */
    private record AlbumSpec(String artist, String title, int year, String... tracks) { }

    private static final List<AlbumSpec> SPECS = List.of(
            new AlbumSpec("NEFFEX", "Best of Me", 2018, "Best of Me|191", "Grateful|183", "Fight Back|205",
                    "Cold|192", "Rise|188", "Careless|176", "Destiny|179", "Never Give Up|198"),
            new AlbumSpec("NEFFEX", "Anxiety", 2020, "Anxiety|187", "Hate|168|x", "Light It Up|181",
                    "Dream On|194", "Walk It Off|172"),
            new AlbumSpec("Aurora Lane", "Paper Lanterns", 2023, "Paper Lanterns|214", "Slow Tide|233",
                    "Glass Garden|201", "Last Train Home|247", "Honey & Smoke|189"),
            new AlbumSpec("Midnight Atlas", "Northbound", 2022, "Northbound|262", "Static Bloom|218",
                    "Cartographer|244", "Neon Rain|207|x"),
            new AlbumSpec("Orchid Hours", "Quiet Rooms", 2022, "Afterglow Drive|229", "Low Orbit|196"),
            new AlbumSpec("Kasra", "Concrete Gardens", 2024, "Concrete Gardens|176|x", "Rooftop Weather|193",
                    "Blue Hour|205", "Tape Hiss|158|x", "Late Bus|184"),
            new AlbumSpec("Velvet Static", "Soft Machines", 2021, "Soft Machines|223", "Dial Tone Lullaby|198",
                    "Chrome Heart|211", "Overexposed|187"),
            new AlbumSpec("سهراب نوری", "شب‌های تهران", 2019, "شب‌های تهران|238", "باران بی‌وقفه|221",
                    "خانه‌ی کوچک|204", "آخرین ایستگاه|252"));

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
            int seed = spec.title().hashCode();
            for (int i = 0; i < spec.tracks().length; i++) {
                String[] t = spec.tracks()[i].split("\\|");
                order++;
                tracks.add(new Track("track-" + order, t[0], artistId, spec.artist(), albumId, spec.title(),
                        Integer.parseInt(t[1]) * 1000L, null, seed, null, t.length > 2, false,
                        spec.year(), i + 1, NOW_MS - order * DAY / 2));
            }
            albums.add(new Album(albumId, spec.title(), artistId, spec.artist(), spec.year(),
                    spec.tracks().length, null, seed));
            int existing = -1;
            for (int i = 0; i < artists.size(); i++) if (artists.get(i).id().equals(artistId)) existing = i;
            if (existing >= 0) {
                Artist a = artists.get(existing);
                artists.set(existing, new Artist(a.id(), a.name(), a.albumCount() + 1,
                        a.trackCount() + spec.tracks().length, null, a.placeholderSeed()));
            } else {
                artists.add(new Artist(artistId, spec.artist(), 1, spec.tracks().length, null,
                        spec.artist().hashCode()));
            }
        }
        ARTISTS = Collections.unmodifiableList(artists);
        ALBUMS = Collections.unmodifiableList(albums);
        TRACKS = Collections.unmodifiableList(tracks);
    }

    /** Ids of tracks liked on first launch. */
    public static final Set<String> INITIALLY_LIKED_IDS =
            Set.of("track-1", "track-2", "track-9", "track-14", "track-20", "track-27");

    /** Seed playlists in display order: playlist -> its track ids. */
    public static final Map<Playlist, List<String>> SEED_PLAYLISTS;

    static {
        Map<Playlist, List<String>> m = new LinkedHashMap<>();
        m.put(playlist("playlist-gym", "Gym motivation", "High energy", 11),
                List.of("track-1", "track-3", "track-4", "track-5", "track-9", "track-10", "track-12"));
        m.put(playlist("playlist-night", "Late night drive", "Synth and slow tempo", 22),
                List.of("track-17", "track-22", "track-23", "track-24", "track-25", "track-29"));
        m.put(playlist("playlist-fa", "فارسی", "Persian favorites", 33),
                List.of("track-34", "track-35", "track-36", "track-37"));
        SEED_PLAYLISTS = Collections.unmodifiableMap(m);
    }

    /** Track ids in the initial listening history, most recent first. */
    public static final List<String> SEED_HISTORY_IDS =
            List.of("track-2", "track-14", "track-1", "track-22", "track-9", "track-27", "track-35", "track-5");

    private static Playlist playlist(String id, String name, String description, int seed) {
        return new Playlist(id, name, 0, description, Collections.emptyList(), seed);
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
            "0|", "10000|شب‌های تهران بوی باران می‌دهد", "18000|کوچه‌ها آرام و چراغ‌ها بیدار",
            "26000|من هنوز اینجا منتظر صبحم", "34000|با دلی که هیچ‌وقت خسته نمی‌شود", "44000|"};

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

    /**
     * track-3 has plain (unsynced) lyrics, track-4 has none, Persian tracks (34-37) have synced Persian lyrics,
     * every other track has synced English lyrics.
     */
    public static Lyrics lyricsFor(String trackId) {
        switch (trackId) {
            case "track-4":
                return Lyrics.none(trackId);
            case "track-3":
                return new Lyrics(trackId, lines(ENGLISH_TIMES_AND_LINES, false, true), false, "Embedded");
            case "track-34": case "track-35": case "track-36": case "track-37":
                return new Lyrics(trackId, lines(PERSIAN_TIMES_AND_LINES, true, false), true, "LRC file");
            default:
                return new Lyrics(trackId, lines(ENGLISH_TIMES_AND_LINES, true, false), true, "LRCLIB");
        }
    }

    private SampleData() { }
}
