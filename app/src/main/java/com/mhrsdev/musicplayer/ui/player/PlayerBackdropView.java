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
 * measured scrim. Static; on track change the new layer fades in over 800 ms (alpha only, no per-frame allocation).
 */
public final class PlayerBackdropView extends View {
    private static final int TINY = 20;

    /** One fully prepared layer: gradient + glow shaders and an optional tiny artwork bitmap. */
    private static final class Layer {
        final Paint base = new Paint();
        final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Matrix matrix = new Matrix();
        @Nullable Bitmap bitmap;
        PlayerPalette palette;
    }

    private Layer back = new Layer(), front = new Layer();
    private final Paint scrim = new Paint();
    private float t = 1f;
    private ValueAnimator anim;

    public PlayerBackdropView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        front.palette = back.palette = PlayerPalette.forSeed(0);
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
        if (anim != null) anim.cancel();
        if (!animate || !PlayerPalette.motionAllowed(getContext())) { t = 1f; invalidate(); return; }
        anim = ValueAnimator.ofFloat(0f, 1f).setDuration(800);
        anim.addUpdateListener(v -> { t = (float) v.getAnimatedValue(); invalidate(); });
        t = 0f;
        anim.start();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        scrim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x4D0F1216, 0x380F1216, 0x9E0F1216, 0xD10F1216},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
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
    }

    private void drawLayer(Canvas c, Layer l, int alpha) {
        int w = getWidth(), h = getHeight();
        l.base.setAlpha(alpha);
        c.drawRect(0, 0, w, h, l.base);
        if (l.bitmap != null) {
            l.art.setAlpha(alpha * 3 / 4);
            c.drawBitmap(l.bitmap, l.matrix, l.art);
        }
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
        super.onDetachedFromWindow();
    }
}
