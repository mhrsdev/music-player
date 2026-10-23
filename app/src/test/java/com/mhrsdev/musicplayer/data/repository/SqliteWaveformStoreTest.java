package com.mhrsdev.musicplayer.data.repository;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.data.waveform.WaveformMath;
import com.mhrsdev.musicplayer.domain.model.Track;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The SQLite {@code WaveformStore} adapter: master-envelope blobs pass through, edited files invalidate. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SqliteWaveformStoreTest {
    private DbHelper helper;
    private TrackQueries tracks;
    private SqliteWaveformStore store;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DbHelper.NAME);
        helper = new DbHelper(context);
        tracks = new TrackQueries(helper.getWritableDatabase());
        tracks.upsert(track("1"), 10);
        store = new SqliteWaveformStore(helper);
    }

    @After public void tearDown() {
        helper.close();
    }

    private static Track track(String id) {
        return new Track(id, "T" + id, "ar", "Artist", "al", "Album", 180_000, null, 1,
                "content://media/" + id, false, false, 0, 1, 100);
    }

    private static byte[] envelope() {
        float[] values = new float[WaveformMath.MASTER_BUCKETS];
        for (int i = 0; i < values.length; i++) values[i] = i / (float) values.length;
        return WaveformMath.encode(values);
    }

    @Test public void roundTripsTheMasterBlobUnchanged() {
        assertNull(store.get("1", "fp"));
        byte[] blob = envelope();
        store.put("1", "fp", blob);
        assertArrayEquals(blob, store.get("1", "other-fingerprint"));
        assertEquals(WaveformMath.MASTER_BUCKETS, store.get("1", "fp").length);
    }

    @Test public void emptyBlobIsTheNegativeCache() {
        store.put("1", "fp", new byte[0]);
        assertEquals(0, store.get("1", "fp").length);
    }

    @Test public void editedFileDropsTheBlobAndUnknownTrackIsIgnored() {
        store.put("1", "fp", envelope());
        tracks.upsert(track("1"), 11); // a rescan saw a newer modified time
        assertNull(store.get("1", "fp"));
        store.put("ghost", "fp", envelope());
        assertNull(store.get("ghost", "fp"));
    }
}
