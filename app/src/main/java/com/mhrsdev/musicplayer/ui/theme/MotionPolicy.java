package com.mhrsdev.musicplayer.ui.theme;

import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;

import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.ui.motion.FrameDriver;

/**
 * Decides whether decorative animation (wave travel, glow, springs) may run.
 * Off when the system animator scale is 0, battery saver is on, or the app's own reduce-motion setting is on.
 * Widgets then draw a static, equally legible frame.
 *
 * <p>Settings &gt; Performance (D26) is applied here and only here: {@link #setPerformance} stores the profile, sets the
 * frame-rate cap on the one shared {@link FrameDriver}, and each effect asks its own question below (so a view never
 * reads the settings itself). Every question is "decorative motion allowed AND this effect on", except the lyrics
 * word sweep, which carries timing information and so keeps running under reduce motion, as it always did.
 */
public class MotionPolicy {
    private static volatile boolean appReduceMotion;
    /** The user's battery-saver choice: TRUE = always on, FALSE = always off, null = follow the system. */
    private static volatile Boolean saverOverride;
    private static volatile PerformanceProfile profile = PerformanceProfile.FULL;

    private MotionPolicy() {}

    /** Settings &gt; Performance changed (main thread, from the settings LiveData): applies it app-wide, live. */
    public static void setPerformance(PerformanceProfile p) {
        profile = p;
        appReduceMotion = p.reduceMotion();
        FrameDriver.get().setMaxFps(p.maxFps());
    }

    public static PerformanceProfile performance() {
        return profile;
    }

    /** Page transitions, the player's cover flight and the cover slide on song change. */
    public static boolean transitionsEnabled(Context c) {
        return profile.transitions() && decorativeMotionEnabled(c);
    }

    /** Press rebound, play/pause jiggle, like pop (the play/pause shape morph itself always runs: it shows state). */
    public static boolean bounceEnabled() {
        return profile.bounce();
    }

    /** Player background drift; off = the same colour field drawn once, still. */
    public static boolean backdropMotionEnabled(Context c) {
        return profile.movingBackdrop() && decorativeMotionEnabled(c);
    }

    /** Wavy seek bar travel / settle; off = a still wave. */
    public static boolean seekMotionEnabled(Context c) {
        return profile.seekMotion() && decorativeMotionEnabled(c);
    }

    /** Per-line lyrics blur (the caller adds the API / low-RAM checks). */
    public static boolean lyricsBlurEnabled(Context c) {
        return profile.lyricsBlur() && decorativeMotionEnabled(c);
    }

    /** Word-by-word sweep on the sung line (30 fps loop); off = the whole line lights at once, no frame loop. */
    public static boolean lyricsWordsEnabled() {
        return profile.lyricsWords();
    }

    /** Settings screen's battery-saver choice (see {@code MusicPlayerApp}); null follows the system. */
    public static void setBatterySaverOverride(Boolean forced) {
        saverOverride = forced;
    }

    public static boolean decorativeMotionEnabled(Context context) {
        if (appReduceMotion) return false;
        Context app = context.getApplicationContext();
        float scale = Settings.Global.getFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (scale == 0f) return false;
        return !batterySaver(app);
    }

    /** Battery saver only (skip grain, glow and other purely decorative layers). */
    public static boolean batterySaver(Context context) {
        Boolean forced = saverOverride;
        if (forced != null) return forced;
        PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }
}
