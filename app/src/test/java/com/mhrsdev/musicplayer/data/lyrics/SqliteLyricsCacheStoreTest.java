package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SqliteLyricsCacheStoreTest {
    private DbHelper helper;
    private long now = 1_000;
    private SqliteLyricsCacheStore store;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DbHelper.NAME);
        helper = new DbHelper(context);
        store = new SqliteLyricsCacheStore(helper, () -> now);
    }

    @After public void tearDown() {
        helper.close();
    }

    @Test public void roundTripsFoundAndNegativeEntriesAndPrunesExpiredOnPut() {
        store.put("a", new LyricsCacheStore.Entry("[00:01.00]hi", "LRCLIB", 5_000));
        store.put("b", new LyricsCacheStore.Entry(null, null, 1_500));
        assertEquals("[00:01.00]hi", store.get("a").text());
        assertEquals("LRCLIB", store.get("a").source());
        assertNull(store.get("b").text());
        assertEquals(1_500, store.get("b").expiresAtMs());

        now = 2_000; // b has expired; the next write removes it
        store.put("c", new LyricsCacheStore.Entry("x", "LRCLIB", 9_000));
        assertNull(store.get("b"));
        assertNotNull(store.get("a"));
    }
}
