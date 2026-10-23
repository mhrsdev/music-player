package com.mhrsdev.musicplayer.data.backup;

import androidx.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Backup file format v1. Tracks are listed once in {@code tracks}; likes, playlists and plays refer to them by index,
 * so the file stays small for big libraries.
 */
public class BackupJson {
    private static final String APP = "musicplayer-backup";

    private BackupJson() { }

    public static byte[] write(BackupData data) {
        try {
            Map<TrackRef, Integer> index = new LinkedHashMap<>();
            JSONArray likes = new JSONArray();
            for (BackupData.Like l : data.likes()) likes.put(new JSONArray().put(indexOf(index, l.track())).put(l.likedAt()));
            JSONArray playlists = new JSONArray();
            for (BackupData.PlaylistEntry p : data.playlists()) {
                JSONArray members = new JSONArray();
                for (TrackRef t : p.tracks()) members.put(indexOf(index, t));
                playlists.put(new JSONObject().put("name", p.name()).put("description", p.description())
                        .put("createdAt", p.createdAt()).put("tracks", members));
            }
            JSONArray plays = new JSONArray();
            for (BackupData.Play p : data.plays()) {
                plays.put(new JSONArray().put(indexOf(index, p.track())).put(p.count()).put(p.lastPlayedAt()));
            }
            JSONArray tracks = new JSONArray();
            for (TrackRef t : index.keySet()) {
                tracks.put(new JSONObject().put("t", t.title()).put("a", t.artist()).put("b", t.album())
                        .put("d", t.durationMs()).put("p", t.path()));
            }
            JSONObject root = new JSONObject().put("app", APP).put("version", data.version())
                    .put("createdAt", data.createdAt()).put("appVersion", data.appVersion())
                    .put("settings", new JSONObject(data.settings())).put("tracks", tracks).put("likes", likes)
                    .put("playlists", playlists).put("plays", plays);
            return root.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IllegalStateException(e); // only NaN / infinite numbers, which a backup never holds
        }
    }

    /** @throws BackupFormatException for anything that is not a complete v1-or-older backup */
    public static BackupData parse(byte[] bytes) throws BackupFormatException {
        try {
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!APP.equals(root.optString("app"))) throw corrupt("not a backup file", null);
            int version = root.getInt("version");
            if (version > BackupData.FORMAT_VERSION) {
                throw new BackupFormatException("backup version " + version, true, null);
            }
            if (version < 1) throw corrupt("bad version", null);
            List<TrackRef> tracks = new ArrayList<>();
            JSONArray rawTracks = root.getJSONArray("tracks");
            for (int i = 0; i < rawTracks.length(); i++) {
                JSONObject t = rawTracks.getJSONObject(i);
                tracks.add(new TrackRef(t.getString("t"), t.getString("a"), t.getString("b"), t.getLong("d"),
                        t.isNull("p") ? null : t.getString("p")));
            }
            List<BackupData.Like> likes = new ArrayList<>();
            JSONArray rawLikes = root.getJSONArray("likes");
            for (int i = 0; i < rawLikes.length(); i++) {
                JSONArray l = rawLikes.getJSONArray(i);
                likes.add(new BackupData.Like(ref(tracks, l.getInt(0)), l.getLong(1)));
            }
            List<BackupData.PlaylistEntry> playlists = new ArrayList<>();
            JSONArray rawLists = root.getJSONArray("playlists");
            for (int i = 0; i < rawLists.length(); i++) {
                JSONObject p = rawLists.getJSONObject(i);
                List<TrackRef> members = new ArrayList<>();
                JSONArray ids = p.getJSONArray("tracks");
                for (int j = 0; j < ids.length(); j++) members.add(ref(tracks, ids.getInt(j)));
                playlists.add(new BackupData.PlaylistEntry(p.getString("name"),
                        p.isNull("description") ? null : p.optString("description"), p.getLong("createdAt"), members));
            }
            List<BackupData.Play> plays = new ArrayList<>();
            JSONArray rawPlays = root.getJSONArray("plays");
            for (int i = 0; i < rawPlays.length(); i++) {
                JSONArray p = rawPlays.getJSONArray(i);
                plays.add(new BackupData.Play(ref(tracks, p.getInt(0)), p.getInt(1), p.getLong(2)));
            }
            return new BackupData(version, root.getLong("createdAt"), root.optString("appVersion"),
                    settings(root.optJSONObject("settings")), likes, playlists, plays);
        } catch (JSONException e) {
            throw corrupt("bad backup JSON", e);
        }
    }

    private static int indexOf(Map<TrackRef, Integer> index, TrackRef t) {
        return index.computeIfAbsent(t, k -> index.size());
    }

    private static TrackRef ref(List<TrackRef> tracks, int i) throws BackupFormatException {
        if (i < 0 || i >= tracks.size()) throw corrupt("track index " + i, null);
        return tracks.get(i);
    }

    private static Map<String, Object> settings(@Nullable JSONObject raw) {
        Map<String, Object> out = new HashMap<>();
        if (raw == null) return out;
        for (Iterator<String> keys = raw.keys(); keys.hasNext();) {
            String key = keys.next();
            out.put(key, raw.opt(key));
        }
        return out;
    }

    private static BackupFormatException corrupt(String message, @Nullable Throwable cause) {
        return new BackupFormatException(message, false, cause);
    }
}
