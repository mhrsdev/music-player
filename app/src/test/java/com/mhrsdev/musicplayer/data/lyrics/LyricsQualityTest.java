package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

/** Junk/watermark detection, metadata normalisation, and that junk never stops the resolver. */
public class LyricsQualityTest {

    private static Lyrics parse(String text) {
        return LyricsParser.toLyrics("t", text, "Embedded");
    }

    private static boolean junk(String text, String artist, String title) {
        return LyricsJunkFilter.clean(parse(text), artist, title) == null;
    }

    /** Real-world junk seen in tags of downloaded MP3s. */
    private static final String[] JUNK = {
        "RozMusic.com",
        "Downloaded from www.melovaz.ir",
        "Telegram: @rozmusic_channel",
        "@Nex1Music",
        "www.Ahangify.com",
        "https://radiojavan.com/mp3s/mp3/Reza-Shiri-Ghalbam",
        "دانلود از سایت روزموزیک",
        "کانال تلگرام ما: @musicfa",
        "Nex1Music.ir\nNex1Music.ir",
        "MusicFa.Com\n",
        "1Music.ir - دانلود آهنگ جدید",
        "Instagram: melovaz_official",
        "Downloaded From UpMusics",
        "Visit us at mp3pm.net",
        "Uploaded by DJ Punjab",
        "Ripped by MusicDel",
        "Ghalbam",
        "Reza Shiri - Ghalbam",
        "Reza Shiri",
        "...",
        "123 456",
        "قلبم",
        "[00:00.00]RozMusic.com",
        "[00:00.00]www.nex1music.ir\n[00:05.00]Nex1Music",
        "la la la\nla la la\nla la la",
        "لینک دانلود مستقیم",
        "Subscribe to our YouTube channel",
        "Follow us on Instagram @ganja2music",
        "Exclusive on Bia2Music",
        "  \n  \n",
        "Free MP3 download - Song name",
    };

    /** Short, real lyrics (no watermark): must pass. */
    private static final String[] VALID = {
        "باران که می‌بارد\nدلم یادت می‌افتد",
        "Roses are red\nViolets are blue",
        "[00:01.00]Hello darkness my old friend\n[00:05.00]I've come to talk with you again",
        "I am the one\nWho will download my heart to you\nAnd keep it there\nForever",
        "شب است و سکوت است",
        "Na na na, hey hey\nGoodbye",
    };

    @Test public void junkTableIsRejected() {
        for (String s : JUNK) {
            Lyrics l = parse(s);
            assertTrue("should be junk: " + s, l == null || LyricsJunkFilter.clean(l, "Reza Shiri @RozMusic.com",
                    s.contains("قلبم") ? "قلبم" : "Ghalbam") == null);
        }
        assertTrue(JUNK.length + VALID.length >= 30);
    }

    @Test public void validTableIsKept() {
        for (String s : VALID) assertFalse("should be valid: " + s, junk(s, "Some Artist", "Some Title"));
    }

    @Test public void watermarkLinesAreStrippedFromRealLyrics() {
        String text = "[00:00.00]Downloaded from www.melovaz.ir\n[00:01.00]First line of the song\n"
                + "[00:04.00]Second line here\n[00:08.00]Third line now\n[00:12.00]Telegram: @melovaz";
        Lyrics l = LyricsJunkFilter.clean(parse(text), "A", "B");
        assertNotNull(l);
        assertEquals(3, l.lines().size());
        assertEquals("First line of the song", l.lines().get(0).text());
        assertTrue(l.synced());
    }

    @Test public void creditHeadersAreStripped() {
        String text = "شعر: فلانی\nآهنگساز: بهمان\n"
                + "Lyrics by John\nline one\nline two\nline three\nline four\nline five";
        Lyrics l = LyricsJunkFilter.clean(parse(text), "A", "B");
        assertEquals("line one", l.lines().get(0).text());
        assertEquals(5, l.lines().size());
    }

    @Test public void longLineMentioningAWordIsNotAWatermark() {
        assertFalse(LyricsJunkFilter.isWatermark(
                "I keep on dreaming of the day you said you'd visit me again and stay a while longer, my love, my dear one"));
        assertTrue(LyricsJunkFilter.isWatermark("RozMusic.com"));
        assertTrue(LyricsJunkFilter.isWatermark("Nex1 Music"));
    }

    @Test public void normaliser() {
        assertEquals("Reza Shiri", LyricsQueryNormalizer.artist("Reza Shiri @RozMusic.com"));
        assertEquals("Ghalbam", LyricsQueryNormalizer.title("Ghalbam"));
        assertEquals("Ghalbam", LyricsQueryNormalizer.title("Ghalbam - RozMusic"));
        assertEquals("Ghalbam", LyricsQueryNormalizer.title("01 - Ghalbam (Official Video) [Free Download]"));
        assertEquals("Song", LyricsQueryNormalizer.title("Song feat. Someone"));
        assertEquals("Song", LyricsQueryNormalizer.title("Song ft Someone"));
        assertEquals("Dance with me", LyricsQueryNormalizer.title("Dance with me"));
        assertEquals("Artist", LyricsQueryNormalizer.artist("Artist ft. Other"));
        assertEquals("Artist", LyricsQueryNormalizer.artist("Artist x Other"));
        assertEquals("My Song", LyricsQueryNormalizer.title("My_Song   www.site.ir"));
        assertEquals("علی یاسینی", LyricsQueryNormalizer.artist("علي ياسيني"));
        assertEquals("کوچه", LyricsQueryNormalizer.title("كوچه"));
        assertEquals("می دهد", LyricsQueryNormalizer.title("می‌دهد"));
        assertEquals("", LyricsQueryNormalizer.title("<unknown>"));
        assertEquals("", LyricsQueryNormalizer.title("@RozMusic.com"));
        assertEquals("(Intro)", LyricsQueryNormalizer.title("(Intro)"));
        assertEquals("Song", LyricsQueryNormalizer.title("Song | Nex1Music"));
    }

    @Test public void similarity() {
        assertEquals(1.0, LyricsMatch.score("Ghalbam", "Reza Shiri", "Ghalbam", "Reza Shiri"), 1e-9);
        assertTrue(LyricsMatch.score("I Want To Break Free", "Queen", "I Want to Break Free", "Queen") > 0.95);
        assertEquals(0, LyricsMatch.score("Ghalbam", "Reza Shiri", "Bohemian Rhapsody", "Queen"), 1e-9);
        assertTrue(LyricsMatch.score("می دهد", "", "می‌دهد", null) > 0.95);
    }

    // ------------------------------------------------------------------ resolver

    private static final class Src implements LyricsSource {
        @Nullable String text;
        int calls;
        final String label;
        Src(String label) { this.label = label; }
        @Nullable @Override public Found load(Track track, CancelToken cancel) throws IOException {
            calls++;
            return text == null ? null : new Found(text, label);
        }
    }

    @Test public void junkEmbeddedFallsThroughToOnline() {
        Src embedded = new Src("Embedded"), sidecar = new Src("LRC file"), remote = new Src("LRCLIB");
        InMemoryLyricsCacheStore cache = new InMemoryLyricsCacheStore();
        Track t = TestTracks.track("t9", "Ghalbam", "Reza Shiri @RozMusic.com", "Ghalbam", 200_000, null);
        LyricsResolver r = new LyricsResolver(Arrays.asList(embedded, sidecar), remote, cache, () -> 1_000L);
        embedded.text = "RozMusic.com";
        remote.text = "[00:01.00]دلم گرفته\n[00:04.00]بی تو";
        Lyrics l = r.resolve(t, new CancelToken());
        assertEquals("LRCLIB", l.source());
        assertEquals(2, l.lines().size());
        assertEquals(1, remote.calls);
        // cached under the CLEANED key: another file of the same song with clean tags shares it
        Track clean = TestTracks.track("t10", "Ghalbam", "Reza Shiri", "Other", 200_500, null);
        assertEquals(LyricsResolver.cacheKey(t), LyricsResolver.cacheKey(clean));
        r.resolve(clean, new CancelToken());
        assertEquals(1, remote.calls);
    }

    @Test public void junkOnlineIsCachedAsNotFound_searchAgainAsksAgain() {
        Src remote = new Src("LRCLIB");
        InMemoryLyricsCacheStore cache = new InMemoryLyricsCacheStore();
        Track t = TestTracks.track("t9", "Ghalbam", "Reza Shiri", "", 200_000, null);
        LyricsResolver r = new LyricsResolver(Arrays.asList(), remote, cache, () -> 1_000L);
        remote.text = "Downloaded from www.melovaz.ir";
        assertTrue(r.resolve(t, new CancelToken()).isEmpty());
        assertNull(cache.get(LyricsResolver.cacheKey(t)).text());
        r.resolve(t, new CancelToken());
        assertEquals(1, remote.calls); // negative cache
        r.resolve(t, new CancelToken(), true);
        assertEquals(2, remote.calls); // "Search again" ignores it
    }

    @Test public void onlineErrorIsReportedAsFailed() {
        LyricsSource failing = (track, cancel) -> { throw new IOException("timeout"); };
        LyricsResolver r = new LyricsResolver(Arrays.asList(), failing, new InMemoryLyricsCacheStore(), () -> 1_000L);
        Lyrics l = r.resolve(TestTracks.queen(), new CancelToken());
        assertTrue(l.isEmpty());
        assertTrue(l.failed());
    }

    @Test public void emptyCleanedTitleNeverAsksOnline() {
        Src remote = new Src("LRCLIB");
        LyricsResolver r = new LyricsResolver(Arrays.asList(), remote, new InMemoryLyricsCacheStore(), () -> 1_000L);
        r.resolve(TestTracks.track("t", "@RozMusic.com", "A", "", 1000, null), new CancelToken());
        assertEquals(0, remote.calls);
    }
}
