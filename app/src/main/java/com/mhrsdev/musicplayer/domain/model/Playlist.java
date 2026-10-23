package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A user playlist.
 *
 * @param artworkUris up to 4 artwork URIs for a mosaic cover; may be empty (use {@code placeholderSeed})
 */
public record Playlist(
        String id,
        String name,
        int trackCount,
        @Nullable String description,
        List<String> artworkUris,
        int placeholderSeed) {

    public Playlist {
        artworkUris = Collections.unmodifiableList(new ArrayList<>(artworkUris));
    }

    /** Copy with a different track count (counts are derived by repositories). */
    public Playlist withTrackCount(int trackCount) {
        return new Playlist(id, name, trackCount, description, artworkUris, placeholderSeed);
    }
}
