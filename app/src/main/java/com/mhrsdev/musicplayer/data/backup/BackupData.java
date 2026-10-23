package com.mhrsdev.musicplayer.data.backup;

import androidx.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** The content of a backup file, independent of device ids. Waveform and lyrics caches are deliberately not part of it. */
public record BackupData(int version, long createdAt, String appVersion, Map<String, Object> settings,
        List<Like> likes, List<PlaylistEntry> playlists, List<Play> plays) {

    /** Bump when the JSON shape changes incompatibly; older files must stay readable. */
    public static final int FORMAT_VERSION = 1;

    public record Like(TrackRef track, long likedAt) { }

    /** {@code tracks} are in playlist order. */
    public record PlaylistEntry(String name, @Nullable String description, long createdAt, List<TrackRef> tracks) { }

    public record Play(TrackRef track, int count, long lastPlayedAt) { }
}
