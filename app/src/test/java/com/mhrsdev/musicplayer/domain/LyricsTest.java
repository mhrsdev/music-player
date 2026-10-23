package com.mhrsdev.musicplayer.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import java.util.List;
import org.junit.Test;

public class LyricsTest {
    private final Lyrics synced = new Lyrics("t", List.of(
            new LyricLine(1_000, "a"), new LyricLine(5_000, "b"), new LyricLine(9_000, "c")), true, null);

    @Test public void activeLineIsLastLineAtOrBeforePosition() {
        assertEquals(-1, synced.activeLineIndex(0));
        assertEquals(0, synced.activeLineIndex(1_000));
        assertEquals(0, synced.activeLineIndex(4_999));
        assertEquals(1, synced.activeLineIndex(5_000));
        assertEquals(2, synced.activeLineIndex(60_000));
    }

    @Test public void unsyncedLyricsHaveNoActiveLine() {
        Lyrics plain = new Lyrics("t", List.of(new LyricLine(LyricLine.UNSYNCED, "x")), false, null);
        assertEquals(-1, plain.activeLineIndex(10_000));
    }

    @Test public void noneIsEmpty() {
        assertTrue(Lyrics.none("t").isEmpty());
    }
}
