package com.mhrsdev.musicplayer.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.data.db.query.AlbumQueries.AlbumStat;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries.TrackStat;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class HomeRankingTest {
    private static final long NOW = 1_000L * HomeRanking.STALE_MS;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static AlbumStat album(String id, long added, int plays, long lastPlayed) {
        return new AlbumStat(new Album(id, id, "ar", "Artist", 0, 1, null, 0), added, plays, lastPlayed);
    }

    private static TrackStat track(String id, int plays, boolean liked, long lastPlayed) {
        return new TrackStat(new Track(id, id, "ar", "Artist", "al", "Album", 1, null, 0, null, false, liked, 0, 0, 0),
                plays, lastPlayed);
    }

    private static List<String> ids(HomeSection section) {
        List<String> out = new ArrayList<>();
        section.items().forEach(i -> out.add(i.album() != null ? i.album().id() : i.track().id()));
        return out;
    }

    private static HomeSection section(List<HomeSection> sections, String id) {
        return sections.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    @Test public void emptyLibraryHasNoSections() {
        assertTrue(HomeRanking.build(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), NOW).isEmpty());
    }

    @Test public void heroIsNewestFirstAndCapped() {
        List<AlbumStat> albums = new ArrayList<>();
        for (int i = 0; i < 12; i++) albums.add(album("a" + i, i, 0, 0));
        List<String> hero = ids(section(HomeRanking.build(albums, List.of(), List.of(), NOW), "new-releases"));
        assertEquals(HomeRanking.HERO_COUNT, hero.size());
        assertEquals("a11", hero.get(0));
        assertEquals("a4", hero.get(7));
    }

    @Test public void throwbacksSkipHeroRecentlyPlayedAndOrderByLeastPlayedThenOldest() {
        List<AlbumStat> albums = new ArrayList<>();
        for (int i = 0; i < 8; i++) albums.add(album("new" + i, 1000 + i, 0, 0)); // all in the hero row
        albums.add(album("old-played", 1, 5, NOW - 100 * DAY));
        albums.add(album("old-never", 2, 0, 0));
        albums.add(album("older-never", 1, 0, 0));
        albums.add(album("old-recent", 3, 9, NOW - DAY)); // played yesterday: not a throwback
        List<String> throwbacks = ids(section(HomeRanking.build(albums, List.of(), List.of(), NOW), "throwbacks"));
        assertEquals(List.of("older-never", "old-never", "old-played"), throwbacks);
    }

    @Test public void quickPicksScorePlaysAndLikesAndDropZeroScores() {
        List<TrackStat> tracks = List.of(
                track("played-twice", 2, false, 10),
                track("liked", 0, true, 5),            // 3
                track("liked-and-played", 1, true, 1), // 4
                track("tie-newer", 3, false, 99),      // 3, played more recently than "liked"
                track("never", 0, false, 0));
        List<String> picks = ids(section(HomeRanking.build(List.of(), tracks, List.of(), NOW), "quick-picks"));
        assertEquals(List.of("liked-and-played", "tie-newer", "liked", "played-twice"), picks);
    }
}
