package com.mhrsdev.musicplayer.data.lyrics;

import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.atom;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.bytes;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.flac;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.id3;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.id3v22;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.m4a;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.syltBody;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.utf16;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.usltBody;
import static com.mhrsdev.musicplayer.data.lyrics.EmbeddedTagFixtures.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.Lyrics;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EmbeddedLyricsSourceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private LyricsSource.Found load(File f) throws IOException {
        return new EmbeddedLyricsSource(t -> f).load(TestTracks.queen(), new CancelToken());
    }

    private String text(byte[] file) throws IOException {
        LyricsSource.Found found = load(write(tmp.getRoot(), "a.bin", file));
        return found == null ? null : found.text();
    }

    @Test public void id3v23UsltUtf8() throws IOException {
        byte[] f = id3(3, "TIT2", bytes(3, "title"), "USLT", usltBody(3, "[00:01.00]سلام\nline2".getBytes(StandardCharsets.UTF_8)));
        assertEquals("[00:01.00]سلام\nline2", text(f));
        assertEquals("Embedded", load(new File(tmp.getRoot(), "a.bin")).source());
    }

    @Test public void id3v23UsltUtf16WithBomAndPersian() throws IOException {
        byte[] f = id3(3, "USLT", usltBody(1, utf16("بخش اول\nبخش دوم")));
        assertEquals("بخش اول\nبخش دوم", text(f));
    }

    @Test public void id3v23UsltLatin1() throws IOException {
        byte[] f = id3(3, "USLT", usltBody(0, "café".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("café", text(f));
    }

    @Test public void id3v24SyltBecomesLrcAndBeatsUslt() throws IOException {
        byte[] f = id3(4, "USLT", usltBody(3, "plain".getBytes(StandardCharsets.UTF_8)),
                "SYLT", syltBody("Hello", 1500, "World", 62_250));
        String lrc = text(f);
        assertEquals("[00:01.50]Hello\n[01:02.25]World\n", lrc);
        Lyrics l = LrcParser.toLyrics("t", lrc, "Embedded");
        assertTrue(l.synced());
        assertEquals(62_250, l.lines().get(1).timeMs());
    }

    @Test public void syltWordsMergeIntoLinesWhenNewlinesPresent() throws IOException {
        byte[] f = id3(4, "SYLT", syltBody("Hel", 1000, "lo ", 1200, "there\n", 1500, "\nNext", 3000));
        assertEquals("[00:01.00]Hello there\n[00:03.00]Next\n", text(f));
    }

    @Test public void id3v22Ult() throws IOException {
        assertEquals("old tag", text(id3v22("ULT", usltBody(0, "old tag".getBytes(StandardCharsets.ISO_8859_1)))));
    }

    @Test public void largeArtworkFrameIsSkippedNotLoaded() throws IOException {
        byte[] f = id3(3, "APIC", new byte[3_000_000], "USLT", usltBody(3, "after art".getBytes(StandardCharsets.UTF_8)));
        assertEquals("after art", text(f));
    }

    @Test public void id3WithoutLyricsOrGarbageIsNull() throws IOException {
        assertNull(text(id3(3, "TIT2", bytes(3, "x"))));
        assertNull(text(bytes("ID3", 9, 0, 0, 0, 0, 0, 5, new byte[20])));
        assertNull(text(new byte[5]));
        assertNull(text(new byte[0]));
    }

    @Test public void truncatedId3DoesNotThrowAnythingButIo() throws IOException {
        byte[] f = id3(3, "USLT", usltBody(3, "hello".getBytes(StandardCharsets.UTF_8)));
        byte[] cut = java.util.Arrays.copyOf(f, 18);
        try {
            text(cut);
        } catch (IOException expected) {
            // EOF while walking frames is reported as IOException, which the resolver treats as "no lyrics here"
        }
    }

    @Test public void flacVorbisCommentLyrics() throws IOException {
        assertEquals("[00:01.00]hi", text(flac("TITLE=x", "lyrics=[00:01.00]hi")));
        assertEquals("plain persian متن", text(flac("UNSYNCEDLYRICS=plain persian متن")));
        assertEquals("[00:02.00]synced", text(flac("LYRICS=plain", "SYNCEDLYRICS=[00:02.00]synced")));
        assertNull(text(flac("TITLE=x", "LYRICS=  ")));
    }

    @Test public void m4aLyricsAtom() throws IOException {
        assertEquals("[00:03.00]خط اول\nخط دوم", text(m4a("[00:03.00]خط اول\nخط دوم")));
    }

    @Test public void m4aWithoutLyricsIsNull() throws IOException {
        byte[] f = bytes(atom("ftyp", "M4A ", 0, 0, 0, 0), atom("moov", atom("udta", atom("free", new byte[4]))));
        assertNull(text(f));
    }

    @Test public void unknownFormatAndMissingFile() throws IOException {
        assertNull(text(bytes("OggS", new byte[40])));
        assertNull(new EmbeddedLyricsSource(t -> null).load(TestTracks.queen(), new CancelToken()));
        assertNull(new EmbeddedLyricsSource(t -> new File(tmp.getRoot(), "missing.mp3"))
                .load(TestTracks.queen(), new CancelToken()));
    }
}
