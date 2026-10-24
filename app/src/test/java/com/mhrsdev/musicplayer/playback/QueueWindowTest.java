package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.media3.exoplayer.ExoPlayer;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * C1 (D32): a 10k-track queue is restored / started as a window of MediaItems around the playing one, the rest stays as
 * Tracks and joins when playback nears the edge, on edits, or when shuffle / repeat-all need it. The queue the UI sees,
 * its order, the saved index and the saved file are always the whole queue.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class QueueWindowTest {
    private static final int N = 10_000;
    private static final int WINDOW = 2 * PlaybackStore.RADIUS + 1;

    private Context app;
    private List<Track> tracks;
    private PlaybackStore store;
    private ExoPlayer player;
    private ExoPlayerController controller;

    @Before public void setUp() throws Exception {
        PlayerHolder.resetForTest();
        app = RuntimeEnvironment.getApplication();
        tracks = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            tracks.add(new Track("id" + i, "Song " + i, "ar" + i % 50, "Artist " + i % 50, "al" + i % 90, "Album " + i % 90,
                    120_000L, null, i % 9, "file:///nonexistent/" + i + ".mp3", false, false, 2000, i % 12, i));
        }
        player = PlayerHolder.get(app);
        store = PlayerHolder.store(app);
        controller = new ExoPlayerController(app, null, null);
    }

    @After public void tearDown() throws Exception {
        PlayerHolder.resetForTest();
    }

    private static void idle() {
        ShadowLooper.idleMainLooper();
    }

    private PlaybackState state() {
        idle();
        return controller.getState().getValue();
    }

    private void showAt(int index) {
        store.show(PlaybackState.frozen(tracks), index, 0L);
        idle();
    }

    @Test public void startingAHugeQueueBuildsOnlyAWindow() {
        long t0 = System.nanoTime();
        showAt(5_000);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PERF queue window: show(10k) " + ms + " ms, player items " + player.getMediaItemCount());
        assertTrue("items in the player: " + player.getMediaItemCount(), player.getMediaItemCount() <= WINDOW);
        PlaybackState s = state();
        assertEquals("the queue screen sees every track", N, s.queue().size());
        assertEquals(5_000, s.currentIndex());
        assertEquals("id5000", s.currentTrack().id());
        assertEquals("id0", s.queue().get(0).id());
        assertEquals("id9999", s.queue().get(N - 1).id());
    }

    @Test public void nextAndPreviousCrossTheWindowEdgesInOrder() {
        showAt(5_000);
        for (int step = 1; step <= 450; step++) {
            assertTrue("next exists at step " + step, player.hasNextMediaItem());
            player.seekToNextMediaItem();
            idle();
            assertEquals("id" + (5_000 + step), state().currentTrack().id());
            assertEquals(5_000 + step, state().currentIndex());
        }
        assertTrue("the window followed playback", player.getMediaItemCount() > WINDOW);
        for (int step = 1; step <= 900; step++) {
            assertTrue("previous exists at step " + step, player.hasPreviousMediaItem());
            player.seekToPreviousMediaItem();
            idle();
            assertEquals(5_450 - step, state().currentIndex());
        }
        assertEquals("id4550", state().currentTrack().id());
        assertEquals(N, state().queue().size());
    }

    @Test public void theWindowStopsAtTheRealEndsOfTheQueue() {
        showAt(N - 3);
        player.seekToNextMediaItem();
        player.seekToNextMediaItem();
        idle();
        assertEquals(N - 1, state().currentIndex());
        assertFalse(player.hasNextMediaItem());
        assertTrue(store.after().isEmpty());
        showAt(2);
        player.seekToPreviousMediaItem();
        player.seekToPreviousMediaItem();
        idle();
        assertEquals(0, state().currentIndex());
        assertFalse(player.hasPreviousMediaItem());
    }

    @Test public void restoreReadsTheWholeQueueButFillsOnlyAWindowAtTheSavedIndex() throws Exception {
        PlayerHolder.resetForTest();
        try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), "queue.bin"))) {
            PlaybackStore.encode(tracks, out);
        }
        app.getSharedPreferences("playback_state", Context.MODE_PRIVATE).edit()
                .putInt("index", 9_000).putLong("position", 1_234L).commit();
        long t0 = System.nanoTime();
        player = PlayerHolder.get(app);
        store = PlayerHolder.store(app);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PERF queue window: restore(10k) " + ms + " ms, player items " + player.getMediaItemCount());
        assertTrue(player.getMediaItemCount() <= WINDOW);
        controller = new ExoPlayerController(app, null, null);
        PlaybackState s = state();
        assertEquals(N, s.queue().size());
        assertEquals(9_000, s.currentIndex());
        assertEquals("id9000", s.currentTrack().id());
        assertEquals(1_234L, player.getCurrentPosition());
    }

    @Test public void theSavedFileAndIndexAreTheWholeQueue() throws Exception {
        showAt(5_000);
        for (int i = 0; i < 100; i++) player.seekToNextMediaItem();
        idle();
        store.saveNow();
        PlaybackStore.awaitBackground();
        try (FileInputStream in = new FileInputStream(new File(app.getFilesDir(), "queue.bin"))) {
            assertEquals("every track, in order", tracks, PlaybackStore.decode(in));
        }
        assertEquals(5_100, app.getSharedPreferences("playback_state", Context.MODE_PRIVATE).getInt("index", -1));
    }

    @Test public void editsAnywhereInAHugeQueueKeepTheOrder() {
        showAt(5_000);
        controller.removeFromQueue(3);          // outside the window: the whole queue joins the player first
        idle();
        PlaybackState s = state();
        assertEquals(N - 1, s.queue().size());
        assertEquals("id4", s.queue().get(3).id());
        assertEquals("the playing track is still the playing track", "id5000", s.currentTrack().id());
        assertEquals(4_999, s.currentIndex());
        controller.moveQueueItem(0, N - 2);
        idle();
        assertEquals("id1", state().queue().get(0).id());
        assertEquals("id0", state().queue().get(N - 2).id());
        assertEquals("id5000", state().currentTrack().id());
    }

    @Test public void addToQueueAtTheEndOfAWindowedQueueNeedsNoItems() {
        showAt(100);
        Track extra = named("extra");
        controller.addToQueue(extra);
        PlaybackState s = state();
        assertEquals(N + 1, s.queue().size());
        assertEquals("extra", s.queue().get(N).id());
        assertTrue("still a window", player.getMediaItemCount() <= WINDOW);
        assertEquals("id100", s.currentTrack().id());
    }

    @Test public void playNextInsertsAfterThePlayingTrack() {
        showAt(5_000);
        controller.playNext(named("next"));
        PlaybackState s = state();
        assertEquals("next", s.queue().get(5_001).id());
        assertEquals("id5001", s.queue().get(5_002).id());
        assertEquals(N + 1, s.queue().size());
    }

    @Test public void skippingFarOutsideTheWindowMovesTheWindow() {
        showAt(100);
        controller.skipTo(9_000);
        PlaybackState s = state();
        assertEquals(9_000, s.currentIndex());
        assertEquals("id9000", s.currentTrack().id());
        assertEquals(N, s.queue().size());
        assertTrue(player.getMediaItemCount() <= WINDOW);
    }

    @Test public void shuffleQueueKeepsEveryTrackAndTheCurrentOne() {
        showAt(5_000);
        controller.shuffleQueue();
        PlaybackState s = state();
        assertEquals(N, s.queue().size());
        assertEquals(N, new HashSet<>(ids(s.queue())).size());
        assertEquals("id5000", s.currentTrack().id());
        assertEquals("up to the playing track the order is untouched", "id4999", s.queue().get(4_999).id());
        assertTrue(s.canUndoShuffle());
        controller.undoShuffleQueue();
        assertEquals(ids(tracks), ids(state().queue()));
    }

    @Test public void shuffleModeNeedsTheWholeQueueAndGetsItInTheBackground() throws Exception {
        showAt(5_000);
        player.setShuffleModeEnabled(true);
        idle();
        PlaybackStore.awaitBackground();
        idle();
        assertEquals(N, player.getMediaItemCount());
        assertTrue(store.before().isEmpty() && store.after().isEmpty());
        assertEquals("id5000", state().currentTrack().id());
    }

    @Test public void aQueueReplacedByAnExternalControllerDropsTheHiddenRest() {
        showAt(5_000);
        store.noteExternalEdit();
        List<Track> three = tracks.subList(0, 3);
        List<androidx.media3.common.MediaItem> items = new ArrayList<>();
        for (Track t : three) items.add(TrackMapper.toMediaItem(t));
        player.setMediaItems(items, 1, 0L);
        PlaybackState s = state();
        assertEquals(3, s.queue().size());
        assertEquals(1, s.currentIndex());
        assertTrue(store.before().isEmpty() && store.after().isEmpty());
    }

    @Test public void smallQueuesAreNotWindowed() {
        store.show(PlaybackState.frozen(tracks.subList(0, 50)), 10, 0L);
        idle();
        assertEquals(50, player.getMediaItemCount());
        assertTrue(store.before().isEmpty() && store.after().isEmpty());
        assertEquals(50, state().queue().size());
        assertEquals(10, state().currentIndex());
    }

    private Track named(String id) {
        return new Track(id, id, "ar", "Artist", "al", "Album", 120_000L, null, 0, "file:///nonexistent/" + id + ".mp3",
                false, false, 2000, 1, 0L);
    }

    private static List<String> ids(List<Track> list) {
        List<String> out = new ArrayList<>(list.size());
        for (Track t : list) out.add(t.id());
        return out;
    }
}
