package com.mhrsdev.musicplayer.ui.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Square player cover with 32 dp corners. Draws the deterministic seed gradient (DESIGN §12 "no cover") until T2's
 * bitmap loader is wired; shaders are rebuilt only on size/track change, never per frame.
 */
public final class PlayerArtworkView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private PlayerPalette palette = PlayerPalette.forSeed(0);

    public PlayerArtworkView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        radius = 32 * getResources().getDisplayMetrics().density;
        note.setColor(0x4DFFFFFF);
        note.setTextAlign(Paint.Align.CENTER);
        setElevation(18 * getResources().getDisplayMetrics().density);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    @Override protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h) > 0 ? MeasureSpec.getSize(h) : Integer.MAX_VALUE);
        setMeasuredDimension(size, size);
    }

    public void setPalette(PlayerPalette p) {
        palette = p;
        rebuild();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        paint.setShader(new LinearGradient(0, 0, w, h, palette.container, palette.deep, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(w * 0.68f, h * 0.36f, w * 0.55f,
                (palette.primary & 0x00FFFFFF) | 0x99000000, palette.primary & 0x00FFFFFF, Shader.TileMode.CLAMP));
        note.setTextSize(w * 0.28f);
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        c.drawRect(0, 0, getWidth(), getHeight(), glow);
        c.drawText("♪", getWidth() / 2f, getHeight() * 0.6f, note);
    }
}
