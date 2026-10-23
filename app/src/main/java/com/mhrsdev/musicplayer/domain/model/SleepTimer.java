package com.mhrsdev.musicplayer.domain.model;

/**
 * Sleep timer state: {@link Kind#OFF}, {@link Kind#COUNTDOWN} (stops when {@code remainingMs} hits 0) or
 * {@link Kind#END_OF_TRACK} (stops when the current track ends). Immutable.
 */
public record SleepTimer(Kind kind, long remainingMs, long totalMs) {

    public enum Kind { OFF, COUNTDOWN, END_OF_TRACK }

    public static final SleepTimer OFF = new SleepTimer(Kind.OFF, 0L, 0L);
    public static final SleepTimer END_OF_TRACK = new SleepTimer(Kind.END_OF_TRACK, 0L, 0L);

    public static SleepTimer countdown(long remainingMs, long totalMs) {
        return new SleepTimer(Kind.COUNTDOWN, remainingMs, totalMs);
    }
}
