package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Wavy seek bar: the played part is a sine wave (3 dp amplitude, 26 dp wavelength) that travels one wavelength per
 * 1.6 s while playing and flattens in 300 ms on pause; the rest is a straight line. Reduced motion: a static wave.
 * Frames are requested at 30 fps only while playing and visible.
 */
public class WavySeekView extends BaseSeekView {
    private final Path wavePath = new Path();
    private final float amplitude;
    private final float wavelength;
    private final float gap;
    private final long phaseOrigin = SystemClock.uptimeMillis();
    private float amp; // 0 flat .. 1 full

    public WavySeekView(Context context) {
        this(context, null);
    }

    public WavySeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        amplitude = Tokens.WAVE_AMPLITUDE * density;
        wavelength = Tokens.WAVE_LENGTH * density;
        gap = 6f * density;
    }

    @Override protected boolean wantsFrames() {
        return playing || amp > 0f;
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        drawWave(canvas, left, right, cy, thumbX, dt);
    }

    /** Also used by {@link WaveformSeekView} as its fallback until the waveform is ready. */
    protected final void drawWave(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        float target = playing ? 1f : 0f;
        amp = canAnimate() ? WaveMath.approach(amp, target, dt, Tokens.WAVE_SETTLE_MS) : target;
        float phase = canAnimate() && playing
                ? WaveMath.phaseAt(SystemClock.uptimeMillis() - phaseOrigin, Tokens.WAVE_PERIOD_MS) : 0f;

        float end = thumbX - gap;
        if (end > left) {
            float a = amplitude * amp;
            if (a < 0.25f) {
                canvas.drawLine(left, cy, end, cy, activePaint);
            } else {
                wavePath.rewind();
                float step = 2f * density;
                wavePath.moveTo(left, cy + WaveMath.y(0f, wavelength, a, phase));
                for (float x = left + step; x < end; x += step) {
                    wavePath.lineTo(x, cy + WaveMath.y(x - left, wavelength, a, phase));
                }
                wavePath.lineTo(end, cy + WaveMath.y(end - left, wavelength, a, phase));
                canvas.drawPath(wavePath, activePaint);
            }
        }
        float start = thumbX + gap;
        if (start < right) canvas.drawLine(start, cy, right, cy, inactivePaint);
    }
}
