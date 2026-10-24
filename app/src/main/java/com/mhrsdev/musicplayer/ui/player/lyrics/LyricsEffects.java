package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.data.settings.DeviceClass;
import com.mhrsdev.musicplayer.data.settings.LyricsTextSize;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;

/** Lyrics presentation policy: when blur may run, text sizes, the scroll anchor. */
public class LyricsEffects {
    /** The active line's top sits at this fraction of the list height. */
    public static final float ANCHOR = 0.36f;

    private LyricsEffects() { }

    /**
     * Per-line blur only on API 31+ (RenderEffect on the line's own RenderNode), and never in battery saver, with
     * reduced motion / animator scale 0, with Settings &gt; Performance "Lyrics blur" off, or on a low-RAM device:
     * those get alpha + scale falloff only.
     */
    public static boolean blurAllowed(Context c) {
        return blurSupported(c) && MotionPolicy.lyricsBlurEnabled(c);
    }

    /** The phone can do it: API 31+ (RenderEffect) and not a low-RAM / small-heap one (a blur layer per line). */
    public static boolean blurSupported(Context c) {
        return Build.VERSION.SDK_INT >= 31 && !DeviceClass.lowEnd(DeviceClass.detect(c));
    }

    public static float textSp(@Nullable LyricsTextSize s) {
        if (s == null) return 26f;
        return switch (s) {
            case SMALL -> 22f;
            case MEDIUM -> 26f;
            case LARGE -> 31f;
        };
    }

    /** A+ ({@code dir > 0}) / A− one step, clamped. */
    public static LyricsTextSize step(@Nullable LyricsTextSize s, int dir) {
        LyricsTextSize[] all = LyricsTextSize.values();
        int i = s == null ? 1 : s.ordinal();
        return all[Math.max(0, Math.min(all.length - 1, i + (dir > 0 ? 1 : -1)))];
    }
}
