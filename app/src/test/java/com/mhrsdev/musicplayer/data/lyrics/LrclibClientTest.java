package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class LrclibClientTest {

    /** Canned responses by URL path prefix ("/get", "/search"); records every URL requested. */
    private static class FakeHttp implements HttpFetcher {
        final Map<String, Object> responses = new LinkedHashMap<>(); // String body, null (=404) or IOException
        final List<String> urls = new ArrayList<>();

        @Nullable @Override public String get(String url, CancelToken cancel) throws IOException {
            urls.add(url);
            for (Map.Entry<String, Object> e : responses.entrySet()) {
                if (url.startsWith("https://x/api" + e.getKey())) {
                    if (e.getValue() instanceof IOException) throw (IOException) e.getValue();
                    return (String) e.getValue();
                }
            }
            throw new IOException("unexpected url " + url);
        }
    }

    private final FakeHttp http = new FakeHttp();
    private final LrclibClient client = new LrclibClient(http, "https://x/api");

    @Test public void exactMatchUsesOnlyGetAndSendsAllFourFields() throws IOException {
        http.responses.put("/get", Resources.read("lrclib/get-found.json"));
        LyricsSource.Found f = client.load(TestTracks.queen(), new CancelToken());
        assertTrue(f.text().startsWith("[00:39.64]"));
        assertEquals("LRCLIB", f.source());
        assertEquals(1, http.urls.size());
        assertEquals("https://x/api/get?track_name=I+Want+To+Break+Free&artist_name=Queen&album_name=The+Works&duration=261",
                http.urls.get(0));
    }

    @Test public void notFoundOnGetFallsBackToSearch() throws IOException {
        http.responses.put("/get", null);
        http.responses.put("/search", Resources.read("lrclib/search-queen.json"));
        LyricsSource.Found f = client.load(TestTracks.queen(), new CancelToken());
        assertTrue(f.text().contains("break free"));
        assertEquals(2, http.urls.size());
        // tags are already clean, so the cleaned /get is skipped: raw /get, then the title search
        assertEquals("https://x/api/search?q=I+Want+To+Break+Free", http.urls.get(1));
    }

    @Test public void getResultWithWrongDurationFallsBackToSearch() throws IOException {
        http.responses.put("/get", "{\"duration\":300,\"syncedLyrics\":\"[00:01.00]wrong song\"}");
        http.responses.put("/search", "[{\"duration\":261,\"trackName\":\"I Want To Break Free\",\"artistName\":\"Queen\","
                + "\"syncedLyrics\":\"[00:01.00]right\"}]");
        assertEquals("[00:01.00]right", client.load(TestTracks.queen(), new CancelToken()).text());
    }

    @Test public void instrumentalTrackStopsAfterGet() throws IOException {
        http.responses.put("/get", "{\"duration\":261,\"instrumental\":true,\"plainLyrics\":null,\"syncedLyrics\":null}");
        assertNull(client.load(TestTracks.queen(), new CancelToken()));
        assertEquals(1, http.urls.size());
    }

    @Test public void missingAlbumOrUnknownArtistGoesStraightToSearch() throws IOException {
        http.responses.put("/search", "[]");
        Track noAlbum = TestTracks.track("t", "Song", "Queen", "", 200_000, null);
        Track unknownArtist = TestTracks.track("t", "Song", "<unknown>", "Album", 200_000, null);
        assertNull(client.load(noAlbum, new CancelToken()));
        assertNull(client.load(unknownArtist, new CancelToken()));
        // no album: the cleaned /get without album (fails here), then the search; unknown artist: search only
        assertEquals("https://x/api/get?track_name=Song&artist_name=Queen&duration=200", http.urls.get(0));
        assertEquals("https://x/api/search?q=Song", http.urls.get(1));
        assertEquals("https://x/api/search?q=Song", http.urls.get(2));
        assertEquals(3, http.urls.size());
    }

    @Test public void persianMetadataIsPercentEncodedAsUtf8() throws IOException {
        http.responses.put("/search", "[]");
        client.load(TestTracks.track("t", "سلام", "سهراب", "", 0, null), new CancelToken());
        assertEquals("https://x/api/search?q=%D8%B3%D9%84%D8%A7%D9%85", http.urls.get(0));
    }

    @Test public void blankTitleMakesNoRequest() throws IOException {
        assertNull(client.load(TestTracks.track("t", "  ", "Queen", "x", 1000, null), new CancelToken()));
        assertTrue(http.urls.isEmpty());
    }

    @Test public void cancelledBetweenGetAndSearchSkipsSearch() throws IOException {
        CancelToken token = new CancelToken();
        http.responses.put("/get", null);
        http.responses.put("/search", "[]");
        HttpFetcher cancelling = (url, c) -> {
            token.cancel();
            return null;
        };
        assertNull(new LrclibClient(cancelling, "https://x/api").load(TestTracks.queen(), token));
    }

    @Test public void failedGetStillTriesSearch() throws IOException {
        http.responses.put("/get", new IOException("HTTP 503"));
        http.responses.put("/search", "[{\"duration\":261,\"trackName\":\"I want to break free\",\"artistName\":\"Queen\","
                + "\"syncedLyrics\":\"[00:01.00]via search\"}]");
        assertEquals("[00:01.00]via search", client.load(TestTracks.queen(), new CancelToken()).text());
        http.responses.put("/get", "not json");
        assertEquals("[00:01.00]via search", client.load(TestTracks.queen(), new CancelToken()).text());
    }

    @Test public void watermarkedTagsAreCleanedAndAtMostThreeRequestsAreMade() throws IOException {
        http.responses.put("/get", null);
        http.responses.put("/search", "[{\"duration\":201,\"trackName\":\"Ghalbam\",\"artistName\":\"Reza Shiri\","
                + "\"syncedLyrics\":\"[00:01.00]line\"},{\"duration\":201,\"trackName\":\"Other song\",\"artistName\":\"X\","
                + "\"syncedLyrics\":\"[00:01.00]wrong\"}]");
        Track t = TestTracks.track("t", "Ghalbam", "Reza Shiri @RozMusic.com", "Ghalbam - Single", 200_000, null);
        assertEquals("[00:01.00]line", client.load(t, new CancelToken()).text());
        assertEquals(3, http.urls.size());
        assertTrue(http.urls.get(0).contains("artist_name=Reza+Shiri+%40RozMusic.com"));
        assertEquals("https://x/api/get?track_name=Ghalbam&artist_name=Reza+Shiri&album_name=Ghalbam+-+Single&duration=200",
                http.urls.get(1));
        assertEquals("https://x/api/search?q=Ghalbam", http.urls.get(2));
    }

    @Test public void fuzzySearchRejectsUnrelatedOrWrongDuration() throws IOException {
        http.responses.put("/get", null);
        http.responses.put("/search", "[{\"duration\":261,\"trackName\":\"Bohemian Rhapsody\",\"artistName\":\"Queen\","
                + "\"syncedLyrics\":\"[00:01.00]no\"},{\"duration\":300,\"trackName\":\"I Want To Break Free\","
                + "\"artistName\":\"Queen\",\"syncedLyrics\":\"[00:01.00]no\"}]");
        assertNull(client.load(TestTracks.queen(), new CancelToken()));
    }

    @Test public void searchFailureAndBadJsonPropagateAsIoException() {
        http.responses.put("/get", null);
        http.responses.put("/search", new IOException("HTTP 503"));
        try {
            client.load(TestTracks.queen(), new CancelToken());
            fail();
        } catch (IOException expected) { }
        http.responses.put("/search", "not json");
        try {
            client.load(TestTracks.queen(), new CancelToken());
            fail();
        } catch (IOException expected) { }
    }
}
