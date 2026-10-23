package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Metadata;
import androidx.media3.extractor.metadata.id3.InternalFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.extractor.metadata.vorbis.VorbisComment;
import com.mhrsdev.musicplayer.domain.model.ReplayGainMode;
import org.junit.Test;

public class ReplayGainTest {
    private static final double EPS = 1e-9;

    @Test public void parsesCommonGainFormats() {
        assertEquals(-6.48, ReplayGain.parseDb("-6.48 dB"), EPS);
        assertEquals(3.2, ReplayGain.parseDb("+3.2"), EPS);
        assertEquals(-6.48, ReplayGain.parseDb(" -6,48 DB "), EPS);
        assertEquals(0.0, ReplayGain.parseDb("0 dB"), EPS);
    }

    @Test public void rejectsGarbage() {
        assertTrue(Double.isNaN(ReplayGain.parseDb(null)));
        assertTrue(Double.isNaN(ReplayGain.parseDb("loud")));
        assertTrue(Double.isNaN(ReplayGain.parseDb("")));
        assertTrue(Double.isNaN(ReplayGain.parseDb("NaN dB")));
        assertTrue(Double.isNaN(ReplayGain.parseDb("Infinity")));
    }

    @Test public void modeChoosesTagWithFallbackAndNeverAmplifies() {
        ReplayGain both = new ReplayGain(-7.0, -5.0);
        assertEquals(-7.0, both.gainDb(ReplayGainMode.TRACK), EPS);
        assertEquals(-5.0, both.gainDb(ReplayGainMode.ALBUM), EPS);
        assertEquals(0.0, both.gainDb(ReplayGainMode.OFF), EPS);
        ReplayGain trackOnly = new ReplayGain(-7.0, Double.NaN);
        assertEquals(-7.0, trackOnly.gainDb(ReplayGainMode.ALBUM), EPS);
        assertEquals(0.0, ReplayGain.NONE.gainDb(ReplayGainMode.TRACK), EPS);
        assertEquals(0.0, new ReplayGain(+4.0, 0).gainDb(ReplayGainMode.TRACK), EPS); // quiet track: not boosted
        assertEquals(-30.0, new ReplayGain(-60.0, 0).gainDb(ReplayGainMode.TRACK), EPS);
    }

    @Test public void volumeIsLinear() {
        assertEquals(1f, ReplayGain.NONE.volume(ReplayGainMode.TRACK), 1e-6f);
        assertEquals(0.5012f, new ReplayGain(-6.0, 0).volume(ReplayGainMode.TRACK), 1e-3f);
    }

    @Test public void readsId3Vorbis_AndMp4Tags() {
        ReplayGain id3 = ReplayGain.from(new Metadata(
                new TextInformationFrame("TIT2", null, "Song"),
                new TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", "-8.1 dB"),
                new TextInformationFrame("TXXX", "replaygain_album_gain", "-6.0 dB")));
        assertEquals(-8.1, id3.trackDb, EPS);
        assertEquals(-6.0, id3.albumDb, EPS);

        ReplayGain vorbis = ReplayGain.from(new Metadata(new VorbisComment("REPLAYGAIN_ALBUM_GAIN", "-3.50 dB")));
        assertTrue(Double.isNaN(vorbis.trackDb));
        assertEquals(-3.5, vorbis.albumDb, EPS);

        ReplayGain mp4 = ReplayGain.from(new Metadata(new InternalFrame("com.apple.iTunes", "REPLAYGAIN_TRACK_GAIN", "-9.9 dB")));
        assertEquals(-9.9, mp4.trackDb, EPS);

        assertSame(ReplayGain.NONE, ReplayGain.from(new Metadata(new VorbisComment("TITLE", "x"))));
    }
}
