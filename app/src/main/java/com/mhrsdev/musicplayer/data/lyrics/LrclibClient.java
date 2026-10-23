package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.lyrics.LrclibJson.Candidate;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LRCLIB (https://lrclib.net) lookup with cleaned metadata ({@link LyricsQueryNormalizer}), at most
 * {@link #MAX_REQUESTS} requests per track:
 * <ol>
 *   <li>{@code /api/get} with the tags as they are (title, artist, album, duration) when all four are known;</li>
 *   <li>{@code /api/get} with the CLEANED artist/title (and album when known), only if cleaning changed something;</li>
 *   <li>{@code /api/search?q=<cleaned title>}: a hit must match the duration within 3 s and score at least
 *       {@link LyricsMatch#ACCEPT} on title + artist similarity.</li>
 * </ol>
 * Synced lyrics are preferred. Sends only title / artist / album / duration. Never asks when the cleaned title is
 * empty. A failed /get falls through; a failed /search is reported (IOException) so the UI can offer Retry.
 */
public class LrclibClient implements LyricsSource {
    public static final String DEFAULT_BASE_URL = "https://lrclib.net/api";
    static final int MAX_REQUESTS = 3;
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
        String cTitle = LyricsQueryNormalizer.title(track.title());
        if (cTitle.isEmpty()) return null;
        String cArtist = LyricsQueryNormalizer.artist(track.artistName());
        String cAlbum = LyricsQueryNormalizer.album(track.albumTitle());
        String title = known(track.title()), artist = known(track.artistName()), album = known(track.albumTitle());
        int sec = (int) Math.round(track.durationMs() / 1000.0);
        int requests = 0;

        if (title != null && artist != null && album != null && sec > 0) {
            requests++;
            Lookup r = get(title, artist, album, sec, cancel);
            if (r.stop) return r.found;
        }
        if (cancel.isCancelled()) return null;
        boolean changed = !cTitle.equals(title) || !cArtist.equals(artist == null ? "" : artist)
                || !cAlbum.equals(album == null ? "" : album);
        if (!cArtist.isEmpty() && sec > 0 && (changed || requests == 0) && requests < MAX_REQUESTS - 1) {
            requests++;
            Lookup r = get(cTitle, cArtist, cAlbum.isEmpty() ? null : cAlbum, sec, cancel);
            if (r.stop) return r.found;
        }
        if (cancel.isCancelled()) return null;
        String body = http.get(baseUrl + "/search?" + param("q", cTitle), cancel);
        if (body == null) return null;
        return LrclibJson.toFound(LrclibJson.pickFuzzy(LrclibJson.parseArray(body), cTitle, cArtist, sec));
    }

    /**
     * Manual search ("Search manually" dialog): free text artist + title, all candidates with lyrics, best first.
     * One request.
     */
    public List<Candidate> search(String artist, String title, CancelToken cancel) throws IOException {
        String q = (LyricsQueryNormalizer.title(title) + " " + LyricsQueryNormalizer.artist(artist)).trim();
        List<Candidate> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        String body = http.get(baseUrl + "/search?" + param("q", q), cancel);
        if (body == null) return out;
        for (Candidate c : LrclibJson.parseArray(body)) if (c.hasLyrics() && !c.instrumental()) out.add(c);
        String t = LyricsQueryNormalizer.title(title), a = LyricsQueryNormalizer.artist(artist);
        out.sort((x, y) -> {
            if ((x.synced() != null) != (y.synced() != null)) return x.synced() != null ? -1 : 1;
            return Double.compare(LyricsMatch.score(t, a, y.trackName(), y.artistName()),
                    LyricsMatch.score(t, a, x.trackName(), x.artistName()));
        });
        return out;
    }

    /** Result of one /get: {@code stop} = answer final (found, or instrumental). */
    private record Lookup(boolean stop, @Nullable Found found) { }

    private Lookup get(String title, String artist, @Nullable String album, int sec, CancelToken cancel) {
        try {
            String body = http.get(baseUrl + "/get?" + param("track_name", title) + "&" + param("artist_name", artist)
                    + (album == null ? "" : "&" + param("album_name", album)) + "&duration=" + sec, cancel);
            Candidate exact = body == null ? null : LrclibJson.parseObject(body);
            if (exact != null && exact.instrumental()) return new Lookup(true, null);
            if (exact != null && exact.durationMatches(sec)) {
                Found found = LrclibJson.toFound(exact);
                if (found != null) return new Lookup(true, found);
            }
        } catch (IOException e) {
            // /get is the busier endpoint (503s are common); the next variant may still answer
        }
        return new Lookup(false, null);
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
