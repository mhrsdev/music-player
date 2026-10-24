package com.mhrsdev.musicplayer.ui.widget;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * The colour-field engine shared by {@link AmbientBackdropView} and the player backdrop: four large, soft radial
 * gradients in the artwork's colours. Shaders are built once per colour set at unit radius; drawing a frame only
 * moves/scales them through their local matrices (no allocation, no bitmap, no blur filter). {@code sec} drives the
 * slow drift (periods 19-31 s); pass a constant for a still field. Each blob fills only its own circle.
 */
public class AmbientField {
    public static final int BLOBS = 4;
    /** Base centres (fractions of w/h), drift periods (s) and phases. */
    private static final float[] BX = {0.18f, 0.86f, 0.24f, 0.78f}, BY = {0.16f, 0.30f, 0.70f, 0.86f};
    private static final float[] PERIOD = {23f, 31f, 19f, 27f}, PHASE = {0f, 1.7f, 3.1f, 4.4f};

    private final Paint[] paints = new Paint[BLOBS];
    private final Matrix[] matrices = new Matrix[BLOBS];
    private boolean ready;

    public AmbientField() {
        for (int i = 0; i < BLOBS; i++) {
            paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            matrices[i] = new Matrix();
        }
    }

    /** @param colors {@link #BLOBS} ARGB colours (their alpha = the blob's peak opacity) */
    public void setColors(int[] colors) {
        for (int i = 0; i < BLOBS; i++) {
            int c = colors[i % colors.length];
            paints[i].setShader(new RadialGradient(0, 0, 1f, new int[]{c, c & 0x00FFFFFF}, null,
                    Shader.TileMode.CLAMP));
        }
        ready = true;
    }

    public boolean isReady() { return ready; }

    /**
     * @param alpha 0..255 layer opacity
     * @param sec   animation time in seconds (constant = still)
     * @param drift 0 = blobs at their base positions, 1 = full drift
     */
    public void draw(Canvas c, int w, int h, int alpha, float sec, float drift) {
        if (!ready || w == 0 || h == 0) return;
        float r = Math.max(w, h) * 0.55f;
        for (int i = 0; i < BLOBS; i++) {
            float cx = w * (BX[i] + drift * driftX(i, sec)), cy = h * (BY[i] + drift * driftY(i, sec));
            float breathe = 1f + drift * 0.06f * (float) Math.sin(2 * Math.PI * sec / (PERIOD[i] * 0.8f) + PHASE[i]);
            Matrix m = matrices[i];
            m.setScale(r * breathe, r * breathe);
            m.postTranslate(cx, cy);
            paints[i].getShader().setLocalMatrix(m);
            paints[i].setAlpha(alpha);
            // The gradient is fully transparent beyond its radius (CLAMP of an alpha-0 edge): a circle draws exactly
            // the same pixels as the full-screen rect it replaced, but fills ~25 % fewer of them (blobs sit near the
            // edges, so much of the screen is outside each circle). Pure GPU fill saved, on every drift frame.
            c.drawCircle(cx, cy, r * breathe, paints[i]);
        }
    }

    /**
     * Fraction of a {@code w x h} frame that blob {@code i}'s circle covers at {@code sec} (sampled on a grid; tests and
     * the perf doc). The old full-screen rect was 1.0 for every blob.
     */
    public static float coverage(int i, int w, int h, float sec, float drift) {
        float r = Math.max(w, h) * 0.55f
                * (1f + drift * 0.06f * (float) Math.sin(2 * Math.PI * sec / (PERIOD[i] * 0.8f) + PHASE[i]));
        float cx = w * (BX[i] + drift * driftX(i, sec)), cy = h * (BY[i] + drift * driftY(i, sec));
        int n = 0, in = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++, n++) {
                float px = (x + 0.5f) * w / 64f - cx, py = (y + 0.5f) * h / 64f - cy;
                if (px * px + py * py <= r * r) in++;
            }
        }
        return in / (float) n;
    }

    /** Pure drift path: blob {@code i} centre offset at {@code sec}, as a fraction of the width (|x| <= 0.12). */
    public static float driftX(int i, float sec) {
        return 0.12f * (float) Math.sin(2 * Math.PI * sec / PERIOD[i] + PHASE[i]);
    }

    /** Vertical counterpart (|y| <= 0.09). */
    public static float driftY(int i, float sec) {
        return 0.09f * (float) Math.cos(2 * Math.PI * sec / (PERIOD[i] * 1.3f) + PHASE[i] * 0.7f);
    }
}
