package com.mhrsdev.musicplayer.data.waveform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WaveformRepositoryImplTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    /** Executor that runs tasks only when told to, so tests control the worker/main interleaving. */
    private static final class Manual implements java.util.concurrent.Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable r) { queue.add(r); }
        void drain() { while (!queue.isEmpty()) queue.poll().run(); }
    }

    private static final class FakeSource implements EnvelopeSource {
        final List<String> calls = new ArrayList<>();
        float[] result = ramp();
        BooleanSupplier lastCancel;
        @Override public float[] extract(Track track, BooleanSupplier cancelled) {
            calls.add(track.id());
            lastCancel = cancelled;
            return result;
        }
    }

    private static float[] ramp() {
        float[] env = new float[WaveformMath.MASTER_BUCKETS];
        for (int i = 0; i < env.length; i++) env[i] = i / (float) (env.length - 1);
        return env;
    }

    private static Track track(String id) {
        return new Track(id, "t", "a", "a", "al", "al", 180_000, null, 0, "content://x/" + id, false, false, 0, 0, 0);
    }

    private final Manual worker = new Manual(), main = new Manual();
    private final FakeSource source = new FakeSource();
    private final InMemoryWaveformStore store = new InMemoryWaveformStore();
    private boolean powerSave;
    private WaveformRepositoryImpl repo;

    @Before public void setUp() {
        repo = new WaveformRepositoryImpl(store, source, t -> "fp", () -> powerSave, worker, main);
    }

    private void settle() {
        worker.drain();
        main.drain();
    }

    private static final class Capture implements Observer<float[]> {
        final List<float[]> values = new ArrayList<>();
        @Override public void onChanged(float[] v) { values.add(v); }
    }

    @Test public void emitsNothingUntilReadyThenResampledValues() {
        Capture c = new Capture();
        repo.waveformFor(track("a"), 64).observeForever(c);
        assertTrue(c.values.isEmpty());
        settle();
        assertEquals(1, c.values.size());
        assertEquals(64, c.values.get(0).length);
        assertEquals(1f, c.values.get(0)[63], 0.01f);
        assertEquals(0f, c.values.get(0)[0], 0.01f);
    }

    @Test public void cacheFirstSecondRequestDoesNotExtractAgain() {
        repo.waveformFor(track("a"), 64).observeForever(new Capture());
        settle();
        Capture again = new Capture();
        repo.waveformFor(track("a"), 100).observeForever(again);
        assertEquals("served synchronously from memory", 1, again.values.size());
        assertEquals(100, again.values.get(0).length);
        settle();
        assertEquals(1, source.calls.size());
    }

    @Test public void concurrentRequestsShareOneExtraction() {
        Capture c1 = new Capture(), c2 = new Capture();
        repo.waveformFor(track("a"), 64).observeForever(c1);
        repo.waveformFor(track("a"), 32).observeForever(c2);
        settle();
        assertEquals(1, source.calls.size());
        assertEquals(64, c1.values.get(0).length);
        assertEquals(32, c2.values.get(0).length);
    }

    @Test public void persistedEnvelopeSkipsExtractionAfterMemoryIsGone() {
        repo.waveformFor(track("a"), 64).observeForever(new Capture());
        settle();
        WaveformRepositoryImpl fresh = new WaveformRepositoryImpl(store, source, t -> "fp", () -> false, worker, main);
        Capture c = new Capture();
        fresh.waveformFor(track("a"), 64).observeForever(c);
        settle();
        assertEquals(1, source.calls.size());
        assertEquals(1, c.values.size());
    }

    @Test public void changedFingerprintReExtracts() {
        repo.waveformFor(track("a"), 64).observeForever(new Capture());
        settle();
        WaveformRepositoryImpl fresh = new WaveformRepositoryImpl(store, source, t -> "fp2", () -> false, worker, main);
        fresh.waveformFor(track("a"), 64).observeForever(new Capture());
        settle();
        assertEquals(2, source.calls.size());
    }

    @Test public void failureIsNegativeCachedInMemoryAndStoreAndEmitsNothing() {
        source.result = null;
        Capture c = new Capture();
        repo.waveformFor(track("bad"), 64).observeForever(c);
        settle();
        assertTrue(c.values.isEmpty());
        repo.waveformFor(track("bad"), 64).observeForever(new Capture());
        WaveformRepositoryImpl fresh = new WaveformRepositoryImpl(store, source, t -> "fp", () -> false, worker, main);
        fresh.waveformFor(track("bad"), 64).observeForever(new Capture());
        settle();
        assertEquals(1, source.calls.size());
        assertEquals(0, store.get("bad", "fp").length);
    }

    @Test public void unobservedJobIsCancelledAndNotCached() {
        LiveData<float[]> live = repo.waveformFor(track("a"), 64);
        Capture c = new Capture();
        live.observeForever(c);
        live.removeObserver(c);
        worker.drain(); // job starts after cancel was requested
        main.drain();
        assertTrue(source.calls.isEmpty());
        assertNull(store.get("a", "fp"));
        // a later request starts fresh
        repo.waveformFor(track("a"), 64).observeForever(new Capture());
        settle();
        assertEquals(1, source.calls.size());
    }

    @Test public void cancellationFlagReachesTheExtractorMidFlight() {
        final boolean[] seen = new boolean[1];
        @SuppressWarnings("unchecked") LiveData<float[]>[] live = new LiveData[1];
        Capture c = new Capture();
        EnvelopeSource slow = (t, cancelled) -> {
            live[0].removeObserver(c); // user leaves while decoding
            seen[0] = cancelled.getAsBoolean();
            return null;
        };
        WaveformRepositoryImpl r = new WaveformRepositoryImpl(store, slow, t -> "fp", () -> false, worker, main);
        live[0] = r.waveformFor(track("a"), 64);
        live[0].observeForever(c);
        // removeObserver must run on the "main thread"; InstantTaskExecutorRule makes every thread main
        settle();
        assertTrue(seen[0]);
        assertNull("cancelled work is not negative-cached", store.get("a", "fp"));
        assertTrue(c.values.isEmpty());
    }

    @Test public void prefetchWarmsCacheButIsSkippedInBatterySaver() {
        powerSave = true;
        repo.prefetch(track("n"));
        settle();
        assertTrue(source.calls.isEmpty());
        powerSave = false;
        repo.prefetch(track("n"));
        settle();
        assertEquals(1, source.calls.size());
        Capture c = new Capture();
        repo.waveformFor(track("n"), 16).observeForever(c);
        assertEquals("prefetched result served from memory", 1, c.values.size());
        repo.prefetch(track("n"));
        settle();
        assertEquals(1, source.calls.size());
    }

    @Test public void visibleTrackIsExtractedEvenInBatterySaver() {
        powerSave = true;
        Capture c = new Capture();
        repo.waveformFor(track("a"), 16).observeForever(c);
        settle();
        assertEquals(1, source.calls.size());
        assertEquals(1, c.values.size());
    }

    @Test public void prefetchQueuedWhileBatterySaverTurnsOnIsDroppedAtRunTime() {
        repo.prefetch(track("n"));
        powerSave = true;
        settle();
        assertTrue(source.calls.isEmpty());
        assertNull(store.get("n", "fp"));
    }

    @Test public void newRequestCancelsStalePrefetchSoOnlyOneExtractionRuns() {
        repo.prefetch(track("n1"));
        repo.prefetch(track("n2"));
        Capture c = new Capture();
        repo.waveformFor(track("v"), 16).observeForever(c);
        settle();
        assertEquals(List.of("v"), source.calls);
        assertNotNull(c.values.get(0));
        assertFalse(c.values.isEmpty());
    }

    @Test public void barCountsOfZeroAndOneAreSafe() {
        Capture c0 = new Capture(), c1 = new Capture();
        repo.waveformFor(track("a"), 0).observeForever(c0);
        repo.waveformFor(track("a"), 1).observeForever(c1);
        settle();
        assertEquals(0, c0.values.get(0).length);
        assertEquals(1, c1.values.get(0).length);
    }
}
