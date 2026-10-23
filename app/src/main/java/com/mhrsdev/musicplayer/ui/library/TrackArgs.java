package com.mhrsdev.musicplayer.ui.library;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;

/** Puts a {@link Track} into fragment arguments (so sheets survive recreation) and reads it back. */
class TrackArgs {
    private TrackArgs() { }

    static Bundle put(Bundle b, Track t) {
        b.putString("t_id", t.id());
        b.putString("t_title", t.title());
        b.putString("t_artist_id", t.artistId());
        b.putString("t_artist", t.artistName());
        b.putString("t_album_id", t.albumId());
        b.putString("t_album", t.albumTitle());
        b.putLong("t_duration", t.durationMs());
        b.putString("t_art", t.artworkUri());
        b.putInt("t_seed", t.placeholderSeed());
        b.putString("t_media", t.mediaUri());
        b.putBoolean("t_explicit", t.explicit());
        b.putBoolean("t_liked", t.liked());
        b.putInt("t_year", t.year());
        b.putInt("t_no", t.trackNumber());
        b.putLong("t_added", t.addedAtMs());
        return b;
    }

    @Nullable static Track get(@Nullable Bundle b) {
        if (b == null || b.getString("t_id") == null) return null;
        return new Track(b.getString("t_id"), b.getString("t_title", ""), b.getString("t_artist_id", ""),
                b.getString("t_artist", ""), b.getString("t_album_id", ""), b.getString("t_album", ""),
                b.getLong("t_duration"), b.getString("t_art"), b.getInt("t_seed"), b.getString("t_media"),
                b.getBoolean("t_explicit"), b.getBoolean("t_liked"), b.getInt("t_year"), b.getInt("t_no"),
                b.getLong("t_added"));
    }
}
