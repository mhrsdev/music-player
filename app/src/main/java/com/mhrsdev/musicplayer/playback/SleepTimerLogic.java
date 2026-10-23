package com.mhrsdev.musicplayer.playback;

import com.mhrsdev.musicplayer.domain.model.SleepTimer;

/**
 * Sleep timer state machine, pure Java. A countdown only runs while playback is running (like a real
 * "stop after N minutes of music"), so a paused player has no pending work. The caller passes a monotonic clock
 * ({@code now}, ms), schedules one timeout from {@link #nextWakeMs}, and applies {@link #volume} (fade-out
 * during the last {@link #FADE_MS}) and {@link #isExpired}.
 */
final class SleepTimerLogic {
    static final long FADE_MS = 8_000L;
    static final long FADE_STEP_MS = 250L;

    private SleepTimer.Kind kind = SleepTimer.Kind.OFF;
    private long totalMs;
    private long remainingMs;
    private long countingSince = -1L;

    SleepTimer.Kind kind() {
        return kind;
    }

    void startCountdown(long totalMs, boolean playing, long now) {
        kind = SleepTimer.Kind.COUNTDOWN;
        this.totalMs = Math.max(0L, totalMs);
        remainingMs = this.totalMs;
        countingSince = playing ? now : -1L;
    }

    void startEndOfTrack() {
        kind = SleepTimer.Kind.END_OF_TRACK;
        totalMs = 0L;
        remainingMs = 0L;
        countingSince = -1L;
    }

    void cancel() {
        kind = SleepTimer.Kind.OFF;
        totalMs = 0L;
        remainingMs = 0L;
        countingSince = -1L;
    }

    /** Playback started/stopped; freezes or resumes a running countdown. */
    void setPlaying(boolean playing, long now) {
        if (kind != SleepTimer.Kind.COUNTDOWN) return;
        if (playing && countingSince < 0) {
            countingSince = now;
        } else if (!playing && countingSince >= 0) {
            remainingMs = remainingMs(now);
            countingSince = -1L;
        }
    }

    long remainingMs(long now) {
        if (kind != SleepTimer.Kind.COUNTDOWN) return 0L;
        long elapsed = countingSince >= 0 ? now - countingSince : 0L;
        return Math.max(0L, remainingMs - elapsed);
    }

    boolean isExpired(long now) {
        return kind == SleepTimer.Kind.COUNTDOWN && remainingMs(now) <= 0L;
    }

    /** Player volume in [0, 1]: 1 until the last FADE_MS of a countdown, then a linear fade to 0. */
    float volume(long now) {
        if (kind != SleepTimer.Kind.COUNTDOWN) return 1f;
        long fade = Math.min(FADE_MS, totalMs);
        if (fade <= 0) return 0f;
        return Math.min(1f, remainingMs(now) / (float) fade);
    }

    /** Delay until the next fade step / expiry while a countdown is running, else -1 (nothing to schedule). */
    long nextWakeMs(long now) {
        if (kind != SleepTimer.Kind.COUNTDOWN || countingSince < 0) return -1L;
        long remaining = remainingMs(now);
        long fade = Math.min(FADE_MS, totalMs);
        if (remaining > fade) return remaining - fade;
        return Math.min(remaining, FADE_STEP_MS);
    }

    SleepTimer snapshot(long now) {
        switch (kind) {
            case COUNTDOWN: return SleepTimer.countdown(remainingMs(now), totalMs);
            case END_OF_TRACK: return SleepTimer.END_OF_TRACK;
            default: return SleepTimer.OFF;
        }
    }
}
