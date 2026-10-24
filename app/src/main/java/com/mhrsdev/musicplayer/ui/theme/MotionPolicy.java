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
    /** What every effect follows: the user's profile, tightened while the phone is hot. */
    private static volatile PerformanceProfile profile = PerformanceProfile.FULL;
    /** The user's choice in Settings &gt; Performance. */
    private static volatile PerformanceProfile userProfile = PerformanceProfile.FULL;
    /** 0 cool, 1 warm (thermal MODERATE): at most Balanced, 2 hot (SEVERE or worse): at most Light. */
    private static volatile int thermal;

    private MotionPolicy() {}

    /** Settings &gt; Performance changed (main thread, from the settings LiveData): applies it app-wide, live. */
    public static void setPerformance(PerformanceProfile p) {
        userProfile = p;
        apply();
    }

    /**
     * Adaptive thermal (v0.3.1, D29), always on: {@code ThermalWatch} reports the platform's thermal status; while the
     * phone is warm the effects behave at most like Balanced (60 fps cap, no full-screen drift, no lyrics blur), while
     * hot at most like Light, and the user's own profile returns when it cools down. Nothing is saved.
     */
    public static void setThermalLevel(int level) {
        int l = Math.max(0, Math.min(2, level));
        if (l == thermal) return;
        thermal = l;
        apply();
    }

    /** True while the thermal state is holding effects back (Settings &gt; Performance shows a note). */
    public static boolean thermalThrottled() {
        return thermal > 0 && !effective(userProfile, thermal).equals(userProfile);
    }

    private static void apply() {
        PerformanceProfile p = effective(userProfile, thermal);
        profile = p;
        appReduceMotion = p.reduceMotion();
        FrameDriver.get().setMaxFps(p.maxFps());
    }

    /** {@code user} tightened to at most Balanced (level 1) / Light (level 2): never more effects than the user chose. */
    static PerformanceProfile effective(PerformanceProfile user, int level) {
        if (level <= 0) return user;
        PerformanceProfile cap = level == 1 ? PerformanceProfile.BALANCED : PerformanceProfile.LIGHT;
        int fps = user.maxFps() == PerformanceProfile.FPS_SCREEN ? cap.maxFps() : Math.min(user.maxFps(), cap.maxFps());
        return new PerformanceProfile(user.reduceMotion() || cap.reduceMotion(), user.transitions() && cap.transitions(),
                user.bounce() && cap.bounce(), user.movingBackdrop() && cap.movingBackdrop(),
                user.lyricsBlur() && cap.lyricsBlur(), user.lyricsWords() && cap.lyricsWords(),
                user.seekMotion() && cap.seekMotion(), fps);
    }

    /** The profile the effects follow right now (the user's, or tightened while hot). */
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
