package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.mhrsdev.musicplayer.data.lyrics.LrclibJson.Candidate;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class LrclibJsonTest {

    @Test public void mapsRealGetResponse() throws IOException {
        Candidate c = LrclibJson.parseObject(Resources.read("lrclib/get-found.json"));
        assertEquals(261.0, c.durationSec(), 0.001);
        assertFalse(c.instrumental());
        assertTrue(c.synced().startsWith("[00:39.64]"));
        assertTrue(c.plain().startsWith("I want to break free"));
        LyricsSource.Found found = LrclibJson.toFound(c);
        assertEquals("LRCLIB", found.source());
        assertEquals(c.synced(), found.text()); // synced preferred
    }

    @Test public void mapsRealSearchResponse() throws IOException {
        List<Candidate> all = LrclibJson.parseArray(Resources.read("lrclib/search-queen.json"));
        assertEquals(20, all.size());
        // track is 261 s: 263 (id 37486877), 262 (id 37538972), 261 (id 37414201), 259, 258 ... all within 3 s;
        // the exact 261 s match must win over the 262/263/259 ones.
        Candidate best = LrclibJson.pickBest(all, 261);
        assertEquals(261.0, best.durationSec(), 0.001);
        assertEquals(262.0, LrclibJson.pickBest(all, 262).durationSec(), 0.001);
    }

    @Test public void searchRespectsDurationToleranceBothWays() throws IOException {
        List<Candidate> all = LrclibJson.parseArray(Resources.read("lrclib/search-queen.json"));
        assertNull(LrclibJson.pickBest(all, 100)); // nothing near 100 s -> better no lyrics than wrong lyrics
        assertEquals(206.0, LrclibJson.pickBest(all, 208).durationSec(), 0.001);
        assertNull(LrclibJson.pickBest(all, 210)); // 3.0 s is the limit; 4 s away is rejected
    }

    @Test public void unknownTrackDurationAcceptsFirstSyncedCandidate() throws IOException {
        List<Candidate> all = LrclibJson.parseArray(Resources.read("lrclib/search-queen.json"));
        assertTrue(LrclibJson.pickBest(all, 0) != null);
    }

    @Test public void syncedBeatsPlainWithinToleranceEvenIfPlainIsCloser() throws IOException {
        List<Candidate> c = LrclibJson.parseArray("[{\"duration\":200,\"plainLyrics\":\"p\",\"syncedLyrics\":null},"
                + "{\"duration\":202.5,\"plainLyrics\":\"p\",\"syncedLyrics\":\"[00:01.00]s\"}]");
        assertEquals("[00:01.00]s", LrclibJson.pickBest(c, 200).synced());
    }

    @Test public void plainIsUsedWhenNoSyncedExists() throws IOException {
        List<Candidate> c = LrclibJson.parseArray("[{\"duration\":200,\"plainLyrics\":\"just text\",\"syncedLyrics\":\"\"}]");
        LyricsSource.Found f = LrclibJson.toFound(LrclibJson.pickBest(c, 200));
        assertEquals("just text", f.text());
    }

    @Test public void instrumentalAndEmptyEntriesAreSkipped() throws IOException {
        List<Candidate> c = LrclibJson.parseArray("[{\"duration\":200,\"instrumental\":true,\"plainLyrics\":null,\"syncedLyrics\":null},"
                + "{\"duration\":200,\"plainLyrics\":\"   \",\"syncedLyrics\":null}]");
        assertNull(LrclibJson.pickBest(c, 200));
        assertTrue(c.get(0).instrumental());
    }

    @Test public void jsonNullIsNotTheStringNull() throws IOException {
        Candidate c = LrclibJson.parseObject("{\"duration\":1,\"plainLyrics\":null,\"syncedLyrics\":null}");
        assertNull(c.plain());
        assertNull(c.synced());
        assertNull(LrclibJson.toFound(c));
    }

    @Test public void persianLyricsSurviveJson() throws IOException {
        Candidate c = LrclibJson.parseObject("{\"duration\":180.4,\"syncedLyrics\":\"[00:01.00] سلام\\n[00:02.00] دنیا\"}");
        assertEquals("[00:01.00] سلام\n[00:02.00] دنیا", c.synced());
    }

    @Test public void errorBodiesAndGarbageAreIoExceptionsOrEmpty() throws IOException {
        // the 404 body is a JSON object without lyrics: no usable candidate
        Candidate nf = LrclibJson.parseObject(Resources.read("lrclib/get-not-found.json"));
        assertFalse(nf.hasLyrics());
        try {
            LrclibJson.parseArray(Resources.read("lrclib/error-503.json")); // object where an array is expected
            fail();
        } catch (IOException expected) { }
        try {
            LrclibJson.parseObject("<html>");
            fail();
        } catch (IOException expected) { }
    }
}
