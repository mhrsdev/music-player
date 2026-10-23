package com.mhrsdev.musicplayer.data.waveform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import org.junit.Test;

public class EnvelopeBuilderTest {
    private static final int RATE = PcmSynth.RATE;

    /** Feeds a signal in chunks of {@code chunk} frames, like codec output buffers. */
    private static EnvelopeBuilder feed(PcmSynth.Signal s, double seconds, int buckets, int channels, int chunk,
            boolean floatPcm) {
        long durationUs = (long) (seconds * 1_000_000);
        EnvelopeBuilder b = new EnvelopeBuilder(new SamplingPlan(durationUs, buckets));
        int total = (int) (seconds * RATE);
        for (int at = 0; at < total; at += chunk) {
            int n = Math.min(chunk, total - at);
            ByteBuffer pcm = floatPcm ? PcmSynth.pcmFloat(s, at, n, channels) : PcmSynth.pcm16(s, at, n, channels);
            b.add(pcm, channels, floatPcm, RATE, (long) (at * 1_000_000.0 / RATE));
        }
        return b;
    }

    @Test public void sineRmsIsAmplitudeOverRootTwo() {
        float[] rms = feed((t, c) -> PcmSynth.sine(t, 0.5), 2.0, 8, 1, 1152, false).rms();
        for (float v : rms) assertEquals(0.5 / Math.sqrt(2), v, 0.01);
    }

    @Test public void silenceIsZeroNotMissing() {
        EnvelopeBuilder b = feed((t, c) -> 0, 1.0, 8, 2, 1024, false);
        assertEquals(1f, b.coverage(), 0f);
        for (float v : b.rms()) assertEquals(0f, v, 0f);
    }

    @Test public void clippingSquareWaveHasFullScaleRms() {
        float[] rms = feed((t, c) -> Math.signum(Math.sin(2 * Math.PI * 100 * t) + 1e-9) * 2.0, 1.0, 4, 1, 700, false)
                .rms();
        for (float v : rms) assertEquals(1f, v, 0.01);
    }

    @Test public void burstsAppearInTheRightBuckets() {
        // 4 s, 8 buckets of 0.5 s; loud 440 Hz only in seconds [1, 2)
        float[] rms = feed((t, c) -> t >= 1 && t < 2 ? PcmSynth.sine(t, 0.9) : PcmSynth.sine(t, 0.02), 4.0, 8, 1, 512,
                false).rms();
        assertTrue(rms[2] > 0.5f && rms[3] > 0.5f);
        assertTrue(rms[0] < 0.05f && rms[4] < 0.05f && rms[7] < 0.05f);
    }

    @Test public void stereoIsDownmixedToMonoAndPhaseOppositeCancels() {
        float[] same = feed((t, c) -> PcmSynth.sine(t, 0.4), 1.0, 4, 2, 1000, false).rms();
        float[] opposite = feed((t, c) -> c == 0 ? PcmSynth.sine(t, 0.4) : -PcmSynth.sine(t, 0.4), 1.0, 4, 2, 1000,
                false).rms();
        float[] oneSide = feed((t, c) -> c == 0 ? PcmSynth.sine(t, 0.4) : 0, 1.0, 4, 2, 1000, false).rms();
        assertEquals(0.4 / Math.sqrt(2), same[1], 0.01);
        assertEquals(0f, opposite[1], 0.001);
        assertEquals(0.2 / Math.sqrt(2), oneSide[1], 0.01);
    }

    @Test public void floatPcmMatches16Bit() {
        PcmSynth.Signal s = (t, c) -> PcmSynth.sine(t, 0.3);
        float[] a = feed(s, 1.0, 4, 1, 999, false).rms();
        float[] b = feed(s, 1.0, 4, 1, 999, true).rms();
        for (int i = 0; i < a.length; i++) assertEquals(a[i], b[i], 0.002);
    }

    @Test public void oddChunkSizesAndTrailingPartialFrameAreHandled() {
        PcmSynth.Signal s = (t, c) -> PcmSynth.sine(t, 0.5);
        float[] a = feed(s, 1.0, 4, 2, 1, false).rms();     // one frame per buffer
        float[] b = feed(s, 1.0, 4, 2, 4093, false).rms();  // odd, larger than a bucket
        for (int i = 0; i < a.length; i++) assertEquals(a[i], b[i], 0.005);
        // 7 bytes of stereo 16-bit = 1 whole frame + 3 stray bytes
        EnvelopeBuilder eb = new EnvelopeBuilder(new SamplingPlan(1_000_000, 4));
        eb.add(ByteBuffer.allocate(7), 2, false, RATE, 0);
        assertEquals(0.25f, eb.coverage(), 0f);
    }

    @Test public void veryShortTracksStillProduceMeasuredBuckets() {
        // 5 ms track, 256 buckets (~20 us each, < 1 frame): fully decoded, most buckets hold <= 1 frame
        EnvelopeBuilder b = feed((t, c) -> PcmSynth.sine(t, 0.5), 0.005, 256, 1, 64, false);
        assertTrue(b.coverage() > 0.5f);
        float[] filled = WaveformMath.fillGaps(b.rms());
        assertTrue(filled != null && filled.length == 256);
    }

    @Test public void emptyBufferChangesNothing() {
        EnvelopeBuilder b = new EnvelopeBuilder(new SamplingPlan(1_000_000, 4));
        b.add(ByteBuffer.allocate(0), 2, false, RATE, 0);
        assertEquals(0f, b.coverage(), 0f);
    }

    @Test public void sparseFeedingGivesTheSameEnvelopeAsFullDecode() {
        // 4 min track with a quiet intro and a loud middle: decoding only the planned windows must match full decode.
        double seconds = 240;
        PcmSynth.Signal s = (t, c) -> PcmSynth.sine(t, t < 60 ? 0.05 : t < 180 ? 0.7 : 0.2);
        long durationUs = (long) (seconds * 1_000_000);
        SamplingPlan plan = new SamplingPlan(durationUs, WaveformMath.MASTER_BUCKETS);
        EnvelopeBuilder sparse = new EnvelopeBuilder(plan);
        int chunk = 1152, total = (int) (seconds * RATE), decodedFrames = 0;
        for (int at = 0; at < total; at += chunk) {
            long t = (long) (at * 1_000_000.0 / RATE);
            if (plan.nextFeedUs(t) != t) continue; // the extractor would skip this input
            sparse.add(PcmSynth.pcm16(s, at, chunk, 1), 1, false, RATE, t);
            decodedFrames += chunk;
        }
        float[] env = WaveformMath.toEnvelope(sparse.rms());
        assertTrue("decoded " + decodedFrames / (double) total, decodedFrames < 0.4 * total);
        assertTrue(sparse.coverage() > 0.99f);
        assertTrue(env[20] < 0.6f && env[128] > 0.95f && env[250] > 0.6f && env[250] < env[128]);
    }
}
