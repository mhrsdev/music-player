package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/**
 * What a home-screen widget needs to know about playback; absent (null) when nothing is queued.
 * {@code albumId}/{@code albumTitle} let the widget draw the same generated cover as the app when there is no art.
 */
public record NowPlaying(String title, String artist, @Nullable String artworkUri, boolean playing, long positionMs,
        long durationMs, @Nullable String albumId, @Nullable String albumTitle) {

    public NowPlaying(String title, String artist, @Nullable String artworkUri, boolean playing, long positionMs,
                      long durationMs) {
        this(title, artist, artworkUri, playing, positionMs, durationMs, null, null);
    }

    /** Fraction played, 0..1. */
    public float progress() {
        return durationMs <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) positionMs / durationMs));
    }
}
