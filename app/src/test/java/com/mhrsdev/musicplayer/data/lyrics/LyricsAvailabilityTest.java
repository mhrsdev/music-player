package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.player.LyricsAvailability;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

/** Lyrics-button availability: local sources, cache hit / miss / negative, online toggle, track switches. */
public class LyricsAvailabilityTest {
    private static class Src implements LyricsSource {
        @Nullable String text;
        int calls;

        @Nullable @Override public Found load(Track track, CancelToken cancel) throws IOException {
            calls++;
            return text == null ? null : new Found(text, "x");
        }
    }

    private final Track a = TestTracks.queen();
    private final Track b = TestTracks.track("t2", "Other", "X", "Y", 200_000, null);
    private final Src embedded = new Src(), sidecar = new Src(), remote = new Src();
    private final InMemoryLyricsCacheStore cache = new InMemoryLyricsCacheStore();
    private long now = 5_000_000;
    private boolean online = true;
    private final LyricsResolver resolver =
            new LyricsResolver(Arrays.asList(embedded, sidecar), remote, cache, () -> now, () -> online);

    private LyricsAvailability check(Track t) {
        Lyrics l = resolver.resolve(t, new CancelToken());
        return LyricsAvailability.of(l, t);
    }

    @Test public void noTrackIsUnknown_loadingIsChecking() {
        assertEquals(LyricsAvailability.UNKNOWN, LyricsAvailability.of(null, null));
        assertEquals(LyricsAvailability.CHECKING, LyricsAvailability.of(null, a));
        // still holding the previous track's answer: checking, never the old state
        assertEquals(LyricsAvailability.CHECKING, LyricsAvailability.of(Lyrics.none(a.id()), b));
    }

    @Test public void localSidecarIsAvailableWithoutNetwork() {
        sidecar.text = "[00:01.00]hi";
        assertEquals(LyricsAvailability.AVAILABLE, check(a));
        assertEquals(0, remote.calls);
    }

    @Test public void remoteNegativeIsCachedAndNotAskedAgain() {
        assertEquals(LyricsAvailability.NONE, check(a));
        assertEquals(1, remote.calls);
        now += 60_000;
        assertEquals(LyricsAvailability.NONE, check(a));
        assertEquals(1, remote.calls); // negative cache hit
        now += LyricsResolver.NOT_FOUND_TTL_MS;
        check(a);
        assertEquals(2, remote.calls); // expired: one new lookup
    }

    @Test public void remotePositiveCacheHit() {
        remote.text = "[00:01.00]la";
        assertEquals(LyricsAvailability.AVAILABLE, check(a));
        online = false; // cached answer still used, no request
        assertEquals(LyricsAvailability.AVAILABLE, check(a));
        assertEquals(1, remote.calls);
    }

    @Test public void onlineOffAndNoLocalIsNone_noRequest() {
        online = false;
        remote.text = "[00:01.00]la";
        assertEquals(LyricsAvailability.NONE, check(a));
        assertEquals(0, remote.calls);
    }

    @Test public void eachTrackIsCheckedOnItsOwn() {
        remote.text = "[00:01.00]la";
        assertEquals(LyricsAvailability.AVAILABLE, check(a));
        remote.text = null;
        assertEquals(LyricsAvailability.NONE, check(b));
        assertEquals(LyricsAvailability.AVAILABLE, check(a)); // a's positive cache, b's negative do not mix
    }
}
