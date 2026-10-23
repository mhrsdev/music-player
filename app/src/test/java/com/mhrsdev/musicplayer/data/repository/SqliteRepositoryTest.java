package com.mhrsdev.musicplayer.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.data.scanner.MediaStoreScanner;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
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

/** The repository + home feed on a real database, with an inline executor so LiveData updates are synchronous. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SqliteRepositoryTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private DbHelper helper;
    private ChangeNotifier notifier;
    private SqliteLibraryRepository repo;
    private LocalHomeFeedSource home;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DbHelper.NAME);
        helper = new DbHelper(context);
        Executor inline = Runnable::run;
        notifier = new ChangeNotifier();
        repo = new SqliteLibraryRepository(helper, inline, notifier,
                new MediaStoreScanner(context, helper, new SettingsStore(context)));
        home = new LocalHomeFeedSource(helper, inline, notifier);
        TrackQueries tracks = new TrackQueries(helper.getWritableDatabase());
        tracks.upsert(track("1", "Alpha", "al1", 100), 1);
        tracks.upsert(track("2", "Beta", "al2", 200), 1);
    }

    @After public void tearDown() {
        helper.close();
    }

    private static Track track(String id, String title, String albumId, long addedAt) {
        return new Track(id, title, "ar", "Artist", albumId, "Album " + albumId, 180_000, null, 1,
                "content://media/" + id, false, false, 0, 1, addedAt);
    }

    private static <T> T observed(LiveData<T> live) {
        live.observeForever(v -> { });
        return live.getValue();
    }

    @Test public void listsLoadAndReactToWrites() {
        LiveData<List<Track>> liked = repo.getLikedTracks();
        assertTrue(observed(liked).isEmpty());
        repo.toggleLike("2");
        assertEquals(1, liked.getValue().size());
        assertTrue(liked.getValue().get(0).liked());
        repo.toggleLike("2");
        assertTrue(liked.getValue().isEmpty());
    }

    @Test public void changesWhileInactiveAreSeenOnNextObserve() {
        LiveData<List<Track>> liked = repo.getLikedTracks();
        repo.toggleLike("1"); // nobody observes yet
        assertEquals(1, observed(liked).size());
    }

    @Test public void historyAndPlayCountsMoveToTheTop() {
        LiveData<List<Track>> history = repo.getHistory();
        assertTrue(observed(history).isEmpty());
        repo.recordPlay("1");
        repo.recordPlay("2");
        repo.recordPlay("1");
        assertEquals("1", history.getValue().get(0).id());
        assertEquals(2, history.getValue().size());
    }

    @Test public void playlistLifecycle() {
        LiveData<List<Playlist>> playlists = repo.getPlaylists();
        assertTrue(observed(playlists).isEmpty());
        repo.createPlaylist("  ", null);
        String id = playlists.getValue().get(0).id();
        assertEquals("New playlist", playlists.getValue().get(0).name());
        LiveData<List<Track>> members = repo.getPlaylistTracks(id);
        observed(members);
        repo.addToPlaylist(id, "2");
        repo.addToPlaylist(id, "1");
        assertEquals("2", members.getValue().get(0).id());
        repo.moveInPlaylist(id, 0, 1);
        assertEquals("1", members.getValue().get(0).id());
        assertEquals(2, playlists.getValue().get(0).trackCount());
        repo.removeFromPlaylist(id, "1");
        assertEquals(1, members.getValue().size());
        repo.renamePlaylist(id, "Road");
        assertEquals("Road", observed(repo.getPlaylist(id)).name());
        repo.deletePlaylist(id);
        assertNull(observed(repo.getPlaylist(id)));
        assertTrue(playlists.getValue().isEmpty());
    }

    @Test public void searchHonoursFilterAndBlankQuery() {
        assertTrue(observed(repo.search("   ", SearchFilter.ALL)).isEmpty());
        List<SearchResult> all = observed(repo.search("alpha", SearchFilter.ALL));
        assertEquals(1, all.size());
        assertEquals(SearchResult.Kind.TRACK, all.get(0).kind());
        assertTrue(observed(repo.search("alpha", SearchFilter.ALBUMS)).isEmpty());
        assertEquals(2, observed(repo.search("artist", SearchFilter.SONGS)).size());
        assertEquals(1, observed(repo.search("artist", SearchFilter.ARTISTS)).size());
    }

    @Test public void albumsArtistsAndUnknownIds() {
        assertEquals(2, observed(repo.getAlbums()).size());
        assertEquals(1, observed(repo.getArtists()).size());
        assertNotNull(observed(repo.getAlbum("al1")));
        assertNull(observed(repo.getAlbum("missing")));
        assertEquals(1, observed(repo.getAlbumTracks("al1")).size());
        assertEquals("2", observed(repo.getRecentlySaved()).get(0).id());
    }

    @Test public void homeFeedFollowsLibraryAndHistory() {
        List<HomeSection> before = observed(home.getSections());
        assertEquals("new-releases", before.get(0).id());
        assertFalse(before.stream().anyMatch(s -> s.id().equals("quick-picks")));
        repo.recordPlay("1");
        assertTrue(home.getSections().getValue().stream().anyMatch(s -> s.id().equals("quick-picks")));
    }
}
