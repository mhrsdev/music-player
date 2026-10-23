package com.mhrsdev.musicplayer.playback;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import com.mhrsdev.musicplayer.domain.model.Track;

/**
 * Domain {@link Track} <-> Media3 {@link MediaItem}. The mediaId is the track id; the complete track travels in
 * the metadata extras, so the player itself is the single source of truth for the queue (no side maps, and a
 * queue restored after process death or seen by a headless service decodes the same way).
 */
final class TrackMapper {
    private static final String ID = "id", TITLE = "title", ARTIST_ID = "artistId", ARTIST = "artist",
            ALBUM_ID = "albumId", ALBUM = "album", DURATION = "duration", ARTWORK = "artwork", SEED = "seed",
            URI = "uri", EXPLICIT = "explicit", LIKED = "liked", YEAR = "year", NUMBER = "number", ADDED = "added";

    private TrackMapper() {}

    /** Only tracks with a playable URI can be queued (fakes have none). */
    static boolean isPlayable(Track track) {
        return track.mediaUri() != null;
    }

    static MediaItem toMediaItem(Track t) {
        Bundle extras = new Bundle();
        extras.putString(ID, t.id());
        extras.putString(TITLE, t.title());
        extras.putString(ARTIST_ID, t.artistId());
        extras.putString(ARTIST, t.artistName());
        extras.putString(ALBUM_ID, t.albumId());
        extras.putString(ALBUM, t.albumTitle());
        extras.putLong(DURATION, t.durationMs());
        extras.putString(ARTWORK, t.artworkUri());
        extras.putInt(SEED, t.placeholderSeed());
        extras.putString(URI, t.mediaUri());
        extras.putBoolean(EXPLICIT, t.explicit());
        extras.putBoolean(LIKED, t.liked());
        extras.putInt(YEAR, t.year());
        extras.putInt(NUMBER, t.trackNumber());
        extras.putLong(ADDED, t.addedAtMs());
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .setTitle(t.title())
                .setArtist(t.artistName())
                .setAlbumTitle(t.albumTitle())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(extras);
        if (t.durationMs() > 0) meta.setDurationMs(t.durationMs());
        if (t.trackNumber() > 0) meta.setTrackNumber(t.trackNumber());
        if (t.artworkUri() != null) meta.setArtworkUri(Uri.parse(t.artworkUri()));
        return new MediaItem.Builder()
                .setMediaId(t.id())
                .setUri(t.mediaUri())
                .setMediaMetadata(meta.build())
                .build();
    }

    /** Inverse of {@link #toMediaItem}; null for an item that was not created by it. */
    @Nullable
    static Track fromMediaItem(@Nullable MediaItem item) {
        Bundle e = item == null ? null : item.mediaMetadata.extras;
        if (e == null || e.getString(ID) == null) return null;
        return new Track(e.getString(ID), nz(e.getString(TITLE)), nz(e.getString(ARTIST_ID)),
                nz(e.getString(ARTIST)), nz(e.getString(ALBUM_ID)), nz(e.getString(ALBUM)), e.getLong(DURATION),
                e.getString(ARTWORK), e.getInt(SEED), e.getString(URI), e.getBoolean(EXPLICIT),
                e.getBoolean(LIKED), e.getInt(YEAR), e.getInt(NUMBER), e.getLong(ADDED));
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }
}
