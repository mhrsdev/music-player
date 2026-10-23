package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.provider.MediaStore;
import androidx.lifecycle.Observer;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * "I downloaded a song and had to Clear Data": the library must follow MediaStore while the process stays alive.
 * Drives the real container (SQLite, scanner, repository, LiveData) against a fake MediaStore provider.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RefreshFlowTest {
    private final AtomicReference<List<Track>> tracks = new AtomicReference<>();
    private AppContainer container;

    @Before public void setUp() throws Exception {
        FakeMediaStore.install();
        Object shadow = Class.forName("org.robolectric.Shadows").getMethod("shadowOf", android.app.Application.class)
                .invoke(null, RuntimeEnvironment.getApplication());
        shadow.getClass().getMethod("grantPermissions", String[].class)
                .invoke(shadow, (Object) new String[] {Manifest.permission.READ_MEDIA_AUDIO});
        container = ((MusicPlayerApp) RuntimeEnvironment.getApplication()).getContainer();
        Observer<List<Track>> o = tracks::set; // like Home / Library: observed while visible
        container.library.getAllTracks().observeForever(o);
    }

    /** Pumps the main looper until the observed library satisfies {@code ok} (the io thread is real). */
    private boolean await(Predicate<List<Track>> ok) throws Exception {
        for (int i = 0; i < 150; i++) {
            ShadowLooper.idleMainLooper();
            List<Track> t = tracks.get();
            if (t != null && ok.test(t)) return true;
            Thread.sleep(20);
        }
        return false;
    }

    private boolean awaitCount(int n) throws Exception {
        return await(t -> t.size() == n);
    }

    /** Lets the io thread run whatever is queued, for "nothing must happen" assertions. */
    private void settle() throws Exception {
        Thread.sleep(300);
        ShadowLooper.idleMainLooper();
    }

    private static void passTime(long seconds) {
        ShadowLooper.idleMainLooper(seconds, TimeUnit.SECONDS);
    }

    private void firstScanOfThree() throws Exception {
        FakeMediaStore.put(1, "One");
        FakeMediaStore.put(2, "Two");
        FakeMediaStore.put(3, "Three");
        container.onAppForeground();
        assertTrue("first scan", awaitCount(3));
    }

    @Test public void everyForegroundPicksUpNewDownloadsNotOnlyTheFirstOfTheProcess() throws Exception {
        firstScanOfThree();

        FakeMediaStore.put(4, "Fresh download", 200_000, true, false, 2000 /* same modified time as the others */, 1000);
        container.onAppForeground(); // within the throttle window: nothing
        settle();
        assertEquals(3, tracks.get().size());

        passTime(21);
        container.onAppForeground(); // process still alive, app comes back to the front
        assertTrue("new file shows without Clear Data", awaitCount(4));
    }

    @Test public void pendingDownloadAppearsOnlyOnceFinished() throws Exception {
        firstScanOfThree();
        FakeMediaStore.put(5, "Downloading", 0, true, true, 2000, 10); // pending, not indexed yet
        container.libraryAdmin().rescan(null);
        settle();
        assertEquals(3, tracks.get().size());

        FakeMediaStore.put(5, "Downloading", 180_000, true, false, 2001, 4_000_000);
        container.libraryAdmin().rescan(null);
        assertTrue(awaitCount(4));
    }

    @Test public void downloadWithoutMusicFlagIsStillMusicButRingtonesAreNot() throws Exception {
        firstScanOfThree();
        FakeMediaStore.put(6, "No flag", 180_000, false, false, 2000, 1000); // IS_MUSIC=0, .mp3
        container.libraryAdmin().rescan(null);
        assertTrue(awaitCount(4));
    }

    @Test public void changedSizeWithEqualTimestampUpdatesTheRow() throws Exception {
        firstScanOfThree();
        FakeMediaStore.put(2, "Two (re-downloaded)", 180_000, true, false, 2000, 2222);
        container.onAppForeground();
        passTime(21);
        container.onAppForeground();
        assertTrue(await(t -> t.stream().anyMatch(x -> x.title().equals("Two (re-downloaded)"))));
        assertEquals(3, tracks.get().size());
    }

    @Test public void deletedFileDisappearsAndItsLikeComesBackWithIt() throws Exception {
        firstScanOfThree();
        container.library.toggleLike("3");
        assertTrue(await(t -> t.stream().anyMatch(x -> x.id().equals("3") && x.liked())));

        FakeMediaStore.remove(3);
        container.libraryAdmin().rescan(null);
        assertTrue(awaitCount(2));

        FakeMediaStore.put(3, "Three");
        container.libraryAdmin().rescan(null);
        assertTrue(await(t -> t.stream().anyMatch(x -> x.id().equals("3") && x.liked())));
    }

    @Test public void mediaStoreChangeWhileVisibleTriggersADebouncedScan() throws Exception {
        firstScanOfThree();
        FakeMediaStore.put(7, "Seen by observer");
        RuntimeEnvironment.getApplication().getContentResolver()
                .notifyChange(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null);
        passTime(1);
        settle();
        assertEquals("debounced: not yet", 3, tracks.get().size());
        passTime(3);
        assertTrue(awaitCount(4));
    }

    @Test public void noObserverOnceTheAppIsInTheBackground() throws Exception {
        firstScanOfThree();
        container.onAppBackground();
        FakeMediaStore.put(8, "Ignored");
        RuntimeEnvironment.getApplication().getContentResolver()
                .notifyChange(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null);
        passTime(5);
        settle();
        assertEquals(3, tracks.get().size());
    }

    @Test public void forceRescanReportsWhatChanged() throws Exception {
        firstScanOfThree();
        FakeMediaStore.put(9, "New");
        AtomicReference<ScanResult> result = new AtomicReference<>();
        container.libraryAdmin().rescan(result::set);
        assertTrue(awaitCount(4));
        for (int i = 0; i < 50 && result.get() == null; i++) { ShadowLooper.idleMainLooper(); Thread.sleep(20); }
        assertNotNull(result.get());
        assertTrue(result.get().ok());
        assertEquals(1, result.get().added());
        assertEquals(0, result.get().updated());
        assertFalse(result.get().removed() > 0);
    }

    @Test public void generationDecidesStalenessAndIsStampedFromBeforeTheScan() {
        android.content.Context ctx = RuntimeEnvironment.getApplication();
        long[] generation = {5};
        com.mhrsdev.musicplayer.data.scanner.MediaStoreScanner scanner =
                new com.mhrsdev.musicplayer.data.scanner.MediaStoreScanner(ctx,
                        new com.mhrsdev.musicplayer.data.db.DbHelper(ctx),
                        new com.mhrsdev.musicplayer.data.settings.SettingsStore(ctx), () -> generation[0]);
        FakeMediaStore.put(1, "One");
        assertTrue("never scanned", scanner.isStale());
        assertTrue(scanner.scan().ok());
        assertFalse("nothing changed: the check is free", scanner.isStale());
        generation[0] = 6; // a download bumped MediaStore
        assertTrue(scanner.isStale());

        long[] ticking = {10};
        com.mhrsdev.musicplayer.data.scanner.MediaStoreScanner racing =
                new com.mhrsdev.musicplayer.data.scanner.MediaStoreScanner(ctx,
                        new com.mhrsdev.musicplayer.data.db.DbHelper(ctx),
                        new com.mhrsdev.musicplayer.data.settings.SettingsStore(ctx), () -> ticking[0]++);
        racing.scan(); // reads 10 before scanning
        assertTrue("a change during the scan is not lost", racing.isStale());
    }
}
