package com.mhrsdev.musicplayer.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lyrics of one track, synced (LRC) or plain. A result with no lines means "no lyrics found" ({@link #none}).
 *
 * @param source free text such as "Embedded", "LRC file", "LRCLIB"; may be null
 */
public record Lyrics(String trackId, List<LyricLine> lines, boolean synced, String source) {

    public Lyrics {
        lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /** The "nothing found" result. */
    public static Lyrics none(String trackId) {
        return new Lyrics(trackId, Collections.emptyList(), false, null);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Index of the line active at {@code positionMs} (last line whose time is <= positionMs),
     * or -1 before the first line and for unsynced lyrics.
     */
    public int activeLineIndex(long positionMs) {
        if (!synced) return -1;
        int lo = 0;
        int hi = lines.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= positionMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
