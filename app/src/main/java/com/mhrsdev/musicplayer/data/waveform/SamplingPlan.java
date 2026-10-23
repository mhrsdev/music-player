package com.mhrsdev.musicplayer.data.waveform;

/**
 * Which parts of a track are decoded. The track is cut into {@code buckets} equal slices; only the first
 * {@code windowUs} of each slice is measured (the whole slice when it is shorter), preceded by {@code warmupUs} of
 * input that is decoded but not measured so codecs with inter-frame state (MP3 reservoir, AAC/Vorbis/Opus overlap)
 * have recovered after a skipped gap. Short tracks therefore decode fully; a 60 min podcast decodes ~1.4%.
 * Pure Java, unit-tested.
 */
public final class SamplingPlan {
    /** Measured audio per bucket. shortcut: one window per bucket, upgrade to 2 if bars look jittery on device. */
    static final long MAX_WINDOW_US = 200_000;
    static final long WARMUP_US = 50_000;

    private final long durationUs;
    private final int buckets;
    private final long windowUs;
    private final long warmupUs;

    public SamplingPlan(long durationUs, int buckets) {
        this(durationUs, buckets, MAX_WINDOW_US, WARMUP_US);
    }

    SamplingPlan(long durationUs, int buckets, long maxWindowUs, long warmupUs) {
        if (durationUs <= 0 || buckets <= 0) throw new IllegalArgumentException("duration and buckets must be > 0");
        this.durationUs = durationUs;
        this.buckets = buckets;
        this.windowUs = Math.max(1, Math.min(maxWindowUs, durationUs / buckets));
        this.warmupUs = warmupUs;
    }

    public int buckets() { return buckets; }

    long startUs(int bucket) { return durationUs * bucket / buckets; }

    private long windowEndUs(int bucket) {
        return Math.min(startUs(bucket) + windowUs, durationUs);
    }

    private int sliceOf(long tUs) {
        int b = (int) Math.min(buckets - 1, Math.max(0, tUs * buckets / durationUs));
        while (b > 0 && tUs < startUs(b)) b--;
        while (b < buckets - 1 && tUs >= startUs(b + 1)) b++;
        return b;
    }

    /** Bucket whose measured window contains {@code tUs}, or -1 (warm-up, skipped part, or outside the track). */
    public int bucketAt(long tUs) {
        if (tUs < 0 || tUs >= durationUs) return -1;
        int b = sliceOf(tUs);
        return tUs < windowEndUs(b) ? b : -1;
    }

    /**
     * Earliest time {@code >= tUs} whose input must be decoded (a window or its warm-up), or -1 when nothing is left.
     * Callers skip input before the returned time (advance, or seek for big gaps).
     */
    public long nextFeedUs(long tUs) {
        if (tUs >= durationUs) return -1;
        for (int b = sliceOf(Math.max(tUs, 0)); b < buckets; b++) {
            if (tUs < windowEndUs(b)) return Math.max(tUs, Math.max(0, startUs(b) - warmupUs));
        }
        return -1;
    }
}
