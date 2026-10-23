package com.mhrsdev.musicplayer.ui.widget;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * Contract shared by {@link WavySeekView}, {@link WaveformSeekView} and {@link BarsSeekView}, so the player can swap
 * styles (Settings: seek style) without code changes. All times are milliseconds. The bar is always LTR.
 */
public interface SeekBarView {

    /** Touch / accessibility seek callbacks. Commit the seek in {@link #onSeekEnd}. */
    interface OnSeekListener {
        void onSeekStart(long positionMs);

        void onSeek(long positionMs);

        void onSeekEnd(long positionMs);
    }

    void setDuration(long durationMs);

    /** Ignored while the user is dragging. */
    void setPosition(long positionMs);

    /** Buffered position for streams; pass 0 / duration for local files. */
    void setBuffered(long bufferedMs);

    /** Normalised amplitudes 0..1 (e.g. 120 buckets); null = not ready. Styles that do not use it ignore it. */
    void setWaveform(@Nullable float[] amplitudes);

    /** Playing drives decorative motion (wave travel); paused settles to a static frame. */
    void setPlaying(boolean playing);

    void setColors(@ColorInt int active, @ColorInt int inactive);

    void setOnSeekListener(@Nullable OnSeekListener listener);

    long getPosition();

    long getDuration();

    boolean isDragging();
}
