package com.mhrsdev.musicplayer.ui.theme;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.animation.PathInterpolator;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mhrsdev.musicplayer.R;

/**
 * App-wide theme state: theme mode (System/Light/Dark/Black) and the artwork-derived {@link AccentScheme}.
 *
 * <p>Cost model: one 32x32 downscale + {@link ColorExtractor} per track change (~0.1 ms), then a 600 ms color
 * crossfade (150 ms with reduced motion). Nothing runs between track changes.
 *
 * <p>Usage: {@code ThemeController.get(ctx).accent().observe(owner, scheme -> ...)};
 * on track change call {@link #setFromArtwork(Bitmap, String)} once the artwork is decoded, or
 * {@link #setFromSeedColor(int, String)} for placeholder art.
 */
public final class ThemeController {
    private static ThemeController instance;

    private final Context app;
    private final MutableLiveData<AccentScheme> accent = new MutableLiveData<>();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private AccentScheme from;
    private AccentScheme to;
    private boolean artworkColors = true;
    private boolean dark;
    @Nullable private Integer lastSeed;
    @Nullable private String currentKey;

    private ThemeController(Context context) {
        app = context.getApplicationContext();
        dark = Tokens.isDark(app);
        AccentScheme base = AccentScheme.base(dark);
        accent.setValue(base);
        from = base;
        to = base;
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(a -> accent.setValue(AccentScheme.lerp(from, to, (float) a.getAnimatedValue())));
    }

    @MainThread
    public static ThemeController get(Context context) {
        if (instance == null) instance = new ThemeController(context);
        else instance.checkUiMode(context);
        return instance;
    }

    /** Current accent roles; emits interpolated values during the crossfade. */
    public LiveData<AccentScheme> accent() {
        return accent;
    }

    public AccentScheme currentAccent() {
        AccentScheme s = accent.getValue();
        return s != null ? s : AccentScheme.base(Tokens.isDark(app));
    }

    /** Settings: "Colors from artwork". Off reverts to the base scheme. */
    @MainThread
    public void setArtworkColorsEnabled(boolean enabled) {
        artworkColors = enabled;
        if (!enabled) animateTo(AccentScheme.base(Tokens.isDark(app)));
    }

    /** Extracts the seed from {@code artwork} (any size; downscaled to 32x32). {@code key} dedupes repeats. */
    @MainThread
    public void setFromArtwork(@Nullable Bitmap artwork, @Nullable String key) {
        if (key != null && key.equals(currentKey)) return;
        currentKey = key;
        if (!artworkColors || artwork == null) {
            lastSeed = null;
            animateTo(AccentScheme.base(Tokens.isDark(app)));
            return;
        }
        setSeed(seedOf(artwork));
    }

    /** For tracks without artwork: derive the accent from the placeholder gradient color. */
    @MainThread
    public void setFromSeedColor(int seedColor, @Nullable String key) {
        if (key != null && key.equals(currentKey)) return;
        currentKey = key;
        if (!artworkColors) return;
        setSeed(seedColor);
    }

    private void setSeed(int seed) {
        lastSeed = seed;
        animateTo(AccentScheme.fromSeed(seed, Tokens.isDark(app)));
    }

    /** Light/dark switched (theme setting or system): re-derive the roles from the same seed, no animation. */
    private void checkUiMode(Context context) {
        boolean nowDark = Tokens.isDark(context);
        if (nowDark == dark) return;
        dark = nowDark;
        animator.cancel();
        AccentScheme s = lastSeed != null && artworkColors
                ? AccentScheme.fromSeed(lastSeed, nowDark) : AccentScheme.base(nowDark);
        from = s;
        to = s;
        accent.setValue(s);
    }

    /** Seed color of a bitmap; safe to call from a background thread (does not touch shared scratch). */
    public static int seedOf(Bitmap artwork) {
        Bitmap small = artwork.getWidth() <= 32 && artwork.getHeight() <= 32
                ? artwork : Bitmap.createScaledBitmap(artwork, 32, 32, true);
        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != artwork) small.recycle();
        return ColorExtractor.extractSeed(px, px.length);
    }

    private void animateTo(AccentScheme target) {
        animator.cancel();
        from = currentAccent();
        to = target;
        animator.setDuration(MotionPolicy.decorativeMotionEnabled(app) ? Tokens.MOTION_COLOR : Tokens.MOTION_COLOR_REDUCED);
        animator.start();
    }

    // ---- Theme mode ----

    /** Call from Application.onCreate and when the setting changes. */
    public static void applyMode(ThemeMode mode) {
        int night;
        switch (mode) {
            case LIGHT: night = AppCompatDelegate.MODE_NIGHT_NO; break;
            case DARK:
            case BLACK: night = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        AppCompatDelegate.setDefaultNightMode(night);
    }

    /** Theme to pass to {@link Activity#setTheme(int)} before {@code super.onCreate}. */
    @StyleRes
    public static int themeResFor(ThemeMode mode) {
        return mode == ThemeMode.BLACK ? R.style.Theme_MusicPlayer_Black : R.style.Theme_MusicPlayer;
    }
}
