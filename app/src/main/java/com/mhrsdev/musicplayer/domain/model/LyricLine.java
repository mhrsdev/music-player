package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One lyric line. For unsynced lyrics {@code timeMs} is {@link #UNSYNCED}.
 *
 * @param words     word/syllable timings in time order, or null when the line has none
 * @param estimated true when {@code words} were emulated from the line timing ({@code WordTimingEstimator}),
 *                  false when they come from the file (enhanced LRC, TTML, SYLT...)
 */
public record LyricLine(long timeMs, String text, @Nullable List<LyricWord> words, boolean estimated) {
    public static final long UNSYNCED = -1L;

    public LyricLine {
        words = words == null || words.isEmpty() ? null : Collections.unmodifiableList(new ArrayList<>(words));
    }

    /** A line without word timings (the original two-field form). */
    public LyricLine(long timeMs, String text) {
        this(timeMs, text, null, false);
    }

    public boolean hasWords() {
        return words != null;
    }

    /** End of the last word, or -1 without words. */
    public long wordsEndMs() {
        return words == null ? -1L : words.get(words.size() - 1).endMs();
    }

    /**
     * Index of the word being sung (or last sung) at {@code positionMs}: the last word whose start is {@code <=}
     * the position; -1 before the first word or without words.
     */
    public int activeWordIndex(long positionMs) {
        if (words == null) return -1;
        int lo = 0, hi = words.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (words.get(mid).startMs() <= positionMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    public LyricLine withWords(@Nullable List<LyricWord> w, boolean isEstimated) {
        return new LyricLine(timeMs, text, w, isEstimated);
    }
}
