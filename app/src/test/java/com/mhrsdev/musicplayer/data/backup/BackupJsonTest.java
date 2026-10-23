package com.mhrsdev.musicplayer.data.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class BackupJsonTest {
    static final TrackRef A = new TrackRef("Song \"A\"", "Artist", "Album", 200_000, "content://media/1");
    static final TrackRef B = new TrackRef("آهنگ", "سهراب", "Album 2", 181_500, null);

    static BackupData sample() {
        return new BackupData(BackupData.FORMAT_VERSION, 1234L, "0.1.0", Map.of("theme", "DARK", "sleep_timer_minutes", 15),
                List.of(new BackupData.Like(A, 10L), new BackupData.Like(B, 20L)),
                List.of(new BackupData.PlaylistEntry("Road trip", "desc", 5L, List.of(B, A)),
                        new BackupData.PlaylistEntry("Empty", null, 6L, List.of())),
                List.of(new BackupData.Play(A, 7, 99L)));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test public void roundTripKeepsEverythingAndPlaylistOrder() throws Exception {
        BackupData back = BackupJson.parse(BackupJson.write(sample()));
        assertEquals(sample(), back);
        assertEquals(List.of(B, A), back.playlists().get(0).tracks());
        assertNull(back.playlists().get(1).description());
        assertNull(back.likes().get(1).track().path());
    }

    @Test public void tracksAreListedOnce() {
        String json = new String(BackupJson.write(sample()), StandardCharsets.UTF_8);
        assertEquals(2, json.split("\"t\":", -1).length - 1);
    }

    @Test public void newerVersionIsReportedAsSuch() {
        String json = new String(BackupJson.write(sample()), StandardCharsets.UTF_8).replace("\"version\":1", "\"version\":2");
        try {
            BackupJson.parse(bytes(json));
            fail();
        } catch (BackupFormatException e) {
            assertTrue(e.newerVersion);
        }
    }

    @Test public void corruptFilesAreRejected() throws Exception {
        String good = new String(BackupJson.write(sample()), StandardCharsets.UTF_8);
        String[] bad = {
            "", "not json", "{}", "[]", good.substring(0, good.length() / 2),
            good.replace("musicplayer-backup", "other-app"),
            good.replace("\"version\":1", "\"version\":0"),
            good.replace("\"likes\":[[0,10]", "\"likes\":[[9,10]"),
            good.replace("\"likes\"", "\"liked\""),
        };
        for (String s : bad) {
            try {
                BackupJson.parse(bytes(s));
                fail("accepted: " + s);
            } catch (BackupFormatException e) {
                assertFalse(s, e.newerVersion);
            }
        }
    }
}
