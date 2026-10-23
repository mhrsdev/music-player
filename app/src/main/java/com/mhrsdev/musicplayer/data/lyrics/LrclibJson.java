package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Maps LRCLIB JSON ({@code /api/get} object, {@code /api/search} array) and picks the best match for a track. */
final class LrclibJson {
    /** Largest accepted difference between the track's duration and a result's, in seconds. */
    static final double DURATION_TOLERANCE_SEC = 3.0;

    /** One LRCLIB record; {@code synced}/{@code plain} are null when absent or blank. */
    record Candidate(double durationSec, boolean instrumental, @Nullable String synced, @Nullable String plain) {

        boolean hasLyrics() {
            return synced != null || plain != null;
        }

        /** Unknown track duration (<= 0) accepts everything. */
        boolean durationMatches(int trackSec) {
            return trackSec <= 0 || (durationSec > 0 && Math.abs(durationSec - trackSec) <= DURATION_TOLERANCE_SEC);
        }
    }

    private LrclibJson() { }

    /** @throws IOException when the body is not the expected JSON */
    static Candidate parseObject(String json) throws IOException {
        try {
            return candidate(new JSONObject(json));
        } catch (JSONException e) {
            throw new IOException("bad LRCLIB response", e);
        }
    }

    static List<Candidate> parseArray(String json) throws IOException {
        try {
            JSONArray array = new JSONArray(json);
            List<Candidate> out = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) out.add(candidate(array.getJSONObject(i)));
            return out;
        } catch (JSONException e) {
            throw new IOException("bad LRCLIB response", e);
        }
    }

    /** Synced lyrics first, then closest duration; null when nothing has lyrics within the tolerance. */
    @Nullable
    static Candidate pickBest(List<Candidate> candidates, int trackSec) {
        Candidate best = null;
        for (Candidate c : candidates) {
            if (c.instrumental() || !c.hasLyrics() || !c.durationMatches(trackSec)) continue;
            if (best == null || better(c, best, trackSec)) best = c;
        }
        return best;
    }

    private static boolean better(Candidate a, Candidate b, int trackSec) {
        if ((a.synced() != null) != (b.synced() != null)) return a.synced() != null;
        return Math.abs(a.durationSec() - trackSec) < Math.abs(b.durationSec() - trackSec);
    }

    /** The lyrics text of a candidate (synced preferred), or null. */
    @Nullable
    static LyricsSource.Found toFound(@Nullable Candidate c) {
        if (c == null || !c.hasLyrics()) return null;
        return new LyricsSource.Found(c.synced() != null ? c.synced() : c.plain(), "LRCLIB");
    }

    private static Candidate candidate(JSONObject o) {
        return new Candidate(o.optDouble("duration", -1), o.optBoolean("instrumental", false),
                text(o, "syncedLyrics"), text(o, "plainLyrics"));
    }

    @Nullable
    private static String text(JSONObject o, String key) {
        if (o.isNull(key)) return null; // optString would turn JSON null into the string "null"
        String s = o.optString(key, "");
        return s.trim().isEmpty() ? null : s;
    }
}
