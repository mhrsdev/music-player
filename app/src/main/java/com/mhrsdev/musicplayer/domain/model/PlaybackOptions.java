package com.mhrsdev.musicplayer.domain.model;

/**
 * Playback tweaks that outlive a track: speed and pitch (1.0 = unchanged; speed changes keep the pitch unless
 * {@code pitch} is set apart), silence skipping and ReplayGain. Immutable.
 */
public record PlaybackOptions(float speed, float pitch, boolean skipSilence, ReplayGainMode replayGain) {
    public static final float MIN = 0.5f;
    public static final float MAX = 2.0f;
    public static final float STEP = 0.05f;

    public static final PlaybackOptions DEFAULT = new PlaybackOptions(1f, 1f, false, ReplayGainMode.OFF);

    /** Clamps to [MIN, MAX] and snaps to {@link #STEP}, so 1.2999 and 1.30 are the same stored value. */
    public static float snap(float value) {
        float clamped = Math.max(MIN, Math.min(MAX, value));
        return Math.round(clamped / STEP) * STEP;
    }
}
