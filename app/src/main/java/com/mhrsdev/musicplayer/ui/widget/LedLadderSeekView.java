package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * LED ladder (D25): a horizontal level meter of rounded blocks stacked from a baseline, each column as tall as the
 * real loudness of that slice of the track. Played columns are lit (accent rising to a pale top block), unplayed
 * ones dim, and every unlit slot keeps a ghost block so it reads as hardware. Fully static: colours per block row
 * are cached on colour change, column levels on size / waveform change.
 */
public class LedLadderSeekView extends BaseSeekView {
    static final int ROWS = 6;
    private final Paint block = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float colW;
    private final float colGap;
    private final float blockH;
    private final float blockGap;
    private final int[] litColors = new int[ROWS];
    private int dimColor;
    private int ghostColor;
    private int[] levels = new int[0];
    private boolean colorsValid;

    public LedLadderSeekView(Context context) {
        this(context, null);
    }

    public LedLadderSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        colW = 4.5f * density;
        colGap = 2f * density;
        blockH = 3f * density;
        blockGap = 1.5f * density;
        block.setStyle(Paint.Style.FILL);
    }

    @Override public void setColors(@ColorInt int active, @ColorInt int inactive) {
        super.setColors(active, inactive);
        colorsValid = false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    @Override protected void onWaveformChanged() {
        rebuild();
    }

    private void rebuild() {
        int n = SeekShapes.cellCount(trackRight() - trackLeft(), colW, colGap);
        levels = new int[n];
        for (int i = 0; i < n; i++) {
            float v = SeekShapes.peakIn(waveform, i / (float) n, (i + 1) / (float) n);
            levels[i] = SeekShapes.ledBlocks(waveform == null ? v * 0.7f : v, ROWS);
        }
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        if (!colorsValid) {
            for (int r = 0; r < ROWS; r++) {
                litColors[r] = SeekShapes.blend(activeColor, 0xFFFFFFFF, r < ROWS - 2 ? r * 0.06f : 0.28f + (r - 3) * 0.12f);
            }
            dimColor = inactiveColor;
            ghostColor = SeekShapes.withAlpha(inactiveColor, 0.16f);
            colorsValid = true;
        }
        float total = ROWS * blockH + (ROWS - 1) * blockGap;
        float base = cy + total / 2f;
        float n = levels.length;
        float used = n * colW + (n - 1) * colGap;
        float x0 = left + (right - left - used) / 2f;
        float rr = 1.25f * density;
        for (int i = 0; i < n; i++) {
            float x = x0 + i * (colW + colGap);
            boolean played = x + colW / 2f <= thumbX;
            int lit = levels[i];
            for (int r = 0; r < ROWS; r++) {
                float b = base - r * (blockH + blockGap);
                if (r < lit) block.setColor(played ? litColors[r] : dimColor);
                else block.setColor(ghostColor);
                canvas.drawRoundRect(x, b - blockH, x + colW, b, rr, rr, block);
            }
        }
    }
}
