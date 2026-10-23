package com.mhrsdev.musicplayer.ui.widget;

import android.graphics.Bitmap;

import androidx.annotation.WorkerThread;

/**
 * Player backdrop without live blur (battery rule): downscale the cover to 48 px, box-blur it three times
 * (approximates a Gaussian), boost saturation, and let the caller draw the tiny bitmap stretched with
 * bitmap filtering. One-off cost per track change (~1 ms), ~9 KB of memory, zero per-frame cost.
 */
public final class BackdropBlur {
    public static final int SIZE = 48;

    private BackdropBlur() {}

    /** Returns a new SIZE x SIZE ARGB bitmap. Call off the main thread when the cover is large. */
    @WorkerThread
    public static Bitmap create(Bitmap cover, int radius, float saturation) {
        Bitmap small = Bitmap.createScaledBitmap(cover, SIZE, SIZE, true);
        int[] px = new int[SIZE * SIZE];
        small.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        if (small != cover) small.recycle();
        blur(px, SIZE, SIZE, radius);
        saturate(px, saturation);
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        return out;
    }

    /** Three horizontal + vertical box-blur passes in place (pure, JVM-testable). */
    public static void blur(int[] px, int w, int h, int radius) {
        if (radius < 1) return;
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 3; pass++) {
            box(px, tmp, w, h, radius, true);
            box(tmp, px, w, h, radius, false);
        }
    }

    private static void box(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[index(line, clamp(i, len), w, horizontal)];
                sa += c >>> 24; sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                dst[index(line, i, w, horizontal)] = (sa / div) << 24 | (sr / div) << 16 | (sg / div) << 8 | (sb / div);
                int out = src[index(line, clamp(i - r, len), w, horizontal)];
                int in = src[index(line, clamp(i + r + 1, len), w, horizontal)];
                sa += (in >>> 24) - (out >>> 24);
                sr += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                sg += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                sb += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : (i >= len ? len - 1 : i);
    }

    /** Saturation multiply around luma (1 = unchanged, DESIGN.md uses 1.35). */
    public static void saturate(int[] px, float s) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            float l = 0.299f * r + 0.587f * g + 0.114f * b;
            px[i] = (c & 0xFF000000) | ch(l + (r - l) * s) << 16 | ch(l + (g - l) * s) << 8 | ch(l + (b - l) * s);
        }
    }

    private static int ch(float v) {
        return v < 0 ? 0 : (v > 255 ? 255 : Math.round(v));
    }
}
