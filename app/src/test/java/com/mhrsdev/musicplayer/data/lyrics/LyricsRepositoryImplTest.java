package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

/** LiveData behavior of the repository; the executor is manual so the test controls when work runs. */
public class LyricsRepositoryImplTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private final Queue<Runnable> queue = new ArrayDeque<>();
    private final Executor manual = queue::add;
    private final List<CancelToken> tokens = new ArrayList<>();
    private int remoteCalls;
    private String remoteText = "[00:01.00]hello";
    private final LyricsSource remote = (t, c) -> {
        remoteCalls++;
        tokens.add(c);
        return remoteText == null ? null : new LyricsSource.Found(remoteText, "LRCLIB");
    };
    private final LyricsRepositoryImpl repo = new LyricsRepositoryImpl(
            new LyricsResolver(Collections.emptyList(), remote, new InMemoryLyricsCacheStore(), System::currentTimeMillis),
            manual);
    private final Track track = TestTracks.queen();

    private static final class Capture implements Observer<Lyrics> {
        final List<Lyrics> values = new ArrayList<>();
        @Override public void onChanged(Lyrics l) {
            values.add(l);
        }
    }

    private void runAll() {
        Runnable r;
        while ((r = queue.poll()) != null) r.run();
    }

    @Test public void nothingRunsUntilObservedThenExactlyOneValueArrives() {
        LiveData<Lyrics> live = repo.lyricsFor(track);
        assertTrue(queue.isEmpty());
        Capture c = new Capture();
        live.observeForever(c);
        assertEquals(1, queue.size());
        assertTrue(c.values.isEmpty()); // no value while loading
        runAll();
        assertEquals(1, c.values.size());
        assertTrue(c.values.get(0).synced());
        assertEquals(track.id(), c.values.get(0).trackId());
    }

    @Test public void notFoundEmitsNoneNotNothing() {
        remoteText = null;
        Capture c = new Capture();
        repo.lyricsFor(track).observeForever(c);
        runAll();
        assertEquals(1, c.values.size());
        assertTrue(c.values.get(0).isEmpty());
    }

    @Test public void concurrentCallsForOneTrackShareOneLookupAndOneRequest() {
        LiveData<Lyrics> a = repo.lyricsFor(track);
        LiveData<Lyrics> b = repo.lyricsFor(track);
        assertSame(a, b);
        Capture ca = new Capture();
        Capture cb = new Capture();
        a.observeForever(ca);
        b.observeForever(cb);
        runAll();
        assertEquals(1, remoteCalls);
        assertEquals(1, ca.values.size());
        assertEquals(1, cb.values.size());
    }

    @Test public void differentTracksAreIndependent() {
        Track other = TestTracks.track("t2", "Other", "X", "Y", 100_000, null);
        assertNotSame(repo.lyricsFor(track), repo.lyricsFor(other));
    }

    @Test public void losingTheLastObserverCancelsTheInFlightLookup() {
        LiveData<Lyrics> live = repo.lyricsFor(track);
        Capture c = new Capture();
        live.observeForever(c);
        Runnable work = queue.poll();
        live.removeObserver(c); // cancels before the worker even starts
        work.run();
        assertEquals(0, remoteCalls);
        assertTrue(c.values.isEmpty());
        assertNull(live.getValue());
    }

    @Test public void staleWorkerAfterRestartDoesNotPublish() {
        LiveData<Lyrics> live = repo.lyricsFor(track);
        Capture c = new Capture();
        live.observeForever(c);
        Runnable stale = queue.poll();
        live.removeObserver(c);
        live.observeForever(c); // restarts with a fresh token
        stale.run(); // its token was cancelled: no request, no value
        assertEquals(0, remoteCalls);
        runAll();
        assertEquals(1, remoteCalls);
        assertEquals(1, c.values.size());
    }

    @Test public void droppingTheObserverMidRequestCancelsItsTokenAndDiscardsTheResult() {
        AtomicReference<LiveData<Lyrics>> live = new AtomicReference<>();
        Capture c = new Capture();
        LyricsSource source = (t, cancel) -> {
            live.get().removeObserver(c); // the UI goes away while the request is in flight
            assertTrue(cancel.isCancelled());
            return new LyricsSource.Found("[00:01.00]late", "LRCLIB");
        };
        LyricsRepositoryImpl r = new LyricsRepositoryImpl(new LyricsResolver(Collections.emptyList(), source,
                new InMemoryLyricsCacheStore(), System::currentTimeMillis), manual);
        live.set(r.lyricsFor(track));
        live.get().observeForever(c);
        runAll();
        assertTrue(c.values.isEmpty());
        assertNull(live.get().getValue());
    }

    @Test public void reobservingAfterCancelRestartsTheLookup() {
        LiveData<Lyrics> live = repo.lyricsFor(track);
        Capture c = new Capture();
        live.observeForever(c);
        live.removeObserver(c);
        queue.clear();
        Capture c2 = new Capture();
        LiveData<Lyrics> again = repo.lyricsFor(track);
        again.observeForever(c2);
        runAll();
        assertEquals(1, c2.values.size());
    }

    @Test public void finishedLookupIsForgottenSoTheNextCallRereadsTheCache() {
        LiveData<Lyrics> first = repo.lyricsFor(track);
        first.observeForever(new Capture());
        runAll();
        LiveData<Lyrics> second = repo.lyricsFor(track);
        assertNotSame(first, second);
        Capture c = new Capture();
        second.observeForever(c);
        runAll();
        assertEquals(1, remoteCalls); // second answer came from the cache
        assertEquals(1, c.values.size());
    }

    @Test public void unexpectedExceptionBecomesNoneInsteadOfLoadingForever() {
        LyricsSource boom = (t, c) -> {
            throw new IllegalStateException("db closed");
        };
        LyricsCacheStore failing = new LyricsCacheStore() {
            @Override public Entry get(String id) {
                throw new IllegalStateException("db closed");
            }
            @Override public void put(String id, Entry e) { }
        };
        LyricsRepositoryImpl r = new LyricsRepositoryImpl(
                new LyricsResolver(Collections.emptyList(), boom, failing, System::currentTimeMillis), manual);
        Capture c = new Capture();
        r.lyricsFor(track).observeForever(c);
        runAll();
        assertEquals(1, c.values.size());
        assertTrue(c.values.get(0).isEmpty());
    }

    @Test public void ioFailureEmitsNone() {
        LyricsSource failing = (t, c) -> {
            throw new IOException("offline");
        };
        LyricsRepositoryImpl r = new LyricsRepositoryImpl(new LyricsResolver(Collections.emptyList(), failing,
                new InMemoryLyricsCacheStore(), System::currentTimeMillis), manual);
        Capture c = new Capture();
        r.lyricsFor(track).observeForever(c);
        runAll();
        assertTrue(c.values.get(0).isEmpty());
    }
}
