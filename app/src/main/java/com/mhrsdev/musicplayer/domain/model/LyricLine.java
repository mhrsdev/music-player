package com.mhrsdev.musicplayer.domain.model;

/** One lyric line. For unsynced lyrics {@code timeMs} is {@link #UNSYNCED}. */
public record LyricLine(long timeMs, String text) {
    public static final long UNSYNCED = -1L;
}
