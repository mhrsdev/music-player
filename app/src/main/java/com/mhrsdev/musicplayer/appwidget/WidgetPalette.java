package com.mhrsdev.musicplayer.appwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.mhrsdev.musicplayer.ui.theme.AccentScheme;
import com.mhrsdev.musicplayer.ui.theme.ColorExtractor;

/** The few colors a widget uses, derived from the cover (same extractor and scheme as the player) or the base theme. */
record WidgetPalette(int bg, int text, int subText, int primary, int onPrimary) {

    /** ImageView.setColorFilter(int) is SRC_ATOP over a white drawable, so translucent tints must be pre-mixed opaque. */
    int coverBg() {
        return mix(bg, text, 0.14f);
    }

    int note() {
        return mix(bg, text, 0.7f);
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static final int SAMPLE = 32;

    static WidgetPalette of(Context context, @Nullable Bitmap art, boolean dark) {
        AccentScheme scheme;
        if (art != null) {
            scheme = AccentScheme.fromSeed(seed(art), dark);
        } else if (Build.VERSION.SDK_INT >= 31) {
            return dynamic(context, dark);
        } else {
            scheme = AccentScheme.base(dark);
        }
        return new WidgetPalette(scheme.container(), scheme.onContainer(), withAlpha(scheme.onContainer(), 0.74f),
                scheme.primary(), scheme.onPrimary());
    }

    /** Cover seed from a 32x32 copy: one tiny bitmap, 1024 ints. */
    static int seed(Bitmap art) {
        Bitmap tiny = Bitmap.createScaledBitmap(art, SAMPLE, SAMPLE, true);
        int[] pixels = new int[SAMPLE * SAMPLE];
        tiny.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE);
        if (tiny != art) tiny.recycle();
        return ColorExtractor.extractSeed(pixels, pixels.length);
    }

    /** No cover: Android 12+ wallpaper colors (accent2 container, accent1 buttons) so the widget matches the home screen. */
    @RequiresApi(31)
    private static WidgetPalette dynamic(Context c, boolean dark) {
        int bg = c.getColor(dark ? android.R.color.system_accent2_800 : android.R.color.system_accent2_100);
        int text = c.getColor(dark ? android.R.color.system_accent2_100 : android.R.color.system_accent2_900);
        return new WidgetPalette(bg, text, withAlpha(text, 0.74f),
                c.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600),
                c.getColor(dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0));
    }

    static int withAlpha(int color, float alpha) {
        return (Math.round(255 * alpha) << 24) | (color & 0xFFFFFF);
    }
}
