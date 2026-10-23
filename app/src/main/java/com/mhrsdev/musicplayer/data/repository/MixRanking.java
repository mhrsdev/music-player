package com.mhrsdev.musicplayer.data.repository;

import com.mhrsdev.musicplayer.data.db.query.PlayEventQueries.PlayEvent;
import com.mhrsdev.musicplayer.data.db.query.Seeds;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries.FeedTrack;
import com.mhrsdev.musicplayer.domain.model.HomeMix;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure "Made for you" rules, built only from the local library and play history (no network, no tags beyond MediaStore).
 * Deterministic for a given seed: tests pass a fixed seed, production passes the day number so mixes stay stable all day.
 */
class MixRanking {
    /** A mix needs at least this many tracks, otherwise it is skipped (small libraries just get fewer mixes). */
    static final int MIX_MIN = 8;
    static final int MIX_MAX = 30;
    static final int DAILY_COUNT = 3;
    /** Time-of-day mixes need this many distinct tracks played in the window. */
    static final int TIME_MIX_MIN = 5;
    private static final Set<String> UPBEAT = Set.of("rock", "metal", "dance", "edm", "electronic", "electro", "house",
            "techno", "trance", "hip hop", "hip-hop", "hiphop", "rap", "punk", "drum", "dubstep", "workout", "disco");

    static int weight(FeedTrack t) {
        return 1 + t.plays() * 2 + (t.track().liked() ? 3 : 0);
    }

    /** Clusters by genre (when tagged), else decade, else artist; the heaviest clusters become Daily mix 1..3. */
    static List<HomeMix> daily(List<FeedTrack> tracks, long seed) {
        Map<String, List<FeedTrack>> clusters = new LinkedHashMap<>();
        for (FeedTrack t : tracks) clusters.computeIfAbsent(primaryKey(t), k -> new ArrayList<>()).add(t);
        List<List<FeedTrack>> eligible = new ArrayList<>();
        for (List<FeedTrack> c : clusters.values()) if (c.size() >= MIX_MIN) eligible.add(c);
        if (eligible.size() < DAILY_COUNT) { // one big genre: split it by decade as well
            Map<String, List<FeedTrack>> second = new LinkedHashMap<>();
            for (FeedTrack t : tracks) {
                if (t.track().year() > 0) second.computeIfAbsent("d" + decade(t.track().year()), k -> new ArrayList<>()).add(t);
            }
            for (List<FeedTrack> c : second.values()) {
                if (c.size() >= MIX_MIN && c.size() < tracks.size() && eligible.size() < DAILY_COUNT) eligible.add(c);
            }
        }
        eligible.sort(Comparator.comparingLong((List<FeedTrack> c) -> {
            long w = 0;
            for (FeedTrack t : c) w += weight(t);
            return w;
        }).reversed());
        List<HomeMix> out = new ArrayList<>();
        for (int i = 0; i < eligible.size() && out.size() < DAILY_COUNT; i++) {
            List<Track> picked = pick(eligible.get(i), seed + i);
            if (picked.size() >= MIX_MIN) out.add(mix("daily-" + (out.size() + 1), HomeMix.Kind.DAILY,
                    String.valueOf(out.size() + 1), picked));
        }
        return out;
    }

    /** Tracks played between {@code fromHour} (inclusive) and {@code toHour} (exclusive, wraps past midnight). */
    static HomeMix timeOfDay(String id, HomeMix.Kind kind, List<PlayEvent> events, Map<String, FeedTrack> byId,
                             ZoneId zone, int fromHour, int toHour, long seed) {
        Map<String, Integer> counts = new HashMap<>();
        for (PlayEvent e : events) {
            int h = Instant.ofEpochMilli(e.playedAtMs()).atZone(zone).getHour();
            boolean in = fromHour <= toHour ? h >= fromHour && h < toHour : h >= fromHour || h < toHour;
            if (in && byId.containsKey(e.trackId())) counts.merge(e.trackId(), 1, Integer::sum);
        }
        if (counts.size() < TIME_MIX_MIN) return null;
        List<FeedTrack> played = new ArrayList<>();
        for (String id2 : counts.keySet()) played.add(byId.get(id2));
        played.sort(Comparator.comparingInt((FeedTrack t) -> counts.get(t.track().id())).reversed()
                .thenComparing(t -> t.track().id()));
        return mix(id, kind, "", pick(played, seed));
    }

    static HomeMix upbeat(List<FeedTrack> tracks, long seed) {
        List<FeedTrack> hits = new ArrayList<>();
        for (FeedTrack t : tracks) if (t.genre() != null && isUpbeat(t.genre())) hits.add(t);
        if (hits.size() < MIX_MIN) return null;
        return mix("upbeat", HomeMix.Kind.UPBEAT, "", pick(hits, seed));
    }

    static boolean isUpbeat(String genre) {
        String g = genre.toLowerCase(Locale.ROOT);
        for (String k : UPBEAT) if (g.contains(k)) return true;
        return false;
    }

    /** Decade / genre / folder groups (whole group, library order), each with at least {@code min} tracks. */
    static List<HomeMix> groups(List<FeedTrack> tracks, HomeMix.Kind kind, int min) {
        Map<String, List<Track>> groups = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (FeedTrack t : tracks) {
            String key;
            String label;
            switch (kind) {
                case DECADE -> {
                    if (t.track().year() <= 0) continue;
                    key = String.valueOf(decade(t.track().year()));
                    label = key;
                }
                case GENRE -> {
                    if (t.genre() == null) continue;
                    key = t.genre().toLowerCase(Locale.ROOT);
                    label = t.genre();
                }
                default -> {
                    String folder = parent(t.path());
                    if (folder == null) continue;
                    key = folder;
                    label = folder.substring(folder.lastIndexOf('/') + 1);
                }
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t.track());
            labels.putIfAbsent(key, label);
        }
        List<HomeMix> out = new ArrayList<>();
        for (Map.Entry<String, List<Track>> g : groups.entrySet()) {
            if (g.getValue().size() < min) continue;
            List<Track> sorted = new ArrayList<>(g.getValue());
            sorted.sort(Comparator.comparing(Track::albumTitle).thenComparingInt(Track::trackNumber)
                    .thenComparing(Track::title));
            String prefix = kind == HomeMix.Kind.DECADE ? "decade-" : kind == HomeMix.Kind.GENRE ? "genre-" : "folder-";
            out.add(mix(prefix + g.getKey(), kind, labels.get(g.getKey()), sorted));
        }
        if (kind == HomeMix.Kind.DECADE) {
            out.sort(Comparator.comparing(HomeMix::arg).reversed());
        } else {
            out.sort(Comparator.comparingInt((HomeMix m) -> m.tracks().size()).reversed().thenComparing(HomeMix::arg));
        }
        return out;
    }

    /**
     * Ranks by weight plus a seeded jitter (so a mix is not the same top-N every day), then spreads artists so the same
     * artist rarely plays twice in a row. At most {@link #MIX_MAX}.
     */
    static List<Track> pick(List<FeedTrack> pool, long seed) {
        List<FeedTrack> ranked = new ArrayList<>(pool);
        Map<String, Double> score = new HashMap<>();
        for (FeedTrack t : ranked) score.put(t.track().id(), weight(t) + 4 * jitter(seed, t.track().id()));
        ranked.sort(Comparator.comparingDouble((FeedTrack t) -> score.get(t.track().id())).reversed()
                .thenComparing(t -> t.track().id()));
        List<FeedTrack> top = new ArrayList<>(ranked.subList(0, Math.min(MIX_MAX, ranked.size())));
        List<Track> out = new ArrayList<>();
        String lastArtist = null;
        while (!top.isEmpty()) {
            int idx = 0;
            for (int i = 0; i < top.size(); i++) {
                if (!top.get(i).track().artistId().equals(lastArtist)) { idx = i; break; }
            }
            FeedTrack next = top.remove(idx);
            out.add(next.track());
            lastArtist = next.track().artistId();
        }
        return out;
    }

    /** Deterministic value in [0, 1) from seed + key (SplitMix64 finalizer). */
    static double jitter(long seed, String key) {
        long z = seed * 0x9E3779B97F4A7C15L + key.hashCode();
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    static int decade(int year) {
        return year / 10 * 10;
    }

    static String parent(String path) {
        if (path == null) return null;
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : null;
    }

    private static String primaryKey(FeedTrack t) {
        if (t.genre() != null) return "g" + t.genre().toLowerCase(Locale.ROOT);
        if (t.track().year() > 0) return "d" + decade(t.track().year());
        return "a" + t.track().artistId();
    }

    private static HomeMix mix(String id, HomeMix.Kind kind, String arg, List<Track> tracks) {
        Map<String, Integer> artists = new LinkedHashMap<>();
        for (Track t : tracks) artists.merge(t.artistName(), 1, Integer::sum);
        List<String> names = new ArrayList<>(artists.keySet());
        names.sort(Comparator.comparingInt((String n) -> artists.get(n)).reversed());
        String art = null;
        for (Track t : tracks) {
            if (t.artworkUri() != null) { art = t.artworkUri(); break; }
        }
        return new HomeMix(id, kind, arg, tracks, names.subList(0, Math.min(3, names.size())), art, Seeds.of(id));
    }

    private MixRanking() { }
}
