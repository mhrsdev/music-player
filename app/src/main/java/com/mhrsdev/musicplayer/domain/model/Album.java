package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/** An album. {@code year} is 0 when unknown; artwork rules as in {@link Track}. Immutable. */
public record Album(
        String id,
        String title,
        String artistId,
        String artistName,
        int year,
        int trackCount,
        @Nullable String artworkUri,
        int placeholderSeed) {
}
