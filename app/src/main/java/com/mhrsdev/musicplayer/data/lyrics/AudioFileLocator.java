package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;

/** Finds the audio file of a track on disk, or null when it has no accessible path (then file sources are skipped). */
public interface AudioFileLocator {

    @Nullable
    File locate(Track track);

    /** Handles {@code file://} URIs and absolute paths only; use {@link MediaStoreFileLocator} for content URIs. */
    AudioFileLocator FILE_ONLY = track -> {
        String uri = track.mediaUri();
        if (uri == null) return null;
        if (uri.startsWith("file://")) {
            try {
                return new File(new java.net.URI(uri));
            } catch (java.net.URISyntaxException | IllegalArgumentException e) {
                return null;
            }
        }
        return uri.startsWith("/") ? new File(uri) : null;
    };
}
