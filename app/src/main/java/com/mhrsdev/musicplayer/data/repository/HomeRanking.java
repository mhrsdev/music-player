package com.mhrsdev.musicplayer.data.repository;

import com.mhrsdev.musicplayer.data.db.query.AlbumQueries.AlbumStat;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries.TrackStat;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure Home-feed rules: what goes into each section and in which order. Empty sections are omitted. */
public final class HomeRanking {
    static final int HERO_COUNT = 8;
    static final int THROWBACK_COUNT = 10;
    static final int QUICK_PICK_COUNT = 8;
    /** An album counts as "not played lately" after this long. */
    static final long STALE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int LIKE_WEIGHT = 3;

    public static List<HomeSection> build(List<AlbumStat> albums, List<TrackStat> tracks, List<Playlist> playlists,
                                          long nowMs) {
        List<AlbumStat> hero = albums.stream()
                .sorted(Comparator.comparingLong(AlbumStat::newestAddedMs).reversed())
                .limit(HERO_COUNT).collect(Collectors.toList());
        Set<String> heroIds = new HashSet<>();
        for (AlbumStat a : hero) heroIds.add(a.album().id());

        // Throwbacks: not already in the hero row, unplayed or not played for a month; least played, then oldest first.
        List<AlbumStat> throwbacks = albums.stream()
                .filter(a -> !heroIds.contains(a.album().id()))
                .filter(a -> a.lastPlayedMs() == 0 || nowMs - a.lastPlayedMs() > STALE_MS)
                .sorted(Comparator.comparingInt(AlbumStat::plays).thenComparingLong(AlbumStat::newestAddedMs))
                .limit(THROWBACK_COUNT).collect(Collectors.toList());

        List<TrackStat> picks = tracks.stream()
                .filter(t -> score(t) > 0)
                .sorted(Comparator.comparingInt(HomeRanking::score).thenComparingLong(TrackStat::lastPlayedMs).reversed())
                .limit(QUICK_PICK_COUNT).collect(Collectors.toList());

        List<HomeSection> out = new ArrayList<>();
        out.add(new HomeSection("new-releases", "Recently added", "Fresh in your library",
                HomeSectionStyle.HERO_CAROUSEL, albumItems(hero)));
        out.add(new HomeSection("throwbacks", "Throwbacks", "Albums you haven't played in a while",
                HomeSectionStyle.CAROUSEL, albumItems(throwbacks)));
        out.add(new HomeSection("playlists", "Your playlists", null, HomeSectionStyle.CAROUSEL,
                playlists.stream().map(p -> HomeItem.of(p)).collect(Collectors.toList())));
        out.add(new HomeSection("quick-picks", "Quick picks", "Your most played and liked",
                HomeSectionStyle.LIST, picks.stream().map(t -> HomeItem.of(t.track())).collect(Collectors.toList())));
        out.removeIf(s -> s.items().isEmpty());
        return out;
    }

    /** Plays count one point each; a like is worth three plays. */
    static int score(TrackStat t) {
        return t.plays() + (t.track().liked() ? LIKE_WEIGHT : 0);
    }

    private static List<HomeItem> albumItems(List<AlbumStat> stats) {
        List<HomeItem> items = new ArrayList<>();
        for (AlbumStat s : stats) {
            Album album = s.album();
            items.add(HomeItem.of(album));
        }
        return items;
    }

    private HomeRanking() { }
}
