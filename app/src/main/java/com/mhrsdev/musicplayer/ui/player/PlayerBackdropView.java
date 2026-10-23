package com.mhrsdev.musicplayer.ui.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Artwork-light backdrop (DESIGN §2.3) without live blur: the cover pre-downscaled once per track to a tiny bitmap
 * ({@link #prepare}) and upscaled with bilinear filtering (= a soft blur), a radial glow of the cover's color and the
 * measured scrim.
 *
 * <p>Living colour field (L1): four large soft radial gradients in the cover's own colours (the averages of the tiny
 * bitmap's quadrants, taken once per track) drift on slow sine paths (periods 19-31 s). It animates at <= 15 fps only
 * while {@link #setAnimating} is on (player visible and playing) and decorative motion is allowed; otherwise the same
 * field is drawn once, still. Per frame only the shaders' local matrices move: no allocation, no bitmap, no blur.
 * On track change the new layer fades in (alpha only, 600 ms).
 */
public class PlayerBackdropView extends View {
    private static final int TINY = 20;

    /** One fully prepared layer: gradient + glow shaders and an optional tiny artwork bitmap. */
    private static class Layer {
        final Paint base = new Paint();
        final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Matrix matrix = new Matrix();
        @Nullable Bitmap bitmap;
        PlayerPalette palette;
        final Paint[] blobs = new Paint[BLOBS];
        final Matrix[] blobMatrix = new Matrix[BLOBS];
        Layer() {
            for (int i = 0; i < BLOBS; i++) {
                blobs[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
                blobMatrix[i] = new Matrix();
            }
        }
    }

    private static final int BLOBS = 4;
    static final long DRIFT_FRAME_MS = 66; // <= 15 fps
    /** Base centres (fractions of w/h), drift periods (s) and phases of the four colour blobs. */
    private static final float[] BX = {0.18f, 0.86f, 0.24f, 0.78f}, BY = {0.16f, 0.30f, 0.70f, 0.86f};
    private static final float[] PERIOD = {23f, 31f, 19f, 27f}, PHASE = {0f, 1.7f, 3.1f, 4.4f};
    private boolean animating;
    private long driftStart = android.os.SystemClock.uptimeMillis();
    private final Runnable driftFrame = this::driftFrame;

    private Layer back = new Layer(), front = new Layer();
    private final Paint scrim = new Paint();
    private float t = 1f;
    private ValueAnimator anim;
    private boolean light;
    /** Scrim alpha per stop (0, .35, .7, 1) after the contrast guard; the design's base values until measured. */
    private final float[] scrimAlpha = new float[4];

    // DESIGN §2.3 step 3 base scrims (top→bottom) and the text they protect.
    private static final float[] DARK_BASE = {0.30f, 0.22f, 0.62f, 0.82f};
    private static final float[] LIGHT_BASE = {0.62f, 0.55f, 0.84f, 0.94f};
    static final int DARK_SCRIM = 0x0F1216, LIGHT_SCRIM = 0xF0F7FD;
    /** Text colors over the backdrop: on-dark white, on-light onSurface. */
    public static final int TEXT_ON_DARK = 0xFFFFFFFF, TEXT_ON_LIGHT = 0xFF181C21;

    public PlayerBackdropView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        front.palette = back.palette = PlayerPalette.forSeed(0);
        System.arraycopy(DARK_BASE, 0, scrimAlpha, 0, 4);
    }

    /** Light-theme variant (design 15): pale scrim, dark text. */
    public void setLight(boolean light) {
        if (this.light == light) return;
        this.light = light;
        guard();
        buildScrim();
        invalidate();
    }

    public boolean isLight() { return light; }

    /**
     * Contrast guard: measures the downscaled cover (as drawn: 75% over the container) under the lyrics band and the
     * title/controls band and raises the scrim alpha there only as far as needed (inactive lyrics white/onSurface .60
     * ≥ 3:1 large text, artist/time text .72 ≥ 4.5:1).
     */
    private void guard() {
        float[] base = light ? LIGHT_BASE : DARK_BASE;
        System.arraycopy(base, 0, scrimAlpha, 0, 4);
        Bitmap bm = front.bitmap;
        if (bm == null || bm.isRecycled() || front.palette == null) return;
        int w = bm.getWidth(), h = bm.getHeight();
        int[] px = new int[w * h];
        bm.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = ContrastGuard.blend(px[i], front.palette.container, 0.75f);
        int scrimRgb = light ? LIGHT_SCRIM : DARK_SCRIM;
        int text = light ? TEXT_ON_LIGHT : TEXT_ON_DARK;
        float top = ContrastGuard.requiredScrimAlpha(px, w, h, 0.10f, 0.62f, scrimRgb, base[1],
                (text & 0x00FFFFFF) | 0x99000000, 3.0);
        float bottom = ContrastGuard.requiredScrimAlpha(px, w, h, 0.62f, 1f, scrimRgb, base[2],
                (text & 0x00FFFFFF) | 0xB8000000, 4.5);
        float dt = top - base[1], db = bottom - base[2];
        scrimAlpha[0] = Math.min(1f, base[0] + dt);
        scrimAlpha[1] = top;
        scrimAlpha[2] = bottom;
        scrimAlpha[3] = Math.min(1f, base[3] + db);
    }

    /** For tests and the render gallery: the guarded scrim alpha at stop {@code i} (0..3). */
    public float scrimAlphaAt(int i) { return scrimAlpha[i]; }

    private void buildScrim() {
        int h = getHeight();
        if (h == 0) return;
        int rgb = light ? LIGHT_SCRIM : DARK_SCRIM;
        int[] colors = new int[4];
        for (int i = 0; i < 4; i++) colors[i] = (Math.round(scrimAlpha[i] * 255) << 24) | rgb;
        scrim.setShader(new LinearGradient(0, 0, 0, h, colors, new float[]{0f, 0.35f, 0.7f, 1f},
                Shader.TileMode.CLAMP));
    }

    /** Downscales the cover once (off the hot path, tiny result: ~1.6 KB) for use as the blurred backdrop. */
    @Nullable public static Bitmap prepare(@Nullable Bitmap cover) {
        if (cover == null || cover.isRecycled()) return null;
        Bitmap mid = Bitmap.createScaledBitmap(cover, TINY * 2, TINY * 2, true);
        Bitmap tiny = Bitmap.createScaledBitmap(mid, TINY, TINY, true);
        if (mid != tiny && mid != cover) mid.recycle();
        return tiny;
    }

    public void setContent(PlayerPalette p, @Nullable Bitmap tinyArt, boolean animate) {
        Layer l = back; // reuse the hidden layer
        back = front;
        front = l;
        front.palette = p;
        front.bitmap = tinyArt;
        build(front);
        guard();
        buildScrim();
        if (anim != null) anim.cancel();
        if (!animate || !PlayerPalette.motionAllowed(getContext())) { t = 1f; invalidate(); return; }
        anim = ValueAnimator.ofFloat(0f, 1f).setDuration(600);
        anim.addUpdateListener(v -> { t = (float) v.getAnimatedValue(); invalidate(); });
        t = 0f;
        anim.start();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        buildScrim();
        build(back);
        build(front);
    }

    private void build(Layer l) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || l.palette == null) return;
        PlayerPalette p = l.palette;
        l.base.setShader(new LinearGradient(0, 0, 0, h, p.container, p.deep, Shader.TileMode.CLAMP));
        l.glow.setShader(new RadialGradient(w * 0.7f, h * 0.28f, Math.max(w, h) * 0.6f,
                (p.glow & 0x00FFFFFF) | 0x8C000000, p.glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        if (l.bitmap != null) {
            float bleed = 80 * getResources().getDisplayMetrics().density;
            float scale = Math.max((w + bleed) / l.bitmap.getWidth(), (h + bleed) / l.bitmap.getHeight());
            l.matrix.setScale(scale, scale);
            l.matrix.postTranslate((w - l.bitmap.getWidth() * scale) / 2f, (h - l.bitmap.getHeight() * scale) / 2f);
        }
        int[] colors = blobColors(l.bitmap, p);
        float r = Math.max(w, h) * 0.55f;
        for (int i = 0; i < BLOBS; i++) {
            // unit-radius gradient at the origin; the drift only moves/scales it through the local matrix
            l.blobs[i].setShader(new RadialGradient(0, 0, 1f, new int[]{colors[i], colors[i] & 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            l.blobMatrix[i].setScale(r, r);
        }
    }

    /**
     * Four colours for the field: the average of each quadrant of the tiny cover (so they are the cover's own hues),
     * saturation lifted a little; without artwork the palette's glow/container colours.
     */
    static int[] blobColors(@Nullable Bitmap bm, PlayerPalette p) {
        int[] out = new int[BLOBS];
        if (bm == null || bm.isRecycled()) {
            int[] f = {p.glow, p.container, p.deep, p.glow};
            for (int i = 0; i < BLOBS; i++) out[i] = (f[i] & 0x00FFFFFF) | 0x8C000000;
            return out;
        }
        int w = bm.getWidth(), h = bm.getHeight();
        int[] px = new int[w * h];
        bm.getPixels(px, 0, w, 0, 0, w, h);
        for (int q = 0; q < BLOBS; q++) {
            int x0 = (q % 2) * w / 2, y0 = (q / 2) * h / 2;
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = y0; y < y0 + h / 2; y++) {
                for (int x = x0; x < x0 + w / 2; x++) {
                    int c = px[y * w + x];
                    r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n++;
                }
            }
            if (n == 0) n = 1;
            float[] hsv = new float[3];
            android.graphics.Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.3f + 0.05f);
            out[q] = (android.graphics.Color.HSVToColor(hsv) & 0x00FFFFFF) | 0x9E000000;
        }
        // quadrant order TL, TR, BL, BR matches the blob centres
        return out;
    }

    /** Player visible and playing: drift (if motion is allowed). Otherwise one still frame. */
    public void setAnimating(boolean on) {
        boolean run = on && PlayerPalette.motionAllowed(getContext());
        if (run == animating) return;
        animating = run;
        removeCallbacks(driftFrame);
        if (run) postOnAnimation(driftFrame);
    }

    public boolean isAnimating() { return animating; }

    private void driftFrame() {
        if (!animating) return;
        if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE || !isShown()) { animating = false; return; }
        invalidate();
        postOnAnimationDelayed(driftFrame, DRIFT_FRAME_MS);
    }

    /** Pure drift path: blob {@code i} centre offset at {@code sec}, as a fraction of the size (|x|,|y| <= 0.12). */
    static float driftX(int i, float sec) {
        return 0.12f * (float) Math.sin(2 * Math.PI * sec / PERIOD[i] + PHASE[i]);
    }

    static float driftY(int i, float sec) {
        return 0.09f * (float) Math.cos(2 * Math.PI * sec / (PERIOD[i] * 1.3f) + PHASE[i] * 0.7f);
    }

    private void drawBlobs(Canvas c, Layer l, int alpha, float sec) {
        int w = getWidth(), h = getHeight();
        float r = Math.max(w, h) * 0.55f;
        for (int i = 0; i < BLOBS; i++) {
            Paint p = l.blobs[i];
            if (p.getShader() == null) continue;
            float cx = w * (BX[i] + driftX(i, sec)), cy = h * (BY[i] + driftY(i, sec));
            float breathe = 1f + 0.06f * (float) Math.sin(2 * Math.PI * sec / (PERIOD[i] * 0.8f) + PHASE[i]);
            Matrix m = l.blobMatrix[i];
            m.setScale(r * breathe, r * breathe);
            m.postTranslate(cx, cy);
            p.getShader().setLocalMatrix(m);
            p.setAlpha(alpha);
            c.drawRect(0, 0, w, h, p);
        }
    }

    private void drawLayer(Canvas c, Layer l, int alpha) {
        int w = getWidth(), h = getHeight();
        l.base.setAlpha(alpha);
        c.drawRect(0, 0, w, h, l.base);
        if (l.bitmap != null) {
            l.art.setAlpha(alpha * 3 / 4);
            c.drawBitmap(l.bitmap, l.matrix, l.art);
        }
        drawBlobs(c, l, alpha * 7 / 10, (android.os.SystemClock.uptimeMillis() - driftStart) / 1000f);
        l.glow.setAlpha(alpha);
        c.drawRect(0, 0, w, h, l.glow);
    }

    @Override protected void onDraw(Canvas c) {
        if (t < 1f) drawLayer(c, back, 255);
        drawLayer(c, front, t < 1f ? (int) (255 * t) : 255);
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        removeCallbacks(driftFrame);
        animating = false;
        super.onDetachedFromWindow();
    }
}
