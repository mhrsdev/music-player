package com.mhrsdev.musicplayer.data.backup;

import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * A track described by what survives a move to another device (MediaStore ids do not): title, artist, album and
 * duration. {@code path} (the stored media URI) is only the fallback key.
 */
public record TrackRef(String title, String artist, String album, long durationMs, @Nullable String path) {

    /** Case/space-insensitive identity; duration in whole seconds because decoders differ by a few ms. */
    public String key() {
        return norm(title) + '\u0001' + norm(artist) + '\u0001' + norm(album) + '\u0001' + Math.round(durationMs / 1000.0);
    }

    private static String norm(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
