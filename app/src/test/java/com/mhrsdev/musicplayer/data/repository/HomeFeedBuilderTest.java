package com.mhrsdev.musicplayer.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.data.db.query.PlayEventQueries.PlayEvent;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries.FeedTrack;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeMix;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class HomeFeedBuilderTest {

    private static List<String> ids(List<HomeSection> sections) {
        List<String> out = new ArrayList<>();
        for (HomeSection s : sections) out.add(s.id());
        return out;
    }

    private static HomeSection find(List<HomeSection> sections, String id) {
        for (HomeSection s : sections) if (s.id().equals(id)) return s;
        return null;
    }

    @Test public void emptyLibraryHasNoSections() {
        assertTrue(FeedFixtures.library(0, 1).build().isEmpty());
    }

    @Test public void smallLibraryGetsAShortFeedWithoutEmptySections() {
        List<HomeSection> s = FeedFixtures.library(10, 1).build();
        for (HomeSection sec : s) assertTrue(sec.id(), sec.hasContent());
        assertTrue(ids(s).toString(), s.size() <= 9);
        assertEquals("new-releases", s.get(0).id());
        assertEquals("stats", s.get(s.size() - 1).id());
    }

    @Test public void bigLibraryGetsEverySection() {
        List<String> ids = ids(FeedFixtures.library(5000, 1).build());
        for (String want : List.of("new-releases", "continue", "jump-back-in", "mixes", "quick-picks", "playlists",
                "favorites", "top-month", "artists", "forgotten", "on-this-day", "unfinished", "throwbacks",
                "discover-actions", "random-albums", "top-all-time", "decades", "genres", "folders", "stats")) {
            assertTrue(want + " missing from " + ids, ids.contains(want));
        }
        assertEquals("random-albums follows its action row", ids.indexOf("discover-actions") + 1, ids.indexOf("random-albums"));
    }

    @Test public void orderIsStablePerDayAndChangesAcrossDays() {
        FeedFixtures.Library lib = FeedFixtures.library(500, 3);
        List<String> a = ids(HomeFeedBuilder.build(lib.input(100, 1)));
        List<String> b = ids(HomeFeedBuilder.build(lib.input(100, 999))); // refresh: same day, new discovery seed
        List<String> other = ids(HomeFeedBuilder.build(lib.input(101, 1)));
        assertEquals(a, b);
        assertNotEquals(a, other);
        assertEquals(a.subList(0, 6), other.subList(0, 6)); // the head never moves
    }

    @Test public void refreshChangesRandomPicksOnly() {
        FeedFixtures.Library lib = FeedFixtures.library(500, 3);
        HomeSection r1 = find(HomeFeedBuilder.build(lib.input(100, 1)), "random-albums");
        HomeSection r2 = find(HomeFeedBuilder.build(lib.input(100, 2)), "random-albums");
        HomeSection again = find(HomeFeedBuilder.build(lib.input(100, 1)), "random-albums");
        assertNotEquals(r1.items(), r2.items());
        assertEquals(r1.items(), again.items());
        assertEquals(find(HomeFeedBuilder.build(lib.input(100, 1)), "mixes"),
                find(HomeFeedBuilder.build(lib.input(100, 2)), "mixes"));
    }

    @Test public void dailyMixesAreDistinctBoundedAndDeterministic() {
        FeedFixtures.Library lib = FeedFixtures.library(1000, 5);
        List<HomeMix> mixes = MixRanking.daily(lib.tracks(), 42);
        assertEquals(3, mixes.size());
        Set<String> firstTracks = new HashSet<>();
        for (HomeMix m : mixes) {
            assertTrue(m.tracks().size() >= MixRanking.MIX_MIN && m.tracks().size() <= MixRanking.MIX_MAX);
            firstTracks.add(m.tracks().get(0).id());
            assertFalse(m.artistNames().isEmpty());
        }
        assertEquals(3, firstTracks.size());
        assertEquals(mixes, MixRanking.daily(lib.tracks(), 42));
        assertEquals(List.of("1", "2", "3"), List.of(mixes.get(0).arg(), mixes.get(1).arg(), mixes.get(2).arg()));
    }

    @Test public void mixSpreadsArtists() {
        FeedFixtures.Library lib = FeedFixtures.library(400, 9);
        for (HomeMix m : MixRanking.daily(lib.tracks(), 1)) {
            int repeats = 0;
            for (int i = 1; i < m.tracks().size(); i++) {
                if (m.tracks().get(i).artistId().equals(m.tracks().get(i - 1).artistId())) repeats++;
            }
            Set<String> artists = new HashSet<>();
            for (Track t : m.tracks()) artists.add(t.artistId());
            if (artists.size() > 1) assertTrue("back-to-back repeats " + repeats, repeats <= m.tracks().size() / 3);
        }
    }

    @Test public void lateNightUsesLocalHoursAndNeedsEnoughTracks() {
        List<FeedTrack> tracks = FeedFixtures.library(20, 1).tracks();
        Map<String, FeedTrack> byId = new HashMap<>();
        for (FeedTrack t : tracks) byId.put(t.track().id(), t);
        long midnight = FeedFixtures.NOW - 12 * 3_600_000L; // 2026-10-23 00:00 UTC
        List<PlayEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) events.add(new PlayEvent("t" + i, midnight + 3_600_000L)); // 01:00
        events.add(new PlayEvent("t9", midnight + 12 * 3_600_000L)); // noon: not late night
        assertNull(MixRanking.timeOfDay("late-night", HomeMix.Kind.LATE_NIGHT, events, byId, FeedFixtures.ZONE, 22, 4, 1));
        events.add(new PlayEvent("t5", midnight - 3_600_000L)); // 23:00 the day before
        HomeMix m = MixRanking.timeOfDay("late-night", HomeMix.Kind.LATE_NIGHT, events, byId, FeedFixtures.ZONE, 22, 4, 1);
        assertNotNull(m);
        assertEquals(5, m.tracks().size());
        for (Track t : m.tracks()) assertNotEquals("t9", t.id());
    }

    @Test public void forgottenNeedsSixtyDaysOfSilence() {
        long now = FeedFixtures.NOW;
        FeedTrack fresh = feed("fresh", true, 5, now - 10 * HomeFeedBuilder.DAY_MS);
        FeedTrack old = feed("old", false, 9, now - 90 * HomeFeedBuilder.DAY_MS);
        FeedTrack rare = feed("rare", false, 1, now - 90 * HomeFeedBuilder.DAY_MS);
        FeedTrack likedNever = feed("liked-never", true, 0, 0);
        List<HomeItem> items = HomeFeedBuilder.forgotten(List.of(fresh, old, rare, likedNever), now);
        List<String> got = new ArrayList<>();
        for (HomeItem i : items) got.add(i.track().id());
        assertEquals(List.of("old", "liked-never"), got);
    }

    @Test public void statsCountThisMonthOnly() {
        FeedTrack a = feed("a", false, 2, FeedFixtures.NOW);
        FeedTrack b = feed("b", false, 1, FeedFixtures.NOW);
        List<PlayEvent> events = List.of(new PlayEvent("a", FeedFixtures.NOW - 1000),
                new PlayEvent("a", FeedFixtures.NOW - 2000), new PlayEvent("b", FeedFixtures.NOW - 40 * HomeFeedBuilder.DAY_MS));
        HomeFeedBuilder.Input in = new HomeFeedBuilder.Input(List.of(a, b), events, List.of(), FeedFixtures.NOW,
                FeedFixtures.ZONE, 1, 1);
        Map<String, FeedTrack> byId = Map.of("a", a, "b", b);
        var stats = HomeFeedBuilder.stats(in.tracks(), in, byId);
        assertEquals(2, stats.tracks());
        assertEquals(2, stats.playsMonth());
        assertEquals("Artist a", stats.topArtistMonth());
        assertEquals(2 * 200_000L, stats.listenedMsMonth());
    }

    @Test public void decadesNewestFirstAndFoldersByParent() {
        List<FeedTrack> tracks = FeedFixtures.library(200, 2).tracks();
        List<HomeMix> decades = MixRanking.groups(tracks, HomeMix.Kind.DECADE, 3);
        assertTrue(decades.size() >= 2);
        assertTrue(Integer.parseInt(decades.get(0).arg()) > Integer.parseInt(decades.get(1).arg()));
        List<HomeMix> folders = MixRanking.groups(tracks, HomeMix.Kind.FOLDER, 1);
        assertEquals(20, folders.size()); // one folder per album of 10
        assertTrue(folders.get(0).arg().startsWith("Album "));
    }

    @Test public void genreCleaningDropsNumericAndUnknown() {
        assertNull(com.mhrsdev.musicplayer.data.scanner.ScannedAudio.cleanGenre("(17)"));
        assertNull(com.mhrsdev.musicplayer.data.scanner.ScannedAudio.cleanGenre("<unknown>"));
        assertNull(com.mhrsdev.musicplayer.data.scanner.ScannedAudio.cleanGenre("  "));
        assertEquals("Rock", com.mhrsdev.musicplayer.data.scanner.ScannedAudio.cleanGenre(" Rock "));
    }

    @Test public void listSectionsUseLibraryStyles() {
        List<HomeSection> s = FeedFixtures.library(200, 1).build();
        assertEquals(HomeSectionStyle.CONTINUE, find(s, "continue").style());
        assertEquals(HomeSectionStyle.AVATARS, find(s, "artists").style());
        assertEquals(HomeSectionStyle.CHIPS, find(s, "decades").style());
        assertNotNull(find(s, "stats").stats());
    }

    private static FeedTrack feed(String id, boolean liked, int plays, long last) {
        Track t = new Track(id, "Song " + id, "ar-" + id, "Artist " + id, "al-" + id, "Album " + id, 200_000, null, 1,
                null, false, liked, 2001, 1, 0);
        return new FeedTrack(t, plays, last, null, null);
    }
}
