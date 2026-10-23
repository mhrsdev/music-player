package com.mhrsdev.musicplayer.data.repository;

import com.mhrsdev.musicplayer.data.db.query.AlbumQueries.AlbumStat;
import com.mhrsdev.musicplayer.data.db.query.PlayEventQueries.PlayEvent;
import com.mhrsdev.musicplayer.data.db.query.Seeds;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries.FeedTrack;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries.TrackStat;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeMix;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.HomeStats;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Home feed v2: every section a local music app can derive from the user's own library + play history. Pure (no
 * Android, no clock, no I/O), so tests pass fixed seeds and a fake "now". Sections carry ids + parameters, never display
 * text (the UI localizes by id). Empty sections are dropped, so a 10-song library gets a short feed and a 5000-song one a
 * long one. {@link HomeRanking} still supplies the hero / throwbacks / playlists / quick-picks rules.
 *
 * <p>Order: a fixed head (what you were doing), then a "discovery" middle whose order rotates once per day
 * ({@code daySeed}), then a fixed tail (browse chips, stats). Random album picks use {@code discoverySeed}, which changes on
 * every launch and pull-to-refresh.
 */
public final class HomeFeedBuilder {
    static final long DAY_MS = 24L * 60 * 60 * 1000;
    static final long FORGOTTEN_MS = 60 * DAY_MS;
    static final int ROW_MAX = 50;
    static final int MIN_ROW = 3;
    static final int RANDOM_ALBUMS = 20;

    /** Everything the feed is built from. {@code events} newest first. */
    public record Input(List<FeedTrack> tracks, List<PlayEvent> events, List<Playlist> playlists, long nowMs,
                        ZoneId zone, long daySeed, long discoverySeed) { }

    public static List<HomeSection> build(Input in) {
        List<FeedTrack> tracks = in.tracks();
        if (tracks.isEmpty()) return List.of();
        Map<String, FeedTrack> byId = new HashMap<>();
        for (FeedTrack t : tracks) byId.put(t.track().id(), t);
        List<AlbumStat> albums = albumStats(tracks);
        Map<String, Album> albumById = new HashMap<>();
        for (AlbumStat a : albums) albumById.put(a.album().id(), a.album());
        List<TrackStat> stats = new ArrayList<>();
        for (FeedTrack t : tracks) {
            if (t.plays() > 0 || t.track().liked()) stats.add(new TrackStat(t.track(), t.plays(), t.lastPlayedMs()));
        }
        Map<String, HomeSection> base = new HashMap<>();
        for (HomeSection s : HomeRanking.build(albums, stats, in.playlists(), in.nowMs())) base.put(s.id(), s);

        List<FeedTrack> recent = new ArrayList<>();
        for (FeedTrack t : tracks) if (t.lastPlayedMs() > 0) recent.add(t);
        recent.sort(Comparator.comparingLong(FeedTrack::lastPlayedMs).reversed());

        List<HomeSection> head = new ArrayList<>();
        add(head, base.get("new-releases"));
        if (!recent.isEmpty()) {
            head.add(section("continue", HomeSectionStyle.CONTINUE, List.of(HomeItem.of(recent.get(0).track())), null));
        }
        head.add(section("jump-back-in", HomeSectionStyle.CAROUSEL, jumpBackIn(recent, albumById), null));
        head.add(section("mixes", HomeSectionStyle.CAROUSEL, mixes(in, byId), null));
        add(head, base.get("quick-picks"));
        add(head, base.get("playlists"));

        List<List<HomeSection>> middle = new ArrayList<>();
        middle.add(List.of(section("favorites", HomeSectionStyle.CAROUSEL, favorites(tracks), null)));
        middle.add(List.of(section("top-month", HomeSectionStyle.LIST, topSince(in, byId, monthStart(in)), null)));
        middle.add(List.of(section("artists", HomeSectionStyle.AVATARS, artists(tracks), null)));
        middle.add(List.of(section("forgotten", HomeSectionStyle.CAROUSEL, forgotten(tracks, in.nowMs()), null)));
        middle.add(List.of(onThisDay(in, byId)));
        middle.add(List.of(section("unfinished", HomeSectionStyle.CAROUSEL, unfinished(tracks, albumById), null)));
        if (base.containsKey("throwbacks")) middle.add(List.of(base.get("throwbacks")));
        List<HomeItem> randomAlbums = randomAlbums(albums, in.discoverySeed());
        if (randomAlbums.size() >= MIN_ROW) {
            middle.add(List.of(section("discover-actions", HomeSectionStyle.ACTIONS, List.of(), null),
                    section("random-albums", HomeSectionStyle.CAROUSEL, randomAlbums, null)));
        }
        middle.add(List.of(section("top-all-time", HomeSectionStyle.LIST, topAllTime(tracks), null)));
        middle.add(List.of(chips("decades", MixRanking.groups(tracks, HomeMix.Kind.DECADE, MIN_ROW))));
        middle.add(List.of(chips("genres", MixRanking.groups(tracks, HomeMix.Kind.GENRE, MIN_ROW))));
        Collections.shuffle(middle, new Random(in.daySeed()));

        List<HomeSection> out = new ArrayList<>(head);
        for (List<HomeSection> block : middle) out.addAll(block);
        out.add(chips("folders", MixRanking.groups(tracks, HomeMix.Kind.FOLDER, 1)));
        out.add(section("stats", HomeSectionStyle.STATS, List.of(), null, stats(tracks, in, byId)));
        out.removeIf(s -> !s.hasContent() || (s.style() != HomeSectionStyle.CONTINUE
                && s.style() != HomeSectionStyle.HERO_CAROUSEL && s.style() != HomeSectionStyle.ACTIONS
                && s.style() != HomeSectionStyle.STATS && s.items().size() < minFor(s)));
        return out;
    }

    private static int minFor(HomeSection s) {
        return switch (s.id()) {
            case "mixes", "playlists", "quick-picks", "throwbacks" -> 1;
            case "decades", "genres", "folders", "jump-back-in" -> 2;
            default -> MIN_ROW;
        };
    }

    // ---- sections ----

    static List<HomeItem> jumpBackIn(List<FeedTrack> recent, Map<String, Album> albums) {
        Set<String> seen = new HashSet<>();
        List<HomeItem> out = new ArrayList<>();
        for (FeedTrack t : recent) {
            if (out.size() == 12) break;
            if (seen.add(t.track().albumId())) out.add(HomeItem.of(albums.get(t.track().albumId())));
        }
        return out;
    }

    static List<HomeItem> mixes(Input in, Map<String, FeedTrack> byId) {
        List<HomeMix> mixes = new ArrayList<>(MixRanking.daily(in.tracks(), in.daySeed()));
        HomeMix late = MixRanking.timeOfDay("late-night", HomeMix.Kind.LATE_NIGHT, in.events(), byId, in.zone(), 22, 4,
                in.daySeed());
        HomeMix morning = MixRanking.timeOfDay("morning", HomeMix.Kind.MORNING, in.events(), byId, in.zone(), 5, 11,
                in.daySeed());
        HomeMix upbeat = MixRanking.upbeat(in.tracks(), in.daySeed());
        for (HomeMix m : new HomeMix[] {late, morning, upbeat}) if (m != null) mixes.add(m);
        return map(mixes, HomeItem::of);
    }

    static List<HomeItem> favorites(List<FeedTrack> tracks) {
        List<FeedTrack> liked = new ArrayList<>();
        for (FeedTrack t : tracks) if (t.track().liked()) liked.add(t);
        liked.sort(Comparator.comparingInt(FeedTrack::plays).reversed().thenComparing(t -> t.track().title()));
        return trackItems(liked);
    }

    static List<HomeItem> topSince(Input in, Map<String, FeedTrack> byId, long fromMs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlayEvent e : in.events()) {
            if (e.playedAtMs() >= fromMs && byId.containsKey(e.trackId())) counts.merge(e.trackId(), 1, Integer::sum);
        }
        List<FeedTrack> top = new ArrayList<>();
        for (String id : counts.keySet()) top.add(byId.get(id));
        top.sort(Comparator.comparingInt((FeedTrack t) -> counts.get(t.track().id())).reversed()
                .thenComparing(t -> t.track().id()));
        return trackItems(top);
    }

    static List<HomeItem> topAllTime(List<FeedTrack> tracks) {
        List<FeedTrack> top = new ArrayList<>();
        for (FeedTrack t : tracks) if (t.plays() >= 2) top.add(t);
        top.sort(Comparator.comparingInt(FeedTrack::plays).reversed().thenComparing(t -> t.track().id()));
        return trackItems(top);
    }

    /** Liked or played 3+ times, but not heard for 60 days (liked and never played counts too). */
    static List<HomeItem> forgotten(List<FeedTrack> tracks, long nowMs) {
        List<FeedTrack> out = new ArrayList<>();
        for (FeedTrack t : tracks) {
            boolean loved = t.track().liked() || t.plays() >= 3;
            boolean stale = t.lastPlayedMs() == 0 ? t.track().liked() : nowMs - t.lastPlayedMs() > FORGOTTEN_MS;
            if (loved && stale) out.add(t);
        }
        out.sort(Comparator.comparingInt(MixRanking::weight).reversed().thenComparingLong(FeedTrack::lastPlayedMs));
        return trackItems(out);
    }

    /** What you played a year ago, give or take three days; {@code arg} = that year. */
    static HomeSection onThisDay(Input in, Map<String, FeedTrack> byId) {
        long center = in.nowMs() - 365 * DAY_MS;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlayEvent e : in.events()) {
            if (Math.abs(e.playedAtMs() - center) <= 3 * DAY_MS && byId.containsKey(e.trackId())) {
                counts.merge(e.trackId(), 1, Integer::sum);
            }
        }
        List<FeedTrack> played = new ArrayList<>();
        for (String id : counts.keySet()) played.add(byId.get(id));
        played.sort(Comparator.comparingInt((FeedTrack t) -> counts.get(t.track().id())).reversed()
                .thenComparing(t -> t.track().id()));
        String year = String.valueOf(Instant.ofEpochMilli(center).atZone(in.zone()).getYear());
        return section("on-this-day", HomeSectionStyle.CAROUSEL, trackItems(played), year);
    }

    /** Artists ranked by plays + likes (library size when nothing is played yet). */
    static List<HomeItem> artists(List<FeedTrack> tracks) {
        Map<String, int[]> score = new LinkedHashMap<>(); // [weight, tracks]
        Map<String, Track> sample = new HashMap<>();
        Map<String, Set<String>> albums = new HashMap<>();
        for (FeedTrack t : tracks) {
            String id = t.track().artistId();
            int[] s = score.computeIfAbsent(id, k -> new int[2]);
            s[0] += MixRanking.weight(t);
            s[1]++;
            Track prev = sample.get(id);
            if (prev == null || (prev.artworkUri() == null && t.track().artworkUri() != null)) sample.put(id, t.track());
            albums.computeIfAbsent(id, k -> new HashSet<>()).add(t.track().albumId());
        }
        List<String> ids = new ArrayList<>(score.keySet());
        ids.sort(Comparator.comparingInt((String id) -> score.get(id)[0]).reversed()
                .thenComparing(id -> sample.get(id).artistName()));
        List<HomeItem> out = new ArrayList<>();
        for (String id : ids) {
            if (out.size() == 30) break;
            Track s = sample.get(id);
            out.add(HomeItem.of(new Artist(id, s.artistName(), albums.get(id).size(), score.get(id)[1], s.artworkUri(),
                    Seeds.of("artist:" + id))));
        }
        return out;
    }

    /** Albums (3+ tracks) you started but did not finish, most recently touched first. */
    static List<HomeItem> unfinished(List<FeedTrack> tracks, Map<String, Album> albums) {
        Map<String, int[]> counts = new HashMap<>(); // [total, played]
        Map<String, Long> last = new HashMap<>();
        for (FeedTrack t : tracks) {
            int[] c = counts.computeIfAbsent(t.track().albumId(), k -> new int[2]);
            c[0]++;
            if (t.plays() > 0) c[1]++;
            last.merge(t.track().albumId(), t.lastPlayedMs(), Math::max);
        }
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            int[] c = e.getValue();
            if (c[0] >= 3 && c[1] > 0 && c[1] < c[0]) ids.add(e.getKey());
        }
        ids.sort(Comparator.comparingLong((String id) -> last.get(id)).reversed().thenComparing(id -> id));
        List<HomeItem> out = new ArrayList<>();
        for (String id : ids) {
            if (out.size() == ROW_MAX) break;
            out.add(HomeItem.of(albums.get(id)));
        }
        return out;
    }

    static List<HomeItem> randomAlbums(List<AlbumStat> albums, long seed) {
        List<Album> all = new ArrayList<>();
        for (AlbumStat a : albums) all.add(a.album());
        all.sort(Comparator.comparing(Album::id)); // input order must not leak into the shuffle
        Collections.shuffle(all, new Random(seed));
        return map(all.subList(0, Math.min(RANDOM_ALBUMS, all.size())), HomeItem::of);
    }

    static HomeStats stats(List<FeedTrack> tracks, Input in, Map<String, FeedTrack> byId) {
        Set<String> artists = new HashSet<>();
        Set<String> albums = new HashSet<>();
        long libraryMs = 0;
        for (FeedTrack t : tracks) {
            artists.add(t.track().artistId());
            albums.add(t.track().albumId());
            libraryMs += t.track().durationMs();
        }
        long from = monthStart(in);
        int plays = 0;
        long listened = 0;
        Map<String, Integer> byArtist = new HashMap<>();
        for (PlayEvent e : in.events()) {
            FeedTrack t = byId.get(e.trackId());
            if (e.playedAtMs() < from || t == null) continue;
            plays++;
            listened += t.track().durationMs();
            byArtist.merge(t.track().artistId(), 1, Integer::sum);
        }
        String topId = null;
        for (Map.Entry<String, Integer> e : byArtist.entrySet()) {
            if (topId == null || e.getValue() > byArtist.get(topId)
                    || (e.getValue().equals(byArtist.get(topId)) && e.getKey().compareTo(topId) < 0)) topId = e.getKey();
        }
        String topName = null;
        if (topId != null) {
            for (FeedTrack t : tracks) {
                if (t.track().artistId().equals(topId)) { topName = t.track().artistName(); break; }
            }
        }
        return new HomeStats(tracks.size(), artists.size(), albums.size(), libraryMs, plays, listened, topName, topId);
    }

    // ---- helpers ----

    static long monthStart(Input in) {
        LocalDate today = Instant.ofEpochMilli(in.nowMs()).atZone(in.zone()).toLocalDate();
        return today.withDayOfMonth(1).atStartOfDay(in.zone()).toInstant().toEpochMilli();
    }

    /** Same shape as {@code AlbumQueries.stats()}, derived from the feed tracks (saves a second full scan). */
    static List<AlbumStat> albumStats(List<FeedTrack> tracks) {
        Map<String, List<FeedTrack>> byAlbum = new LinkedHashMap<>();
        for (FeedTrack t : tracks) byAlbum.computeIfAbsent(t.track().albumId(), k -> new ArrayList<>()).add(t);
        List<AlbumStat> out = new ArrayList<>();
        for (Map.Entry<String, List<FeedTrack>> e : byAlbum.entrySet()) {
            Track first = e.getValue().get(0).track();
            int year = 0;
            int plays = 0;
            long newest = 0;
            long lastPlayed = 0;
            String art = null;
            for (FeedTrack t : e.getValue()) {
                year = Math.max(year, t.track().year());
                plays += t.plays();
                newest = Math.max(newest, t.track().addedAtMs());
                lastPlayed = Math.max(lastPlayed, t.lastPlayedMs());
                if (art == null) art = t.track().artworkUri();
            }
            Album album = new Album(e.getKey(), first.albumTitle(), first.artistId(), first.artistName(), year,
                    e.getValue().size(), art, Seeds.of(e.getKey()));
            out.add(new AlbumStat(album, newest, plays, lastPlayed));
        }
        out.sort(Comparator.comparing(a -> a.album().title()));
        return out;
    }

    private static HomeSection chips(String id, List<HomeMix> groups) {
        return section(id, HomeSectionStyle.CHIPS, map(groups, HomeItem::of), null);
    }

    private static List<HomeItem> trackItems(List<FeedTrack> tracks) {
        List<HomeItem> out = new ArrayList<>();
        for (FeedTrack t : tracks) {
            if (out.size() == ROW_MAX) break;
            out.add(HomeItem.of(t.track()));
        }
        return out;
    }

    private static <T> List<HomeItem> map(List<T> list, Function<T, HomeItem> f) {
        List<HomeItem> out = new ArrayList<>(list.size());
        for (T t : list) out.add(f.apply(t));
        return out;
    }

    private static void add(List<HomeSection> out, HomeSection s) {
        if (s != null) out.add(s);
    }

    private static HomeSection section(String id, HomeSectionStyle style, List<HomeItem> items, String arg) {
        return section(id, style, items, arg, null);
    }

    private static HomeSection section(String id, HomeSectionStyle style, List<HomeItem> items, String arg,
                                       HomeStats stats) {
        return new HomeSection(id, id, null, style, items, arg, stats);
    }

    private HomeFeedBuilder() { }
}
