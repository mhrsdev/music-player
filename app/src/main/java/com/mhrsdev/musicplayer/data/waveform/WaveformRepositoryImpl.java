package com.mhrsdev.musicplayer.data.waveform;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.WaveformRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Cache-first waveform source: memory (8 master envelopes) -> {@link WaveformStore} -> {@link EnvelopeSource}.
 *
 * <p>Always one master envelope of {@link WaveformMath#MASTER_BUCKETS} values per track; {@code bars} is resampled
 * from it, so changing the bar count never re-decodes. Work is lazy: nothing happens until a LiveData gets an active
 * observer (the track is on screen) or {@link #prefetch} is called for the queued-next track. Concurrent requests
 * for one track share one job; a job nobody observes any more is cancelled; the single {@code worker} thread means
 * at most one extraction runs at a time. Failures are cached negatively (memory + store) so a bad file is tried once.
 * All mutable state is touched on the main thread only; the worker just reads {@link Job}'s volatile fields.
 */
public final class WaveformRepositoryImpl implements WaveformRepository {
    private static final int MEMORY_ENTRIES = 8;
    private static final float[] FAILED = new float[0];

    private final WaveformStore store;
    private final EnvelopeSource source;
    private final Function<Track, String> fingerprint;
    private final BooleanSupplier powerSave;
    private final Executor worker;
    private final Executor main;

    private final Map<String, float[]> memory = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > MEMORY_ENTRIES;
        }
    };
    private final Map<String, Job> inFlight = new HashMap<>();

    /**
     * @param fingerprint file size/modified/duration key, called on {@code worker}
     * @param powerSave true while the device is in battery saver (background prefetch is skipped then)
     * @param worker single low-priority thread; main: posts to the main thread
     */
    public WaveformRepositoryImpl(WaveformStore store, EnvelopeSource source, Function<Track, String> fingerprint,
            BooleanSupplier powerSave, Executor worker, Executor main) {
        this.store = store;
        this.source = source;
        this.fingerprint = fingerprint;
        this.powerSave = powerSave;
        this.worker = worker;
        this.main = main;
    }

    @Override public LiveData<float[]> waveformFor(Track track, int bars) {
        return new Result(track, bars);
    }

    /**
     * Warms the cache for the track queued next. Does nothing in battery saver or when already cached/in flight.
     * Main thread. Replaces an earlier unobserved prefetch.
     */
    public void prefetch(Track track) {
        String key = keyOf(track);
        if (memory.containsKey(key) || powerSave.getAsBoolean()) return;
        cancelUnobservedPrefetches(key);
        Job job = inFlight.get(key);
        if (job == null) job = start(key, track, false);
        job.prefetch = true;
    }

    private static String keyOf(Track track) {
        return track.id() + "|" + track.durationMs();
    }

    private Job start(String key, Track track, boolean observed) {
        Job job = new Job(key, track);
        job.hasObservers = observed;
        inFlight.put(key, job);
        worker.execute(() -> run(job));
        return job;
    }

    private void cancelUnobservedPrefetches(String except) {
        for (Job j : new ArrayList<>(inFlight.values())) {
            if (!j.key.equals(except) && j.prefetch && j.observers.isEmpty()) cancel(j);
        }
    }

    private void cancel(Job job) {
        job.cancelled = true;
        inFlight.remove(job.key, job);
    }

    /** Worker thread. */
    private void run(Job job) {
        if (job.cancelled) return;
        Track track = job.track;
        String fp = fingerprint.apply(track);
        byte[] blob = store.get(track.id(), fp);
        float[] master;
        if (blob != null && (blob.length == 0 || blob.length == WaveformMath.MASTER_BUCKETS)) {
            master = blob.length == 0 ? FAILED : WaveformMath.decode(blob);
        } else {
            if (job.cancelled || (!job.hasObservers && powerSave.getAsBoolean())) {
                main.execute(() -> finish(job, null));
                return;
            }
            float[] extracted = source.extract(track, () -> job.cancelled);
            if (extracted == null && job.cancelled) {
                main.execute(() -> finish(job, null));
                return;
            }
            master = extracted == null ? FAILED : extracted;
            store.put(track.id(), fp, WaveformMath.encode(master));
        }
        float[] result = master;
        main.execute(() -> finish(job, result));
    }

    /** Main thread. {@code master == null} = cancelled/skipped, nothing is cached. */
    private void finish(Job job, @Nullable float[] master) {
        inFlight.remove(job.key, job);
        if (master == null) return;
        memory.put(job.key, master);
        for (Result r : new ArrayList<>(job.observers)) r.deliver(master);
    }

    private static final class Job {
        final String key;
        final Track track;
        final List<Result> observers = new ArrayList<>();
        volatile boolean cancelled;
        volatile boolean hasObservers;
        boolean prefetch;

        Job(String key, Track track) {
            this.key = key;
            this.track = track;
        }
    }

    private final class Result extends LiveData<float[]> {
        private final Track track;
        private final int bars;
        private Job job;

        Result(Track track, int bars) {
            this.track = track;
            this.bars = bars;
        }

        @Override protected void onActive() {
            String key = keyOf(track);
            float[] cached = memory.get(key);
            if (cached != null) {
                deliver(cached);
                return;
            }
            cancelUnobservedPrefetches(key);
            job = inFlight.get(key);
            if (job == null) job = start(key, track, true);
            job.observers.add(this);
            job.hasObservers = true;
        }

        @Override protected void onInactive() {
            if (job == null) return;
            job.observers.remove(this);
            job.hasObservers = !job.observers.isEmpty();
            if (!job.hasObservers && !job.prefetch) cancel(job);
            job = null;
        }

        void deliver(float[] master) {
            if (master.length > 0) setValue(WaveformMath.resample(master, bars));
        }
    }
}
