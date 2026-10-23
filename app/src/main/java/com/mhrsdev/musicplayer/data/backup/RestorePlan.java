package com.mhrsdev.musicplayer.data.backup;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A backup resolved against the local library: only local track ids, unmatched tracks already dropped and counted. */
public record RestorePlan(Map<String, Object> settings, Map<String, Long> likes, List<Playlist> playlists,
        Map<String, Stat> plays, int unmatched) {

    public record Playlist(String name, @Nullable String description, long createdAt, List<String> trackIds) { }

    public record Stat(int count, long lastPlayedAt) { }

    public static RestorePlan of(BackupData data, TrackMatcher matcher) {
        Set<TrackRef> missing = new HashSet<>();
        Map<String, Long> likes = new LinkedHashMap<>();
        for (BackupData.Like l : data.likes()) {
            String id = resolve(matcher, l.track(), missing);
            if (id != null) likes.merge(id, l.likedAt(), Math::max);
        }
        List<Playlist> playlists = new ArrayList<>();
        for (BackupData.PlaylistEntry p : data.playlists()) {
            List<String> ids = new ArrayList<>();
            for (TrackRef t : p.tracks()) {
                String id = resolve(matcher, t, missing);
                if (id != null && !ids.contains(id)) ids.add(id);
            }
            playlists.add(new Playlist(p.name(), p.description(), p.createdAt(), ids));
        }
        Map<String, Stat> plays = new LinkedHashMap<>();
        for (BackupData.Play p : data.plays()) {
            String id = resolve(matcher, p.track(), missing);
            if (id != null) plays.merge(id, new Stat(p.count(), p.lastPlayedAt()),
                    (a, b) -> new Stat(Math.max(a.count(), b.count()), Math.max(a.lastPlayedAt(), b.lastPlayedAt())));
        }
        return new RestorePlan(data.settings(), likes, playlists, plays, missing.size());
    }

    public BackupSummary summary() {
        return new BackupSummary(playlists.size(), likes.size(), plays.size(), settings.size(), unmatched,
                BackupSummary.Error.NONE);
    }

    @Nullable
    private static String resolve(TrackMatcher matcher, TrackRef ref, Set<TrackRef> missing) {
        String id = matcher.match(ref);
        if (id == null) missing.add(ref);
        return id;
    }
}
