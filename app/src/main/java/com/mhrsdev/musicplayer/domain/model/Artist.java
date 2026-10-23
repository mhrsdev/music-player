package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/** An artist. Image/placeholder rules as in {@link Track}. Immutable. */
public record Artist(
        String id,
        String name,
        int albumCount,
        int trackCount,
        @Nullable String imageUri,
        int placeholderSeed) {
}
