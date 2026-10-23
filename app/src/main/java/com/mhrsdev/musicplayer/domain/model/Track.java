package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/**
 * A playable song. Immutable value object.
 *
 * <p>Artwork: when {@link #artworkUri} is null the UI draws a deterministic gradient from {@link #placeholderSeed}.
 * {@link #year} and {@link #trackNumber} use 0 for "unknown".
 *
 * @param mediaUri playable URI (content://, file://, https://); null for fakes
 * @param addedAtMs epoch millis when added to the library (drives "Recently saved" / "New releases")
 */
public record Track(
        String id,
        String title,
        String artistId,
        String artistName,
        String albumId,
        String albumTitle,
        long durationMs,
        @Nullable String artworkUri,
        int placeholderSeed,
        @Nullable String mediaUri,
        boolean explicit,
        boolean liked,
        int year,
        int trackNumber,
        long addedAtMs) {

    /** Copy of this track with a different like flag. */
    public Track withLiked(boolean liked) {
        return new Track(id, title, artistId, artistName, albumId, albumTitle, durationMs, artworkUri,
                placeholderSeed, mediaUri, explicit, liked, year, trackNumber, addedAtMs);
    }
}
