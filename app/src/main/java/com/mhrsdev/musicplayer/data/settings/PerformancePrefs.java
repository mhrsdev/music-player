package com.mhrsdev.musicplayer.data.settings;

import android.content.SharedPreferences;
import java.util.Map;

/** SharedPreferences keys of {@link PerformanceProfile}; kept apart so {@link SettingsStore} only delegates. */
class PerformancePrefs {
    /** The key the older "Reduce motion" switch already used: existing choices carry over. */
    private static final String K_REDUCE_MOTION = "reduce_motion";
    private static final String K_TRANSITIONS = "perf_transitions";
    private static final String K_BOUNCE = "perf_bounce";
    private static final String K_BACKDROP = "perf_moving_backdrop";
    private static final String K_LYRICS_BLUR = "perf_lyrics_blur";
    private static final String K_LYRICS_WORDS = "perf_lyrics_words";
    private static final String K_SEEK = "perf_seek_motion";
    private static final String K_FPS = "perf_max_fps";

    /** Keys a settings backup carries, with their stored type. */
    static final Map<String, Class<?>> BACKUP_TYPES = Map.of(
            K_REDUCE_MOTION, Boolean.class, K_TRANSITIONS, Boolean.class, K_BOUNCE, Boolean.class,
            K_BACKDROP, Boolean.class, K_LYRICS_BLUR, Boolean.class, K_LYRICS_WORDS, Boolean.class,
            K_SEEK, Boolean.class, K_FPS, Integer.class);

    private PerformancePrefs() { }

    /** True once the user (or a restored backup) saved any Performance setting, including the older reduce-motion switch. */
    static boolean hasSaved(SharedPreferences p) {
        for (String key : BACKUP_TYPES.keySet()) if (p.contains(key)) return true;
        return false;
    }

    static PerformanceProfile read(SharedPreferences p) {
        PerformanceProfile d = PerformanceProfile.FULL;
        return new PerformanceProfile(
                p.getBoolean(K_REDUCE_MOTION, d.reduceMotion()),
                p.getBoolean(K_TRANSITIONS, d.transitions()),
                p.getBoolean(K_BOUNCE, d.bounce()),
                p.getBoolean(K_BACKDROP, d.movingBackdrop()),
                p.getBoolean(K_LYRICS_BLUR, d.lyricsBlur()),
                p.getBoolean(K_LYRICS_WORDS, d.lyricsWords()),
                p.getBoolean(K_SEEK, d.seekMotion()),
                p.getInt(K_FPS, d.maxFps()));
    }

    static void write(SharedPreferences.Editor e, PerformanceProfile v) {
        e.putBoolean(K_REDUCE_MOTION, v.reduceMotion())
                .putBoolean(K_TRANSITIONS, v.transitions())
                .putBoolean(K_BOUNCE, v.bounce())
                .putBoolean(K_BACKDROP, v.movingBackdrop())
                .putBoolean(K_LYRICS_BLUR, v.lyricsBlur())
                .putBoolean(K_LYRICS_WORDS, v.lyricsWords())
                .putBoolean(K_SEEK, v.seekMotion())
                .putInt(K_FPS, v.maxFps());
    }
}
