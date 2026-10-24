package com.mhrsdev.musicplayer.ui.widget;

import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.mhrsdev.musicplayer.ui.theme.ColorExtractor;

/**
 * Colours of an ambient backdrop, from one cover: the {@link ColorExtractor} seed of the 32x32 downscale plus the
 * average of each quadrant (so the field keeps the cover's own spread), each mixed a little toward the seed and
 * saturation-lifted. Contrast guard: lightness is clamped (dark theme L <= 0.42, light theme L >= 0.72) so white /
 * onSurface text over the field keeps >= 4.5:1. Results are cached per artwork key (small LRU, 48 entries ~ 6 KB).
 *
 * @param blobs {@link AmbientField#BLOBS} colours with alpha
 * @param top   top colour of the base gradient (opaque)
 * @param bottom bottom colour of the base gradient (opaque)
 */
public record AmbientPalette(int[] blobs, int top, int bottom, int seed) {
    private static final LruCache<String, AmbientPalette> CACHE = new LruCache<>(48);

    @Nullable public static AmbientPalette cached(String key, boolean dark) {
        return CACHE.get(key + (dark ? "#d" : "#l"));
    }

    public static void put(String key, boolean dark, AmbientPalette p) {
        CACHE.put(key + (dark ? "#d" : "#l"), p);
    }

    /**
     * A bitmap whose pixels can be read: a HARDWARE bitmap (any decoder or a future loader may hand one out) throws
     * IllegalStateException from getPixels, and createScaledBitmap keeps it HARDWARE: copy it to software once.
     */
    public static Bitmap readable(Bitmap b) {
        if (b.getConfig() != Bitmap.Config.HARDWARE) return b;
        Bitmap copy = b.copy(Bitmap.Config.ARGB_8888, false);
        return copy != null ? copy : b;
    }

    /** Call off the main thread for big bitmaps; a <= 96 px bitmap is fine on the main thread (< 1 ms). */
    public static AmbientPalette fromBitmap(Bitmap art, boolean dark) {
        art = readable(art);
        Bitmap small = art.getWidth() <= 32 && art.getHeight() <= 32 ? art : Bitmap.createScaledBitmap(art, 32, 32, true);
        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != art) small.recycle();
        int seed = ColorExtractor.extractSeed(px, px.length);
        int[] q = new int[AmbientField.BLOBS];
        for (int k = 0; k < q.length; k++) q[k] = ColorUtils.blendARGB(quadrant(px, w, h, k), seed, 0.35f);
        return build(seed, q, dark);
    }

    /** No artwork: one seed colour (placeholder gradient / neutral accent) spread into four related hues. */
    public static AmbientPalette fromSeed(int seed, boolean dark) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(seed, hsl);
        int[] q = new int[AmbientField.BLOBS];
        float[] shift = {0f, 24f, -18f, 40f};
        for (int k = 0; k < q.length; k++) {
            q[k] = ColorUtils.HSLToColor(new float[]{(hsl[0] + shift[k] + 360f) % 360f, hsl[1], hsl[2]});
        }
        return build(seed, q, dark);
    }

    private static AmbientPalette build(int seed, int[] q, boolean dark) {
        int[] blobs = new int[q.length];
        for (int k = 0; k < q.length; k++) blobs[k] = (guard(q[k], dark, 1.25f) & 0x00FFFFFF) | (dark ? 0xA0000000 : 0x90000000);
        int top = guard(ColorUtils.blendARGB(seed, dark ? 0xFF0F1216 : 0xFFF0F7FD, dark ? 0.45f : 0.55f), dark, 1f);
        int bottom = dark ? ColorUtils.blendARGB(seed, 0xFF0B0D10, 0.86f) : ColorUtils.blendARGB(seed, 0xFFF7FAFD, 0.86f);
        return new AmbientPalette(blobs, top | 0xFF000000, bottom | 0xFF000000, seed);
    }

    /** Lightness clamp that keeps text readable over the colour (see class doc), with a saturation lift. */
    static int guard(int c, boolean dark, float satLift) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(c, hsl);
        hsl[1] = Math.min(0.85f, hsl[1] * satLift + (hsl[1] > 0.06f ? 0.04f : 0f));
        hsl[2] = dark ? Math.min(0.42f, Math.max(0.16f, hsl[2])) : Math.max(0.72f, Math.min(0.90f, hsl[2]));
        return ColorUtils.HSLToColor(hsl);
    }

    private static int quadrant(int[] px, int w, int h, int k) {
        int x0 = (k % 2) * w / 2, y0 = (k / 2) * h / 2;
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = y0; y < y0 + h / 2; y++) {
            for (int x = x0; x < x0 + w / 2; x++) {
                int c = px[y * w + x];
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0xFF5F7FA6;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }
}
