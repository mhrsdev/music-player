package com.mhrsdev.musicplayer.domain.model;

/**
 * One timed unit of a lyric line (a word or a syllable group), as sung.
 *
 * @param startMs when the unit starts (absolute track time, ms)
 * @param endMs   when it ends; always {@code >= startMs}
 * @param text    the unit's text (no surrounding spaces)
 * @param start   start char offset of the unit inside {@link LyricLine#text()}
 * @param end     end char offset (exclusive)
 */
public record LyricWord(long startMs, long endMs, String text, int start, int end) {

    /** 0..1 progress of the unit at {@code positionMs}. */
    public float progress(long positionMs) {
        if (positionMs <= startMs) return 0f;
        if (positionMs >= endMs) return 1f;
        return (positionMs - startMs) / (float) (endMs - startMs);
    }
}
