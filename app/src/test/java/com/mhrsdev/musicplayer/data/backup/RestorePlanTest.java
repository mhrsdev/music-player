package com.mhrsdev.musicplayer.data.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RestorePlanTest {
    private static final TrackRef LOCAL_A = new TrackRef("song a", "ARTIST", "album", 200_400, "content://other/9");

    @Test public void matchesByNormalisedKeyAcrossDevices() {
        TrackMatcher m = new TrackMatcher(Map.of("42", LOCAL_A));
        // case, spacing and sub-second duration noise do not matter; ids and paths differ
        assertEquals("42", m.match(new TrackRef("  Song   A ", "artist", "Album", 199_900, "content://media/1")));
        assertNull(m.match(new TrackRef("Song A", "artist", "Album", 230_000, null)));
    }

    @Test public void fallsBackToPath() {
        TrackMatcher m = new TrackMatcher(Map.of("42", LOCAL_A));
        assertEquals("42", m.match(new TrackRef("Retagged", "Someone", "Else", 1, "content://other/9")));
    }

    @Test public void planDropsUnmatchedAndCountsThemOnce() {
        TrackMatcher m = new TrackMatcher(Map.of("42", LOCAL_A));
        TrackRef ghost = new TrackRef("Ghost", "x", "y", 1000, null);
        TrackRef a = new TrackRef("Song A", "Artist", "Album", 200_000, null);
        BackupData data = new BackupData(1, 0, "", Map.of("theme", "DARK"),
                List.of(new BackupData.Like(a, 5), new BackupData.Like(ghost, 6)),
                List.of(new BackupData.PlaylistEntry("P", null, 1, List.of(ghost, a, a))),
                List.of(new BackupData.Play(ghost, 3, 1), new BackupData.Play(a, 2, 8)));
        RestorePlan plan = RestorePlan.of(data, m);
        assertEquals(Map.of("42", 5L), plan.likes());
        assertEquals(List.of("42"), plan.playlists().get(0).trackIds());
        assertEquals(Map.of("42", new RestorePlan.Stat(2, 8)), plan.plays());
        assertEquals(1, plan.unmatched());
        assertEquals(1, plan.summary().playlists());
        assertEquals(1, plan.summary().likes());
    }
}
