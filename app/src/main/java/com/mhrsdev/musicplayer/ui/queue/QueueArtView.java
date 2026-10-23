package com.mhrsdev.musicplayer.ui.queue;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.ui.player.PlayerPalette;

/** 52 dp row artwork, 12 dp corners: seed gradient placeholder until T2's artwork loader is wired. */
public final class QueueArtView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private int seed;
    private boolean dirty = true;

    public QueueArtView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        radius = 12 * getResources().getDisplayMetrics().density;
    }

    public void setSeed(int s) {
        if (s == seed && !dirty) return;
        seed = s;
        dirty = true;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { dirty = true; }

    @Override protected void onDraw(Canvas c) {
        if (dirty) {
            PlayerPalette p = PlayerPalette.forSeed(seed);
            paint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(), p.primary, p.container, Shader.TileMode.CLAMP));
            dirty = false;
        }
        c.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
    }
}
