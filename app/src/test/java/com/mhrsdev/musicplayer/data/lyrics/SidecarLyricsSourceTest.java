package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SidecarLyricsSourceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private LyricsSource.Found load(String audioName) throws IOException {
        File audio = new File(tmp.getRoot(), audioName);
        return new SidecarLyricsSource(t -> audio).load(TestTracks.queen(), new CancelToken());
    }

    @Test public void lrcNextToAudioIsReadAndDecoded() throws IOException {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "[00:01.00]سلام".getBytes(StandardCharsets.UTF_8);
        EmbeddedTagFixtures.write(tmp.getRoot(), "My Song.lrc", EmbeddedTagFixtures.bytes(bom, body));
        LyricsSource.Found f = load("My Song.mp3");
        assertEquals("[00:01.00]سلام", f.text());
        assertEquals("LRC file", f.source());
    }

    @Test public void lrcWinsOverTxtAndTxtIsFallback() throws IOException {
        EmbeddedTagFixtures.write(tmp.getRoot(), "a.txt", "plain".getBytes(StandardCharsets.UTF_8));
        assertEquals("plain", load("a.flac").text());
        EmbeddedTagFixtures.write(tmp.getRoot(), "a.lrc", "[00:01.00]x".getBytes(StandardCharsets.UTF_8));
        assertEquals("[00:01.00]x", load("a.flac").text());
    }

    @Test public void dotsInNameAndNoSidecar() throws IOException {
        EmbeddedTagFixtures.write(tmp.getRoot(), "01. Intro.v2.lrc", "[00:01.00]x".getBytes(StandardCharsets.UTF_8));
        assertEquals("[00:01.00]x", load("01. Intro.v2.mp3").text());
        assertNull(load("other.mp3"));
    }

    @Test public void noAccessiblePathIsSkipped() throws IOException {
        assertNull(new SidecarLyricsSource(t -> null).load(TestTracks.queen(), new CancelToken()));
    }

    @Test public void fileOnlyLocatorUnderstandsFileUrisAndPaths() {
        var t1 = TestTracks.track("1", "a", "b", "c", 1, "file:///music/My%20Song.mp3");
        var t2 = TestTracks.track("2", "a", "b", "c", 1, "/music/x.mp3");
        var t3 = TestTracks.track("3", "a", "b", "c", 1, "content://media/external/audio/media/5");
        assertEquals(new File("/music/My Song.mp3"), AudioFileLocator.FILE_ONLY.locate(t1));
        assertEquals(new File("/music/x.mp3"), AudioFileLocator.FILE_ONLY.locate(t2));
        assertNull(AudioFileLocator.FILE_ONLY.locate(t3));
        assertNull(AudioFileLocator.FILE_ONLY.locate(TestTracks.queen()));
    }
}
