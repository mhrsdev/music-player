package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.LyricWord;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

/** Word-level sync: enhanced LRC / A2 / inline brackets / Walaoke, TTML, JSON, SYLT words, dispatcher. */
public class WordSyncParsersTest {

    private static LyricLine only(String lrc) {
        ParsedLrc p = LrcParser.parse(lrc);
        assertNotNull(p);
        return p.lines().get(0);
    }

    private static void word(LyricWord w, long s, long e, String text) {
        assertEquals(text, w.text());
        assertEquals(s, w.startMs());
        assertEquals(e, w.endMs());
    }

    @Test public void enhancedLrcWithEndTags() {
        LyricLine l = only("[00:12.00]<00:12.00>Hel<00:12.30>lo <00:12.80>world<00:13.40>");
        assertEquals("Hello world", l.text());
        List<LyricWord> w = l.words();
        assertEquals(3, w.size());
        word(w.get(0), 12_000, 12_300, "Hel");
        word(w.get(1), 12_300, 12_800, "lo");
        word(w.get(2), 12_800, 13_400, "world");
        assertEquals(6, w.get(2).start());
        assertEquals("world", l.text().substring(w.get(2).start(), w.get(2).end()));
        assertFalse(l.estimated());
    }

    @Test public void a2FirstWordWithoutTagStartsAtLineTime_lastEndsAtNextLine() {
        ParsedLrc p = LrcParser.parse("[00:01.00]Hello <00:01.50>there\n[00:02.20]next");
        LyricLine l = p.lines().get(0);
        word(l.words().get(0), 1_000, 1_500, "Hello");
        word(l.words().get(1), 1_500, 2_200, "there");
        assertNull(p.lines().get(1).words());
    }

    @Test public void openEndIsCapped() {
        ParsedLrc p = LrcParser.parse("[00:01.00]<00:01.00>la\n[01:00.00]next");
        assertEquals(1_000 + LrcParser.OPEN_END_MS, p.lines().get(0).words().get(0).endMs());
    }

    @Test public void inlineBracketVariantAndWalaoke() {
        LyricLine l = only("[00:05.00]F: Hello [00:05.60]world [00:06.10]");
        assertEquals("Hello world", l.text());
        word(l.words().get(0), 5_000, 5_600, "Hello");
        word(l.words().get(1), 5_600, 6_100, "world");
    }

    @Test public void persianEnhancedLine() {
        LyricLine l = only("[00:10.00]<00:10.00>دل <00:10.40>من <00:10.90>دریاست<00:11.80>");
        assertEquals("دل من دریاست", l.text());
        assertEquals(3, l.words().size());
        assertEquals("دریاست", l.text().substring(l.words().get(2).start(), l.words().get(2).end()));
    }

    @Test public void offsetShiftsWordsToo() {
        ParsedLrc p = LrcParser.parse("[offset:500]\n[00:02.00]<00:02.00>a <00:02.50>b<00:03.00>");
        LyricLine l = p.lines().get(0);
        assertEquals(1_500, l.timeMs());
        word(l.words().get(0), 1_500, 2_000, "a");
    }

    @Test public void heartIsNotATag() {
        LyricLine l = only("[00:01.00]I <3 you");
        assertEquals("I <3 you", l.text());
        assertNull(l.words());
    }

    @Test public void ttmlAppleStyle() {
        String ttml = "<?xml version=\"1.0\"?><tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div>"
                + "<p begin=\"00:01.000\" end=\"00:03.000\"><span begin=\"00:01.000\" end=\"00:01.500\">Rock</span> "
                + "<span begin=\"1.5s\" end=\"2500ms\">&amp; roll</span></p>"
                + "<p begin=\"0:00:04.2\" end=\"0:00:05\"><span begin=\"4.2\" end=\"4.6\">آ</span><span begin=\"4.6\" end=\"5\">واز</span></p>"
                + "</div></body></tt>";
        Lyrics l = LyricsParser.toLyrics("t", ttml, "TTML file");
        assertNotNull(l);
        assertTrue(l.synced());
        LyricLine a = l.lines().get(0);
        assertEquals("Rock & roll", a.text());
        word(a.words().get(0), 1_000, 1_500, "Rock");
        word(a.words().get(1), 1_500, 2_500, "& roll");
        LyricLine b = l.lines().get(1);
        assertEquals("آواز", b.text());
        assertEquals(4_200, b.timeMs());
        assertEquals(2, b.words().size());
        assertFalse(b.estimated());
    }

    @Test public void jsonSidecar() {
        String json = "{\"lines\":[{\"time\":1000,\"text\":\"hi there\",\"words\":[{\"start\":1000,\"end\":1300,\"text\":\"hi\"},"
                + "{\"start\":1300,\"end\":1900,\"text\":\"there\"}]},{\"words\":[{\"startMs\":2000,\"endMs\":2500,\"text\":\"سلام\"}]}]}";
        Lyrics l = LyricsParser.toLyrics("t", json, "LRC file");
        assertEquals(2, l.lines().size());
        word(l.lines().get(0).words().get(1), 1_300, 1_900, "there");
        assertEquals(3, l.lines().get(0).words().get(1).start());
        assertEquals("سلام", l.lines().get(1).text());
        assertEquals(2_000, l.lines().get(1).timeMs());
    }

    @Test public void lineSyncedLrcGetsEstimatedWords() {
        Lyrics l = LyricsParser.toLyrics("t", "[00:01.00]one two three\n[00:04.00]four", "LRCLIB");
        LyricLine a = l.lines().get(0);
        assertTrue(a.estimated());
        assertEquals(3, a.words().size());
        assertEquals(1_000, a.words().get(0).startMs());
        assertTrue(a.wordsEndMs() <= 4_000);
    }

    @Test public void plainStaysPlain() {
        Lyrics l = LyricsParser.toLyrics("t", "just text\nmore", null);
        assertFalse(l.synced());
        assertNull(l.lines().get(0).words());
    }

    @Test public void lrclibEnhancedSyncedLyricsKeepRealTimings() {
        Lyrics l = LyricsParser.toLyrics("t", "[00:01.00]<00:01.00>a <00:01.20>b<00:01.40>", "LRCLIB");
        assertFalse(l.lines().get(0).estimated());
        assertEquals(1_200, l.lines().get(0).words().get(1).startMs());
    }

    @Test public void syltWordEntriesBecomeEnhancedLrc() {
        // encoding 3 (UTF-8), lang "eng", format 2 (ms), type 1, empty descriptor; entries "\nHel"@1000 "lo"@1300 "\nNext"@2000
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        b.write(3);
        b.writeBytes("eng".getBytes(StandardCharsets.ISO_8859_1));
        b.write(2);
        b.write(1);
        b.write(0);
        entry(b, "\nHel", 1000);
        entry(b, "lo", 1300);
        entry(b, "\nNext", 2000);
        String lrc = Id3LyricsReader.sylt(b.toByteArray(), 0);
        assertNotNull(lrc);
        LyricLine l = LrcParser.parse(lrc).lines().get(0);
        assertEquals("Hello", l.text());
        assertEquals(2, l.words().size());
        word(l.words().get(0), 1_000, 1_300, "Hel");
    }

    private static void entry(java.io.ByteArrayOutputStream b, String text, int ms) {
        b.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        b.write(0);
        b.write(ms >>> 24);
        b.write(ms >>> 16);
        b.write(ms >>> 8);
        b.write(ms);
    }
}
