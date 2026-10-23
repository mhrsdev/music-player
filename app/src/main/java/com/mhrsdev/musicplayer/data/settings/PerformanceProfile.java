package com.mhrsdev.musicplayer.data.settings;

/**
 * Settings &gt; Performance (owner D26): which visual effects run. Default = {@link Preset#FULL} (D24: we optimise, we
 * don't cut); the other presets are an opt-in escape hatch for slow phones. Pure value, persisted by
 * {@link PerformancePrefs}, applied app-wide by {@code ui.theme.MotionPolicy} (live, no restart).
 *
 * @param reduceMotion   the existing "Reduce motion" switch: when on, every decorative animation is off (overrides the
 *                       effect switches below, like the system's "Remove animations")
 * @param transitions    page transitions, the player's cover flight on open, the cover slide on song change
 * @param bounce         press rebound, play/pause jiggle, like "pop"
 * @param movingBackdrop player background drifts (else drawn once, still)
 * @param lyricsBlur     inactive lyric lines blurred by distance (API 31+)
 * @param lyricsWords    word-by-word sweep on the sung line (else the whole line lights up)
 * @param seekMotion     wavy seek bar travels while playing (else a still wave)
 * @param maxFps         cap of the shared animation frame driver: {@link #FPS_SCREEN}, 60 or 30
 */
public record PerformanceProfile(boolean reduceMotion, boolean transitions, boolean bounce, boolean movingBackdrop,
        boolean lyricsBlur, boolean lyricsWords, boolean seekMotion, int maxFps) {

    /** No cap: animations follow the screen's refresh rate (60, 90, 120 Hz). */
    public static final int FPS_SCREEN = 0;
    public static final int[] FPS_CHOICES = {FPS_SCREEN, 60, 30};

    public enum Preset { FULL, BALANCED, LIGHT, CUSTOM }

    public static final PerformanceProfile FULL =
            new PerformanceProfile(false, true, true, true, true, true, true, FPS_SCREEN);
    /** Drops the two GPU-heavy layers (full-screen drift, per-line blur) and caps at 60 fps; motion stays. */
    public static final PerformanceProfile BALANCED =
            new PerformanceProfile(false, true, true, false, false, true, true, 60);
    /** Nothing loops on its own; springs still answer touches, at 30 fps. */
    public static final PerformanceProfile LIGHT =
            new PerformanceProfile(false, false, false, false, false, false, false, 30);

    public PerformanceProfile {
        if (maxFps != FPS_SCREEN && maxFps != 60 && maxFps != 30) maxFps = FPS_SCREEN;
    }

    public static PerformanceProfile of(Preset p) {
        return switch (p) {
            case BALANCED -> BALANCED;
            case LIGHT -> LIGHT;
            default -> FULL; // CUSTOM is not selectable: it is what any other mix is called
        };
    }

    /** The preset this mix equals, or {@link Preset#CUSTOM}. */
    public Preset preset() {
        if (equals(FULL)) return Preset.FULL;
        if (equals(BALANCED)) return Preset.BALANCED;
        if (equals(LIGHT)) return Preset.LIGHT;
        return Preset.CUSTOM;
    }

    public PerformanceProfile withReduceMotion(boolean v) {
        return new PerformanceProfile(v, transitions, bounce, movingBackdrop, lyricsBlur, lyricsWords, seekMotion, maxFps);
    }

    public PerformanceProfile withTransitions(boolean v) {
        return new PerformanceProfile(reduceMotion, v, bounce, movingBackdrop, lyricsBlur, lyricsWords, seekMotion, maxFps);
    }

    public PerformanceProfile withBounce(boolean v) {
        return new PerformanceProfile(reduceMotion, transitions, v, movingBackdrop, lyricsBlur, lyricsWords, seekMotion, maxFps);
    }

    public PerformanceProfile withMovingBackdrop(boolean v) {
        return new PerformanceProfile(reduceMotion, transitions, bounce, v, lyricsBlur, lyricsWords, seekMotion, maxFps);
    }

    public PerformanceProfile withLyricsBlur(boolean v) {
        return new PerformanceProfile(reduceMotion, transitions, bounce, movingBackdrop, v, lyricsWords, seekMotion, maxFps);
    }

    public PerformanceProfile withLyricsWords(boolean v) {
        return new PerformanceProfile(reduceMotion, transitions, bounce, movingBackdrop, lyricsBlur, v, seekMotion, maxFps);
    }

    public PerformanceProfile withSeekMotion(boolean v) {
        return new PerformanceProfile(reduceMotion, transitions, bounce, movingBackdrop, lyricsBlur, lyricsWords, v, maxFps);
    }

    public PerformanceProfile withMaxFps(int fps) {
        return new PerformanceProfile(reduceMotion, transitions, bounce, movingBackdrop, lyricsBlur, lyricsWords, seekMotion, fps);
    }
}
