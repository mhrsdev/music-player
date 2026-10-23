package com.mhrsdev.musicplayer.data.scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import com.mhrsdev.musicplayer.FakeMediaStore;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Scanner staleness driven by an injected MediaStore generation. Owns its single DbHelper (never a second one on the
 * same file, and no AppContainer touching the database), so nothing can contend for the write lock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ScannerStalenessTest {
    private final Context ctx = RuntimeEnvironment.getApplication();
    private DbHelper helper;

    @Before public void setUp() {
        FakeMediaStore.install();
        FakeMediaStore.put(1, "One");
        helper = new DbHelper(ctx);
    }

    @After public void tearDown() {
        helper.close();
    }

    @Test public void generationDecidesStaleness() {
        long[] generation = {5};
        MediaStoreScanner scanner = new MediaStoreScanner(ctx, helper, new SettingsStore(ctx), () -> generation[0]);
        assertTrue("never scanned", scanner.isStale());
        assertTrue(scanner.scan().ok());
        assertFalse("nothing changed: the check is free", scanner.isStale());
        generation[0] = 6; // a download bumped MediaStore
        assertTrue(scanner.isStale());
    }

    @Test public void generationIsStampedFromBeforeTheScan() {
        long[] ticking = {10};
        MediaStoreScanner scanner = new MediaStoreScanner(ctx, helper, new SettingsStore(ctx), () -> ticking[0]++);
        assertTrue(scanner.scan().ok()); // reads 10 before scanning, the next read is already 11
        assertTrue("a change during the scan is not lost", scanner.isStale());
    }
}
