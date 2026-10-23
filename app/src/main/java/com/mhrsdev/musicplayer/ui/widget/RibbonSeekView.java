package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * Ribbon (D25): a thick rounded ribbon. The played part is filled with a deep-to-bright gradient that always ends
 * brightest at the thumb (one unit gradient re-mapped with a pre-allocated matrix, so a position change never builds
 * a shader), with a soft glow cap; the unplayed part is a faint inset groove. Fully static, cheap.
 */
public class RibbonSeekView extends BaseSeekView {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groove = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final float ribbonH;
    private final float glowR;
    @Nullable private LinearGradient gradient;
    private boolean shadersValid;

    public RibbonSeekView(Context context) {
        this(context, null);
    }

    public RibbonSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ribbonH = 10f * density;
        glowR = 20f * density;
        fill.setStyle(Paint.Style.FILL);
        groove.setStyle(Paint.Style.FILL);
        glow.setStyle(Paint.Style.FILL);
        cap.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f * density);
    }

    @Override public void setColors(@ColorInt int active, @ColorInt int inactive) {
        super.setColors(active, inactive);
        shadersValid = false;
    }

    private void buildShaders() {
        int deep = SeekShapes.withAlpha(SeekShapes.blend(activeColor, 0xFF000000, 0.15f), 0.55f);
        int bright = SeekShapes.blend(activeColor, 0xFFFFFFFF, 0.35f);
        gradient = new LinearGradient(0f, 0f, 1f, 0f, new int[] {deep, activeColor, bright},
                new float[] {0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
        fill.setShader(gradient);
        glow.setShader(new RadialGradient(0f, 0f, glowR,
                new int[] {SeekShapes.withAlpha(activeColor, 0.55f), SeekShapes.withAlpha(activeColor, 0.18f), 0},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        groove.setColor(inactiveColor);
        cap.setColor(SeekShapes.blend(activeColor, 0xFFFFFFFF, 0.82f));
        ring.setColor(activeColor);
        shadersValid = true;
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        if (!shadersValid) buildShaders();
        float r = ribbonH / 2f;
        // Unplayed groove: thinner, inset, so the played ribbon reads as raised above it.
        float g = 3f * density;
        canvas.drawRoundRect(left, cy - g, right, cy + g, g, g, groove);
        if (thumbX > left + 0.5f) {
            // glow under the cap
            int save = canvas.save();
            canvas.translate(thumbX, cy);
            canvas.drawCircle(0f, 0f, glowR, glow);
            canvas.restoreToCount(save);
            float span = Math.max(1f, thumbX - left);
            matrix.setScale(span, 1f);
            matrix.postTranslate(left, 0f);
            gradient.setLocalMatrix(matrix);
            canvas.drawRoundRect(left, cy - r, Math.max(left + ribbonH, thumbX), cy + r, r, r, fill);
        }
    }

    /** The cap: a pale pearl ringed in the accent, 14 dp (20 dp while dragging). */
    @Override protected void drawThumb(Canvas canvas, float thumbX, float cy, float grow) {
        float rr = (7f + 3f * grow) * density;
        canvas.drawCircle(thumbX, cy, rr, cap);
        canvas.drawCircle(thumbX, cy, rr - ring.getStrokeWidth() / 2f, ring);
    }
}
