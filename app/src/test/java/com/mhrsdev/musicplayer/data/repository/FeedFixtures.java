package com.mhrsdev.musicplayer.data.repository;

import com.mhrsdev.musicplayer.data.db.query.PlayEventQueries.PlayEvent;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries.FeedTrack;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Synthetic libraries + listening history of any size, deterministic per seed (JVM tests and the Home renders). */
public final class FeedFixtures {
    public static final ZoneId ZONE = ZoneId.of("UTC");
    /** 2026-10-23 12:00 UTC. */
    public static final long NOW = 1_792_756_800_000L;
    private static final String[] GENRES = {"Pop", "Rock", "Electronic", "Jazz", "Hip-Hop", "Classical", "Folk", null};
    private static final String[] ARTISTS = {"NEFFEX", "سهراب نوری", "Aurora Vale", "The Night Owls", "Kian Mehr",
            "Lumen", "Static Bloom", "Darya", "Velvet Coast", "Orbital Kids", "Sara Ahmadi", "Echo Park"};

    public record Library(List<FeedTrack> tracks, List<PlayEvent> events, List<Playlist> playlists) {
        public HomeFeedBuilder.Input input(long daySeed, long discoverySeed) {
            return new HomeFeedBuilder.Input(tracks, events, playlists, NOW, ZONE, daySeed, discoverySeed);
        }

        public List<HomeSection> build() {
            return HomeFeedBuilder.build(input(7, 11));
        }
    }

    /** {@code n} tracks in albums of ~10, a third played, some liked, a history spread over 13 months. */
    public static Library library(int n, long seed) {
        Random r = new Random(seed);
        int artists = Math.max(2, Math.min(ARTISTS.length * 40, n / 25 + 2));
        List<FeedTrack> tracks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int album = i / 10;
            int artist = album % artists;
            String artistName = ARTISTS[artist % ARTISTS.length] + (artist >= ARTISTS.length ? " " + (artist / ARTISTS.length) : "");
            int year = 1965 + (album * 7) % 60;
            boolean liked = r.nextInt(6) == 0;
            int plays = r.nextInt(3) == 0 ? 1 + r.nextInt(25) : 0;
            long last = plays > 0 ? NOW - (long) (r.nextDouble() * 200 * HomeFeedBuilder.DAY_MS) : 0;
            Track t = new Track("t" + i, "Song " + i, "ar" + artist, artistName, "al" + album, "Album " + album,
                    150_000 + r.nextInt(150_000), null, ("al" + album).hashCode(), null, false, liked, year, i % 10 + 1,
                    NOW - (long) i * 3_600_000L);
            String genre = GENRES[(album + artist) % GENRES.length];
            tracks.add(new FeedTrack(t, plays, last, genre, "/storage/emulated/0/Music/" + artistName + "/Album " + album + "/" + i + ".mp3"));
        }
        List<PlayEvent> events = new ArrayList<>();
        for (FeedTrack t : tracks) {
            for (int p = 0; p < t.plays(); p++) {
                long at = p == 0 ? t.lastPlayedMs() : NOW - (long) (r.nextDouble() * 395 * HomeFeedBuilder.DAY_MS);
                events.add(new PlayEvent(t.track().id(), at));
            }
        }
        events.sort((a, b) -> Long.compare(b.playedAtMs(), a.playedAtMs()));
        List<Playlist> playlists = new ArrayList<>();
        for (int i = 0; i < Math.min(6, n / 30); i++) {
            playlists.add(new Playlist("p" + i, "Playlist " + (i + 1), 12, null, List.of(), i * 31));
        }
        return new Library(tracks, events, playlists);
    }

    private FeedFixtures() { }
}
