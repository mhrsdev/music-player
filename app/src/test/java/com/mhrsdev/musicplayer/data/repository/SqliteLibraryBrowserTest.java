package com.mhrsdev.musicplayer.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


import android.content.Context;
import android.os.Looper;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.PlayStatsQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.model.TrackDetails;
import com.mhrsdev.musicplayer.domain.model.YearCount;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Smart lists, years, batch playlist edits and details on a real database (inline executor). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SqliteLibraryBrowserTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private DbHelper helper;
    private SqliteLibraryBrowser browser;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DbHelper.NAME);
        helper = new DbHelper(context);
        Executor inline = Runnable::run;
        browser = new SqliteLibraryBrowser(helper, inline, new ChangeNotifier(), null);
        TrackQueries tracks = new TrackQueries(helper.getWritableDatabase());
        tracks.upsert(track("1", "Alpha", 1999, 100), 1);
        tracks.upsert(track("2", "Beta", 2005, 200), 1);
        tracks.upsert(track("3", "Gamma", 2005, 300), 1);
        tracks.upsert(track("4", "Delta", 0, 400), 1);
        PlayStatsQueries stats = new PlayStatsQueries(helper.getWritableDatabase());
        stats.recordPlay("2", 1000);
        stats.recordPlay("2", 2000);
        stats.recordPlay("1", 3000);
    }

    @After public void tearDown() {
        helper.close();
    }

    private static Track track(String id, String title, int year, long addedAt) {
        return new Track(id, title, "ar", "Artist", "al" + id, "Album " + id, 180_000, null, 1,
                "content://media/" + id, false, false, year, 1, addedAt);
    }

    private static <T> T observed(LiveData<T> live) {
        live.observeForever(v -> { });
        return live.getValue();
    }

    private static List<String> ids(List<Track> tracks) {
        List<String> out = new ArrayList<>();
        for (Track t : tracks) out.add(t.id());
        return out;
    }

    @Test public void smartListsComeFromPlayStats() {
        assertEquals(List.of("2", "1"), ids(observed(browser.getMostPlayed())));
        assertEquals(List.of("4", "3"), ids(observed(browser.getNeverPlayed())));
        assertEquals(Integer.valueOf(2), observed(browser.getPlayCounts()).get("2"));
    }

    @Test public void yearsSkipUnknownAndCount() {
        List<YearCount> years = observed(browser.getYears());
        assertEquals(List.of(new YearCount(2005, 2), new YearCount(1999, 1)), years);
        assertEquals(2, observed(browser.getYearTracks(2005)).size());
    }

    @Test public void addAllSkipsDuplicatesAndRestoreKeepsPosition() {
        PlaylistQueries playlists = new PlaylistQueries(helper.getWritableDatabase());
        String id = playlists.create("Mix", 1);
        int[] added = new int[2];
        browser.addAllToPlaylist(id, List.of("1", "2"), n -> added[0] = n);
        browser.addAllToPlaylist(id, List.of("2", "3"), n -> added[1] = n);
        ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle();
        assertEquals(2, added[0]);
        assertEquals(1, added[1]);
        assertEquals(List.of("1", "2", "3"), playlists.orderedTrackIds(id));
        playlists.removeTrack(id, "2");
        browser.restoreToPlaylist(id, "2", 1);
        assertEquals(List.of("1", "2", "3"), playlists.orderedTrackIds(id));
    }

    @Test public void detailsCarryPlayCountAndForgetRemovesRows() {
        Track t = observed(browser.getMostPlayed()).get(0);
        TrackDetails[] got = new TrackDetails[1];
        browser.loadDetails(t, d -> got[0] = d);
        ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle();
        assertEquals(2, got[0].playCount());
        browser.forgetTracks(List.of("2"));
        assertTrue(ids(observed(browser.getMostPlayed())).equals(List.of("1")));
    }
}
