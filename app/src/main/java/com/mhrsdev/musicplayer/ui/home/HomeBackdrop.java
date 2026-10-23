package com.mhrsdev.musicplayer.ui.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.ui.nav.HomeGlow;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import com.mhrsdev.musicplayer.ui.widget.ArtworkLoader;
import com.mhrsdev.musicplayer.ui.widget.PlaceholderArt;
import java.util.concurrent.Executor;

/**
 * Tints Home's top glow from a cover: the focused hero card after the carousel settles, else the playing track. Last
 * event wins; repeats are deduped by key. A 64 px bitmap comes from the shared {@link ArtworkLoader} cache, the colour is
 * extracted on the io executor and cached; the glow then makes one cross-fade (instant under reduce-motion / battery
 * saver). "Colors from artwork" off = the neutral default glow. Nothing runs while scrolling.
 */
class HomeBackdrop {
    private static final int ART_PX = 64;
    private static final LruCache<String, Integer> COLORS = new LruCache<>(64);

    private final Activity activity;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable private String currentKey;

    HomeBackdrop(Activity activity, Executor io) {
        this.activity = activity;
        this.io = io;
    }

    /** @param artworkUri null = placeholder gradient: its seed colour is used directly */
    @MainThread
    void show(@Nullable String artworkUri, int placeholderSeed) {
        View glow = HomeGlow.find(activity);
        if (glow == null) return;
        if (!ThemeController.get(activity).isArtworkColorsEnabled()) {
            currentKey = null;
            HomeGlow.setTint(glow, null, animate());
            return;
        }
        String key = artworkUri != null ? artworkUri : "seed:" + placeholderSeed;
        if (key.equals(currentKey)) return;
        currentKey = key;
        Integer cached = COLORS.get(key);
        if (cached != null) {
            apply(key, cached);
        } else if (artworkUri == null) {
            int c = glowColor(PlaceholderArt.seedColor(placeholderSeed));
            COLORS.put(key, c);
            apply(key, c);
        } else {
            ArtworkLoader.get(activity).load(artworkUri, ART_PX, (k, bmp) -> {
                if (bmp == null) {
                    show(null, placeholderSeed); // no cover: fall back to the placeholder hue
                    return;
                }
                io.execute(() -> {
                    int c = glowColor(ThemeController.seedOf(bmp)); // off the main thread
                    COLORS.put(key, c);
                    main.post(() -> apply(key, c));
                });
            });
        }
    }

    private void apply(String key, int color) {
        if (!key.equals(currentKey) || activity.isFinishing()) return; // a newer request won
        View glow = HomeGlow.find(activity);
        if (glow != null) HomeGlow.setTint(glow, color, animate());
    }

    private boolean animate() {
        return MotionPolicy.decorativeMotionEnabled(activity) && !MotionPolicy.batterySaver(activity);
    }

    /** Keeps the cover's hue but a soft, glow-friendly saturation / value, so dull or neon covers both read well. */
    static int glowColor(int seed) {
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        hsv[1] = Math.max(0.35f, Math.min(0.7f, hsv[1]));
        hsv[2] = 0.85f;
        return Color.HSVToColor(hsv) & 0xFFFFFF;
    }
}
