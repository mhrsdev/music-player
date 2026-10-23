package com.mhrsdev.musicplayer.data.waveform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Streams decoded PCM into per-bucket RMS: downmixes to mono on the fly and keeps only a sum of squares and a count
 * per bucket, so memory is O(buckets) however long the track is. Pure Java, unit-tested.
 */
public final class EnvelopeBuilder {
    private final SamplingPlan plan;
    private final double[] sumSq;
    private final long[] count;

    public EnvelopeBuilder(SamplingPlan plan) {
        this.plan = plan;
        sumSq = new double[plan.buckets()];
        count = new long[plan.buckets()];
    }

    /**
     * Adds one decoded buffer ({@code pcm.remaining()} bytes of interleaved 16-bit or 32-bit float samples, native
     * byte order) whose first frame is at {@code ptsUs}. Frames outside the plan's windows are ignored.
     */
    public void add(ByteBuffer pcm, int channels, boolean floatPcm, int sampleRate, long ptsUs) {
        int bytesPerSample = floatPcm ? 4 : 2;
        int frames = pcm.remaining() / (bytesPerSample * channels);
        int pos = pcm.position();
        ByteOrder saved = pcm.order();
        pcm.order(ByteOrder.nativeOrder());
        double usPerFrame = 1_000_000.0 / sampleRate;
        for (int f = 0; f < frames; f++) {
            int b = plan.bucketAt(ptsUs + (long) (f * usPerFrame));
            if (b < 0) continue;
            double mono = 0;
            for (int c = 0; c < channels; c++) {
                int at = pos + (f * channels + c) * bytesPerSample;
                mono += floatPcm ? pcm.getFloat(at) : pcm.getShort(at) / 32768.0;
            }
            mono /= channels;
            sumSq[b] += mono * mono;
            count[b]++;
        }
        pcm.order(saved);
    }

    /** Fraction of buckets that received at least one frame. */
    public float coverage() {
        int n = 0;
        for (long c : count) if (c > 0) n++;
        return (float) n / count.length;
    }

    /** RMS per bucket (full scale = 1); NaN for buckets that received no frame. */
    public float[] rms() {
        float[] out = new float[count.length];
        Arrays.fill(out, Float.NaN);
        for (int i = 0; i < out.length; i++) if (count[i] > 0) out[i] = (float) Math.sqrt(sumSq[i] / count[i]);
        return out;
    }
}
