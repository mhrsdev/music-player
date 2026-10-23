package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LyricsResolverTest {
    private static final String SYNCED = "[00:01.00]one\n[00:02.00]two";
    private static final String PLAIN = "one\ntwo";

    /** Scripted source: returns {@code result}, or throws {@code error}; counts calls. */
    private static final class Scripted implements LyricsSource {
        final String label;
        @Nullable String text;
        @Nullable IOException error;
        int calls;

        Scripted(String label, @Nullable String text) {
            this.label = label;
            this.text = text;
        }

        @Nullable @Override public Found load(Track track, CancelToken cancel) throws IOException {
            calls++;
            if (error != null) throw error;
            return text == null ? null : new Found(text, label);
        }
    }

    private final Track track = TestTracks.queen();
    private final InMemoryLyricsCacheStore cache = new InMemoryLyricsCacheStore();
    private long now = 1_000_000;
    private final Scripted embedded = new Scripted("Embedded", null);
    private final Scripted sidecar = new Scripted("LRC file", null);
    private final Scripted remote = new Scripted("LRCLIB", null);
    private final LyricsResolver resolver = new LyricsResolver(Arrays.asList(embedded, sidecar), remote, cache, () -> now);

    private Lyrics resolve() {
        return resolver.resolve(track, new CancelToken());
    }

    @Test public void embeddedSyncedWinsAndNothingElseRuns() {
        embedded.text = SYNCED;
        sidecar.text = SYNCED;
        Lyrics l = resolve();
        assertEquals("Embedded", l.source());
        assertTrue(l.synced());
        assertEquals(0, sidecar.calls);
        assertEquals(0, remote.calls);
        assertNull(cache.get(track.id())); // local results are not cached
    }

    @Test public void sidecarIsUsedWhenEmbeddedIsMissing() {
        sidecar.text = SYNCED;
        assertEquals("LRC file", resolve().source());
        assertEquals(0, remote.calls);
    }

    @Test public void syncedSidecarBeatsPlainEmbedded() {
        embedded.text = PLAIN;
        sidecar.text = SYNCED;
        Lyrics l = resolve();
        assertEquals("LRC file", l.source());
        assertTrue(l.synced());
        assertEquals(0, remote.calls);
    }

    @Test public void syncedRemoteUpgradesLocalPlain() {
        embedded.text = PLAIN;
        remote.text = SYNCED;
        Lyrics l = resolve();
        assertEquals("LRCLIB", l.source());
        assertTrue(l.synced());
    }

    @Test public void plainRemoteDoesNotReplaceLocalPlain() {
        embedded.text = PLAIN;
        remote.text = "other plain";
        assertEquals("Embedded", resolve().source());
    }

    @Test public void remoteIsUsedWhenNothingLocal() {
        remote.text = SYNCED;
        Lyrics l = resolve();
        assertEquals("LRCLIB", l.source());
        assertEquals(track.id(), l.trackId());
        assertEquals(1, remote.calls);
    }

    @Test public void remotePlainIsAcceptedWhenNothingLocal() {
        remote.text = PLAIN;
        Lyrics l = resolve();
        assertFalse(l.synced());
        assertEquals(2, l.lines().size());
    }

    @Test public void foundRemoteResultIsCachedAndServedWithoutNetwork() {
        remote.text = SYNCED;
        resolve();
        remote.text = null; // would be "not found" now
        remote.calls = 0;
        Lyrics again = resolve();
        assertEquals("LRCLIB", again.source());
        assertTrue(again.synced());
        assertEquals(0, remote.calls);
    }

    @Test public void negativeResultIsCachedForDaysThenRetried() {
        assertTrue(resolve().isEmpty());
        assertEquals(1, remote.calls);
        now += LyricsResolver.NOT_FOUND_TTL_MS - 1;
        assertTrue(resolve().isEmpty());
        assertEquals(1, remote.calls); // still cached
        now += 2;
        remote.text = SYNCED;
        assertFalse(resolve().isEmpty());
        assertEquals(2, remote.calls); // expired: asked again
    }

    @Test public void failuresAreCachedBrieflyToAvoidHammering() {
        remote.error = new IOException("HTTP 503");
        assertTrue(resolve().isEmpty());
        assertTrue(resolve().isEmpty());
        assertEquals(1, remote.calls);
        now += LyricsResolver.ERROR_TTL_MS + 1;
        remote.error = null;
        remote.text = SYNCED;
        assertFalse(resolve().isEmpty());
        assertEquals(2, remote.calls);
    }

    @Test public void emptyRemoteTextCountsAsNotFound() {
        remote.text = "  \n [ti:x] ";
        assertTrue(resolve().isEmpty());
        assertNull(cache.get(track.id()).text());
    }

    @Test public void cachedPositiveEntryIsSkippedWhenExpired() {
        cache.put(track.id(), new LyricsCacheStore.Entry(SYNCED, "LRCLIB", now - 1));
        remote.text = "[00:09.00]fresh";
        assertEquals(9_000, resolve().lines().get(0).timeMs());
    }

    @Test public void onlineSettingOffSkipsTheNetworkButStillUsesLocalAndCachedLyrics() {
        LyricsResolver offline = new LyricsResolver(Arrays.asList(embedded, sidecar), remote, cache, () -> now, () -> false);
        remote.text = SYNCED;
        assertTrue(offline.resolve(track, new CancelToken()).isEmpty());
        assertEquals(0, remote.calls);
        assertNull(cache.get(track.id())); // nothing cached, so turning the setting on works at once
        sidecar.text = PLAIN;
        assertEquals("LRC file", offline.resolve(track, new CancelToken()).source());
        sidecar.text = null;
        cache.put(track.id(), new LyricsCacheStore.Entry(SYNCED, "LRCLIB", now + 1));
        assertEquals("LRCLIB", offline.resolve(track, new CancelToken()).source());
        assertEquals(0, remote.calls);
    }

    @Test public void brokenLocalSourceDoesNotStopTheLookup() {
        embedded.error = new IOException("EOF");
        sidecar.text = null;
        remote.text = SYNCED;
        assertEquals("LRCLIB", resolve().source());
    }

    @Test public void runtimeExceptionInLocalSourceIsAMiss() {
        LyricsSource boom = (t, c) -> {
            throw new IllegalStateException("bug");
        };
        LyricsResolver r = new LyricsResolver(Collections.singletonList(boom), remote, cache, () -> now);
        remote.text = SYNCED;
        assertNotNull(r.resolve(track, new CancelToken()));
    }

    @Test public void cancelledBeforeStartReturnsNullAndTouchesNothing() {
        CancelToken token = new CancelToken();
        token.cancel();
        assertNull(resolver.resolve(track, token));
        assertEquals(0, embedded.calls + sidecar.calls + remote.calls);
    }

    @Test public void cancelledDuringRemoteReturnsNullAndIsNotCached() {
        CancelToken token = new CancelToken();
        LyricsSource cancelling = (t, c) -> {
            token.cancel();
            return new LyricsSource.Found(SYNCED, "LRCLIB");
        };
        List<LyricsSource> none = Collections.emptyList();
        LyricsResolver r = new LyricsResolver(none, cancelling, cache, () -> now);
        assertNull(r.resolve(track, token));
        assertNull(cache.get(track.id()));
    }

    @Test public void cancelledFailureIsNotCachedAsError() {
        CancelToken token = new CancelToken();
        LyricsSource failing = (t, c) -> {
            token.cancel();
            throw new IOException("Socket closed");
        };
        LyricsResolver r = new LyricsResolver(Collections.emptyList(), failing, cache, () -> now);
        assertNull(r.resolve(track, token));
        assertNull(cache.get(track.id()));
    }

    @Test public void persianSidecarSurvivesTheWholePipeline() {
        sidecar.text = "[00:01.00]سلام\n[00:03.00]دنیا";
        Lyrics l = resolve();
        assertEquals("سلام", l.lines().get(0).text());
        assertEquals(1, l.activeLineIndex(3_500) );
    }
}
