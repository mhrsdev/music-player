package com.mhrsdev.musicplayer.diag;

/**
 * Frame statistics per "screen tag" (D38, PT1): fixed-size histograms and counters, no allocation per frame. Pure Java
 * (no Android types), so the maths is unit-tested with fake frames. {@link #record} is called from the probe's own
 * HandlerThread; {@link #slotFor} from the main thread on a screen change; reads come from any thread. Everything is
 * guarded by one lock (uncontended in practice: the lock is held for a few hundred nanoseconds).
 */
public class PerfStats {
    /** Histogram bucket upper bounds in ms: 0-4, 4-8, 8-12, 12-16.7, 16.7-33, 33-66, 66-150, 150+. */
    public static final long[] BUCKET_NS = {4_000_000L, 8_000_000L, 12_000_000L, 16_700_000L, 33_000_000L, 66_000_000L,
            150_000_000L};
    public static final String[] BUCKET_LABELS = {"0-4", "4-8", "8-12", "12-17", "17-33", "33-66", "66-150", "150+"};
    public static final int BUCKETS = 8;
    /** Phases kept per frame: the eight CPU/pipeline phases of FrameMetrics, then the GPU time (API 31+, overlaps them). */
    public static final String[] PHASES = {"delay", "input", "anim", "layout", "draw", "sync", "issue", "swap", "gpu"};
    public static final int PHASE_COUNT = 9;
    public static final int GPU = 8;
    /** Which decorations were active (set by their owners through {@link PerfProbe#setFlag}). */
    public static final String[] FLAG_NAMES = {"playing", "lyrics", "lyrics-blur", "backdrop-drift", "decor-beat", "springs",
            "mini-player"};
    public static final int FLAG_COUNT = 7;
    public static final long FROZEN_NS = 700_000_000L;
    public static final int MAX_SLOTS = 24;
    /** A frame counts as "idle" (nobody is touching the app) when no input was handled for this long before it. */
    public static final long IDLE_AFTER_MS = 1500;

    /** One screen tag's numbers. Fields are read by {@link PerfReport} under {@link #lock}. */
    public static class Slot {
        public final String tag;
        public final long[] hist = new long[BUCKETS];
        public long frames, janky, slow, frozen;
        public long sumTotalNs, sumIntervalNs, maxTotalNs;
        public long activeMs;
        public final long[] phaseSum = new long[PHASE_COUNT];
        public final long[] phaseSumJanky = new long[PHASE_COUNT];
        public long jankyTotalNs;
        public final long[] worstPhases = new long[PHASE_COUNT];
        public long worstTotalNs, worstIntervalNs, worstAtMs;
        public int worstFlags;
        public final long[] flagFrames = new long[FLAG_COUNT];
        public final long[] flagJanky = new long[FLAG_COUNT];
        /** Frames drawn while nobody touched the app (no input for {@link #IDLE_AFTER_MS}), the time spent so, and which decorations ran. */
        public long idleFrames, idleMs;
        public final long[] idleFlagFrames = new long[FLAG_COUNT];

        Slot(String tag) {
            this.tag = tag;
        }

        /** Percentile (0..1) in ms, interpolated inside the bucket; the open last bucket is bounded by the slowest frame. */
        public double percentileMs(double p) {
            if (frames == 0) return 0;
            double target = p * frames;
            double seen = 0;
            for (int b = 0; b < BUCKETS; b++) {
                if (hist[b] == 0) continue;
                if (seen + hist[b] >= target) {
                    double lo = b == 0 ? 0 : BUCKET_NS[b - 1] / 1e6;
                    double hi = Math.max(lo, Math.min(b < BUCKETS - 1 ? BUCKET_NS[b] / 1e6 : Double.MAX_VALUE, maxTotalNs / 1e6));
                    return lo + (hi - lo) * (target - seen) / hist[b];
                }
                seen += hist[b];
            }
            return maxTotalNs / 1e6;
        }

        /** Redraws per second while untouched, or -1 when less than 5 s of idle time was seen. */
        public double idleFps() {
            return idleMs < 5_000 ? -1 : idleFrames * 1000.0 / idleMs;
        }

        public double jankPercent() {
            return frames == 0 ? 0 : 100.0 * janky / frames;
        }
    }

    private final Object lock = new Object();
    private final Slot[] slots = new Slot[MAX_SLOTS];
    private int used;
    private long firstDrawFrames, droppedReports;
    private long startMs;
    private long lastInputMs, idleCursor;
    private boolean foreground = true;

    public PerfStats(long nowMs) {
        startMs = nowMs;
        lastInputMs = nowMs; // the person just tapped the switch
        idleCursor = nowMs;
        slotFor("other"); // slot 0 takes the overflow
    }

    /** The slot index of {@code tag}, created on first use ("other", slot 0, once {@link #MAX_SLOTS} are taken). */
    public int slotFor(String tag) {
        synchronized (lock) {
            for (int i = 0; i < used; i++) if (slots[i].tag.equals(tag)) return i;
            if (used == MAX_SLOTS) return 0;
            slots[used] = new Slot(tag);
            return used++;
        }
    }

    /** Adds the time the screen was shown (called when the tag changes or the recording ends). */
    public void addActive(int slot, long ms) {
        synchronized (lock) {
            slots[slot].activeMs += ms;
        }
    }

    /**
     * One frame. {@code phases} is the caller's reused array of {@link #PHASE_COUNT} nanosecond values; nothing is kept
     * from it except sums and (for a new worst frame) a copy into the slot's own array. {@code intervalNs} is the
     * frame budget of the display at that moment.
     */
    public void record(int slot, int flags, long totalNs, long[] phases, long intervalNs, long nowMs, boolean input) {
        synchronized (lock) {
            Slot s = slots[slot];
            accountIdle(nowMs, slot);
            if (input) {
                lastInputMs = nowMs;
            } else if (foreground && nowMs - lastInputMs >= IDLE_AFTER_MS) {
                s.idleFrames++;
                for (int f = 0; f < FLAG_COUNT; f++) if ((flags & (1 << f)) != 0) s.idleFlagFrames[f]++;
            }
            int b = 0;
            while (b < BUCKETS - 1 && totalNs >= BUCKET_NS[b]) b++;
            s.hist[b]++;
            s.frames++;
            s.sumTotalNs += totalNs;
            s.sumIntervalNs += intervalNs;
            if (totalNs > s.maxTotalNs) s.maxTotalNs = totalNs;
            boolean janky = totalNs > intervalNs;
            if (janky) {
                s.janky++;
                s.jankyTotalNs += totalNs;
                if (totalNs > 2 * intervalNs) s.slow++;
                if (totalNs > FROZEN_NS) s.frozen++;
            }
            for (int i = 0; i < PHASE_COUNT; i++) {
                s.phaseSum[i] += phases[i];
                if (janky) s.phaseSumJanky[i] += phases[i];
            }
            for (int f = 0; f < FLAG_COUNT; f++) {
                if ((flags & (1 << f)) == 0) continue;
                s.flagFrames[f]++;
                if (janky) s.flagJanky[f]++;
            }
            if (totalNs > s.worstTotalNs) {
                s.worstTotalNs = totalNs;
                s.worstIntervalNs = intervalNs;
                s.worstAtMs = nowMs - startMs;
                s.worstFlags = flags;
                System.arraycopy(phases, 0, s.worstPhases, 0, PHASE_COUNT);
            }
        }
    }

    /** Idle time up to {@code nowMs} belongs to {@code slot}; call before the slot changes and from the sampler. */
    public void tick(int slot, long nowMs) {
        synchronized (lock) {
            accountIdle(nowMs, slot);
        }
    }

    /** The app's activity is on screen (true) or stopped (false): idle time only counts while it is on screen. */
    public void setForeground(boolean on, int slot, long nowMs) {
        synchronized (lock) {
            accountIdle(nowMs, slot);
            foreground = on;
        }
    }

    private void accountIdle(long nowMs, int slot) {
        long from = Math.max(idleCursor, lastInputMs + IDLE_AFTER_MS);
        if (foreground && nowMs > from) slots[slot].idleMs += nowMs - from;
        if (nowMs > idleCursor) idleCursor = nowMs;
    }

    public void addFirstDraw() {
        synchronized (lock) {
            firstDrawFrames++;
        }
    }

    public void addDropped(int n) {
        synchronized (lock) {
            droppedReports += n;
        }
    }

    public long firstDrawFrames() {
        synchronized (lock) {
            return firstDrawFrames;
        }
    }

    public long droppedReports() {
        synchronized (lock) {
            return droppedReports;
        }
    }

    public long startMs() {
        return startMs;
    }

    /** Frames over all screens (cheap: the live summary reads it every 5 s). */
    public long totalFrames() {
        synchronized (lock) {
            long n = 0;
            for (int i = 0; i < used; i++) n += slots[i].frames;
            return n;
        }
    }

    public long totalJanky() {
        synchronized (lock) {
            long n = 0;
            for (int i = 0; i < used; i++) n += slots[i].janky;
            return n;
        }
    }

    /** The screen with the highest janky share among those with at least {@code minFrames} frames, or null. */
    public Slot worstScreen(long minFrames) {
        synchronized (lock) {
            Slot worst = null;
            for (int i = 0; i < used; i++) {
                Slot s = slots[i];
                if (s.frames >= minFrames && (worst == null || s.jankPercent() > worst.jankPercent())) worst = s;
            }
            return worst;
        }
    }

    /** Runs {@code reader} on a consistent view of the slots (in creation order, only those with frames or time). */
    public void read(java.util.function.Consumer<Slot> reader) {
        synchronized (lock) {
            for (int i = 0; i < used; i++) if (slots[i].frames > 0 || slots[i].activeMs > 0) reader.accept(slots[i]);
        }
    }
}
