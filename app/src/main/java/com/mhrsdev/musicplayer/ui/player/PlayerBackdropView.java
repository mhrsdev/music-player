package com.mhrsdev.musicplayer.ui.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * Artwork-light backdrop (DESIGN §2.3) without live blur: a soft radial glow of the cover's color over a deep
 * gradient plus the measured scrim. Static; crossfades (800 ms) only on track change. Shaders rebuilt per change.
 */
public final class PlayerBackdropView extends View {
    private final Paint base = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrim = new Paint();
    private int fromGlow, toGlow, fromDeep, toDeep;
    private float t = 1f;
    private ValueAnimator anim;

    public PlayerBackdropView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        PlayerPalette p = PlayerPalette.forSeed(0);
        fromGlow = toGlow = p.glow;
        fromDeep = toDeep = p.deep;
    }

    public void setPalette(PlayerPalette p, boolean animate) {
        fromGlow = ColorUtils.blendARGB(fromGlow, toGlow, t);
        fromDeep = ColorUtils.blendARGB(fromDeep, toDeep, t);
        toGlow = p.glow;
        toDeep = p.deep;
        if (anim != null) anim.cancel();
        if (!animate || !PlayerPalette.motionAllowed(getContext())) { t = 1f; rebuild(); return; }
        anim = ValueAnimator.ofFloat(0f, 1f).setDuration(800);
        anim.addUpdateListener(v -> { t = (float) v.getAnimatedValue(); rebuild(); });
        t = 0f;
        anim.start();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        scrim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x4D0F1216, 0x380F1216, 0x9E0F1216, 0xD10F1216},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        rebuild();
    }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        int g = ColorUtils.blendARGB(fromGlow, toGlow, t), d = ColorUtils.blendARGB(fromDeep, toDeep, t);
        base.setShader(new LinearGradient(0, 0, 0, h, ColorUtils.blendARGB(g, d, 0.55f), d, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(w * 0.7f, h * 0.3f, Math.max(w, h) * 0.6f,
                (g & 0x00FFFFFF) | 0x8C000000, g & 0x00FFFFFF, Shader.TileMode.CLAMP));
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), base);
        c.drawRect(0, 0, getWidth(), getHeight(), glow);
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
