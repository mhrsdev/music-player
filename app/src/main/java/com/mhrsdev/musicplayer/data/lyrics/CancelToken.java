package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;

/** Cooperative cancellation for one lyrics lookup; a running HTTP call registers {@code disconnect} here. */
public final class CancelToken {
    private volatile boolean cancelled;
    @Nullable private volatile Runnable onCancel;

    public void cancel() {
        cancelled = true;
        Runnable r = onCancel;
        if (r != null) r.run();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Registers (or clears, with null) what to run on cancel; runs it at once when already cancelled. */
    public void setOnCancel(@Nullable Runnable r) {
        onCancel = r;
        if (r != null && cancelled) r.run();
    }
}
