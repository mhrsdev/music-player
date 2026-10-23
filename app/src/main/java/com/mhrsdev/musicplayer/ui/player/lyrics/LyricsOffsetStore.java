package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Per-track lyrics timing offset (+ = lyrics later), ±{@link #STEP_MS} per tap, clamped to ±{@link #MAX_MS}. One tiny
 * preferences file, written only when the user nudges; 0 removes the entry.
 */
public class LyricsOffsetStore {
    public static final long STEP_MS = 500, MAX_MS = 10_000;
    private final SharedPreferences prefs;

    public LyricsOffsetStore(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE);
    }

    public long get(String trackId) {
        return prefs.getLong(trackId, 0L);
    }

    /** @return the new offset */
    public long nudge(String trackId, long deltaMs) {
        long v = clamp(get(trackId) + deltaMs);
        if (v == 0) prefs.edit().remove(trackId).apply();
        else prefs.edit().putLong(trackId, v).apply();
        return v;
    }

    static long clamp(long v) {
        return Math.max(-MAX_MS, Math.min(MAX_MS, v));
    }

    /** "+0.5 s" / "−1.0 s" / "0 s" in the locale's digits. */
    public static String format(long ms, Locale locale) {
        if (ms == 0) return String.format(locale, "%d s", 0);
        return (ms > 0 ? "+" : "−") + String.format(locale, "%.1f s", Math.abs(ms) / 1000.0);
    }
}
