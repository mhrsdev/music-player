package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.LyricLine;
import java.util.List;
import org.junit.Test;

public class LrcParserTest {

    private static ParsedLrc parse(String s) {
        ParsedLrc p = LrcParser.parse(s);
        assertTrue("expected lyrics", p != null);
        return p;
    }

    private static void assertLine(LyricLine l, long timeMs, String text) {
        assertEquals(timeMs, l.timeMs());
        assertEquals(text, l.text());
    }

    @Test public void basicSyncedLines() {
        ParsedLrc p = parse("[00:12.00]Hello\n[00:17.20]World\n");
        assertTrue(p.synced());
        assertEquals(2, p.lines().size());
        assertLine(p.lines().get(0), 12_000, "Hello");
        assertLine(p.lines().get(1), 17_200, "World");
    }

    @Test public void timestampFormats() {
        assertEquals(62_000L, (long) LrcParser.parseTimestamp("01:02")); // mm:ss
        assertEquals(62_500L, (long) LrcParser.parseTimestamp("01:02.5")); // one fraction digit = tenths
        assertEquals(62_500L, (long) LrcParser.parseTimestamp("01:02.50"));
        assertEquals(62_050L, (long) LrcParser.parseTimestamp("01:02.05")); // "05" = 50 ms
        assertEquals(62_123L, (long) LrcParser.parseTimestamp("01:02.123"));
        assertEquals(62_070L, (long) LrcParser.parseTimestamp("01:02:07")); // mm:ss:xx
        assertEquals(62_070L, (long) LrcParser.parseTimestamp("01:02,07"));
        assertEquals(3_662_000L, (long) LrcParser.parseTimestamp("1:01:02.00")); // h:mm:ss.xx
        assertNull(LrcParser.parseTimestamp("00:125")); // 3-digit seconds are not a timestamp
    }

    @Test public void notTimestamps() {
        assertNull(LrcParser.parseTimestamp("ti:Song"));
        assertNull(LrcParser.parseTimestamp("00:xx"));
        assertNull(LrcParser.parseTimestamp("Chorus"));
        assertNull(LrcParser.parseTimestamp("99999999999:00"));
        assertNull(LrcParser.parseTimestamp(""));
    }

    @Test public void multipleTimestampsPerLineExpandAndSort() {
        ParsedLrc p = parse("[00:30.00][01:10.00]Chorus\n[00:10.00]Verse");
        assertEquals(3, p.lines().size());
        assertLine(p.lines().get(0), 10_000, "Verse");
        assertLine(p.lines().get(1), 30_000, "Chorus");
        assertLine(p.lines().get(2), 70_000, "Chorus");
    }

    @Test public void unsortedInputIsSortedAndStableForEqualTimes() {
        ParsedLrc p = parse("[00:20.00]third\n[00:05.00]first\n[00:05.00]translation\n[00:10.00]second");
        assertEquals("first", p.lines().get(0).text());
        assertEquals("translation", p.lines().get(1).text());
        assertEquals("second", p.lines().get(2).text());
        assertEquals("third", p.lines().get(3).text());
    }

    @Test public void exactDuplicatesAreDropped() {
        ParsedLrc p = parse("[00:05.00]same\n[00:05.00]same\n[00:05.00]other");
        assertEquals(2, p.lines().size());
    }

    @Test public void positiveOffsetMakesLyricsEarlier() {
        ParsedLrc p = parse("[offset:+500]\n[00:10.00]a\n[00:00.20]b");
        assertEquals(500, p.offsetMs());
        assertLine(p.lines().get(0), 0, "b"); // clamped at 0
        assertLine(p.lines().get(1), 9_500, "a");
    }

    @Test public void negativeOffsetMakesLyricsLater() {
        ParsedLrc p = parse("[offset: -1000 ]\n[00:10.00]a");
        assertLine(p.lines().get(0), 11_000, "a");
    }

    @Test public void garbageOffsetIsIgnored() {
        ParsedLrc p = parse("[offset:abc]\n[00:10.00]a");
        assertEquals(0, p.offsetMs());
        assertLine(p.lines().get(0), 10_000, "a");
    }

    @Test public void metadataTagsAreCollectedNotShown() {
        ParsedLrc p = parse("[ti:Song]\n[AR:Artist]\n[al:Album]\n[by:me]\n[00:01.00]line");
        assertEquals("Song", p.metadata().get("ti"));
        assertEquals("Artist", p.metadata().get("ar"));
        assertEquals("Album", p.metadata().get("al"));
        assertEquals("me", p.metadata().get("by"));
        assertEquals(1, p.lines().size());
    }

    @Test public void enhancedWordTimestampsAreRemoved() {
        ParsedLrc p = parse("[00:12.00]<00:12.00>Hello <00:12.50>big <00:13.00>world <00:13.50>");
        assertLine(p.lines().get(0), 12_000, "Hello big world");
    }

    @Test public void crlfAndCrLineEndings() {
        assertEquals(3, parse("[00:01.00]a\r\n[00:02.00]b\r\n[00:03.00]c\r\n").lines().size());
        assertEquals(2, parse("[00:01.00]a\r[00:02.00]b").lines().size());
    }

    @Test public void bomAndInvisibleMarksBeforeTags() {
        ParsedLrc p = parse("\uFEFF[ti:x]\n\u200F[00:01.00]\u200Fسلام");
        assertLine(p.lines().get(0), 1_000, "سلام");
        assertEquals("x", p.metadata().get("ti"));
    }

    @Test public void persianTextIsKeptIntact() {
        String fa = "دلم برات تنگ شده، ای یار مهربان \u200Cمی\u200Cروم";
        ParsedLrc p = parse("[00:05.00]" + fa);
        assertEquals(fa, p.lines().get(0).text());
    }

    @Test public void persianDigitsInTimestamps() {
        ParsedLrc p = parse("[۰۰:۱۲٫۵۰]سلام\n[٠١:٠٠.٠٠]دنیا");
        assertLine(p.lines().get(0), 12_500, "سلام");
        assertLine(p.lines().get(1), 60_000, "دنیا");
    }

    @Test public void blankLinesAreKeptAsGapsButCollapsed() {
        ParsedLrc p = parse("[00:01.00]a\n[00:05.00]\n[00:06.00]\n[00:09.00]b");
        assertEquals(3, p.lines().size());
        assertLine(p.lines().get(1), 5_000, "");
    }

    @Test public void plainTextFallbackIsUnsynced() {
        ParsedLrc p = parse("First line\nSecond line\n\n\nThird line\n");
        assertFalse(p.synced());
        assertEquals(4, p.lines().size()); // stanza break kept once
        for (LyricLine l : p.lines()) assertEquals(LyricLine.UNSYNCED, l.timeMs());
        assertEquals("", p.lines().get(2).text());
        assertEquals("Third line", p.lines().get(3).text());
    }

    @Test public void sectionHeadersInPlainTextAreNotMistakenForTags() {
        ParsedLrc p = parse("[Chorus]\nla la\n[Verse 1: Drake]\nyo");
        assertFalse(p.synced());
        assertEquals("[Chorus]", p.lines().get(0).text());
        assertEquals("[Verse 1: Drake]", p.lines().get(2).text());
    }

    @Test public void allIdenticalTimestampsMeanUnsynced() {
        ParsedLrc p = parse("[00:00.00]a\n[00:00.00]b\n[00:00.00]c");
        assertFalse(p.synced());
        assertEquals(3, p.lines().size());
        assertEquals("b", p.lines().get(1).text());
    }

    @Test public void untimedLinesInsideSyncedFileAreIgnored() {
        ParsedLrc p = parse("junk header\n[00:01.00]a\nstray\n[00:02.00]b");
        assertTrue(p.synced());
        assertEquals(2, p.lines().size());
    }

    @Test public void emptyOrTagOnlyInputIsNull() {
        assertNull(LrcParser.parse(null));
        assertNull(LrcParser.parse(""));
        assertNull(LrcParser.parse("  \n \r\n"));
        assertNull(LrcParser.parse("[ti:Only]\n[ar:Tags]\n"));
        assertNull(LrcParser.parse("[00:01.00]\n[00:02.00]  "));
    }

    @Test public void malformedTagsDoNotThrow() {
        ParsedLrc p = parse("[00:xx.00]bad\n[00:01.00 missing bracket\n[99999999999:00]huge\n[00:03.00]ok");
        assertTrue(p.synced());
        assertEquals(1, p.lines().size());
        assertLine(p.lines().get(0), 3_000, "ok");
        assertFalse(parse("[00:01.00").synced()); // unclosed tag is just text
    }

    @Test public void toLyricsCarriesSourceAndTrackId() {
        var l = LrcParser.toLyrics("tid", "[00:01.00]a", "LRCLIB");
        assertEquals("tid", l.trackId());
        assertEquals("LRCLIB", l.source());
        assertTrue(l.synced());
        assertNull(LrcParser.toLyrics("tid", "", "x"));
    }
}
