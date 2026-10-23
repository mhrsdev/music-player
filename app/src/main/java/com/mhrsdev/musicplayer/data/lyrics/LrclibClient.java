package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.lyrics.LrclibJson.Candidate;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * LRCLIB (https://lrclib.net) lookup: exact {@code /api/get} first (needs title, artist, album and duration), then
 * {@code /api/search}; synced lyrics are preferred and the result must match the track's duration within 3 s.
 * Sends only title / artist / album / duration. At most two requests per call; a failed /get falls through to /search.
 */
public final class LrclibClient implements LyricsSource {
    public static final String DEFAULT_BASE_URL = "https://lrclib.net/api";
    private final HttpFetcher http;
    private final String baseUrl;

    public LrclibClient(HttpFetcher http) {
        this(http, DEFAULT_BASE_URL);
    }

    LrclibClient(HttpFetcher http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    @Nullable
    @Override public Found load(Track track, CancelToken cancel) throws IOException {
        String title = track.title();
        if (title == null || title.trim().isEmpty()) return null;
        String artist = known(track.artistName());
        String album = known(track.albumTitle());
        int sec = (int) Math.round(track.durationMs() / 1000.0);

        if (artist != null && album != null && sec > 0) {
            try {
                String body = http.get(baseUrl + "/get?" + param("track_name", title) + "&" + param("artist_name", artist)
                        + "&" + param("album_name", album) + "&duration=" + sec, cancel);
                Candidate exact = body == null ? null : LrclibJson.parseObject(body);
                if (exact != null && exact.instrumental()) return null;
                if (exact != null && exact.durationMatches(sec)) {
                    Found found = LrclibJson.toFound(exact);
                    if (found != null) return found;
                }
            } catch (IOException e) {
                // /get is the busier endpoint (503s are common); /search may still answer, so only fail if it fails too
            }
        }
        if (cancel.isCancelled()) return null;
        String query = "/search?" + param("track_name", title) + (artist == null ? "" : "&" + param("artist_name", artist));
        String body = http.get(baseUrl + query, cancel);
        return body == null ? null : LrclibJson.toFound(LrclibJson.pickBest(LrclibJson.parseArray(body), sec));
    }

    /** MediaStore reports missing tags as "&lt;unknown&gt;". */
    @Nullable
    private static String known(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() || t.toLowerCase(Locale.ROOT).equals("<unknown>") ? null : t;
    }

    private static String param(String key, String value) {
        try {
            return key + "=" + URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always supported
        }
    }
}
