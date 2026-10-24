package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Previous and next are always offered and behave the same at the ends of the queue (D36). Nothing here waits on audio. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class SessionPlayerTest {
    private static final int[] SKIPS = {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM};

    private Context app;
    private ExoPlayer raw;
    private SessionPlayer session;
    private ServiceController<PlaybackService> service;

    @Before public void setUp() throws Exception {
        PlayerHolder.resetForTest();
        app = RuntimeEnvironment.getApplication();
        raw = PlayerHolder.get(app);
        session = new SessionPlayer(raw);
    }

    @After public void tearDown() throws Exception {
        if (service != null) service.destroy();
        PlayerHolder.resetForTest();
    }

    private void queue(int count, int index) {
        for (int i = 0; i < count; i++) {
            raw.addMediaItem(new MediaItem.Builder().setMediaId("t" + i)
                    .setUri("file:///nonexistent/" + i + ".wav").build());
        }
        raw.seekToDefaultPosition(index);
    }

    private void assertSkipsAvailable(String why) {
        for (int c : SKIPS) assertTrue(why + " command " + c, session.isCommandAvailable(c));
        for (int c : SKIPS) assertTrue(why + " in set " + c, session.getAvailableCommands().contains(c));
    }

    @Test public void emptyQueueOffersNoSkip() {
        for (int c : SKIPS) assertFalse(session.isCommandAvailable(c));
    }

    @Test public void skipsAreAvailableAtEveryPositionAndRepeatMode() {
        queue(3, 0);
        int[] modes = {Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE};
        for (boolean shuffle : new boolean[] {false, true}) {
            raw.setShuffleModeEnabled(shuffle);
            for (int mode : modes) {
                raw.setRepeatMode(mode);
                for (int i = 0; i < 3; i++) {
                    raw.seekToDefaultPosition(i);
                    assertSkipsAvailable("index " + i + " repeat " + mode + " shuffle " + shuffle);
                }
            }
        }
        assertEquals("other commands are untouched", raw.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
                session.isCommandAvailable(Player.COMMAND_PLAY_PAUSE));
    }

    @Test public void nextOnTheLastTrackWrapsToTheFirstKeepingThePlayState() {
        queue(3, 2);
        raw.setPlayWhenReady(true);
        assertFalse("premise: ExoPlayer has no next here", raw.hasNextMediaItem());
        session.seekToNext();
        assertEquals(0, raw.getCurrentMediaItemIndex());
        assertTrue(raw.getPlayWhenReady());
        raw.seekToDefaultPosition(2);
        raw.setPlayWhenReady(false);
        session.seekToNextMediaItem();
        assertEquals(0, raw.getCurrentMediaItemIndex());
        assertFalse("a paused player stays paused", raw.getPlayWhenReady());
    }

    @Test public void nextInTheMiddleAndWithRepeatOneStillSkips() {
        queue(3, 0);
        session.seekToNext();
        assertEquals(1, raw.getCurrentMediaItemIndex());
        raw.setRepeatMode(Player.REPEAT_MODE_ONE);
        session.seekToNext();
        assertEquals(2, raw.getCurrentMediaItemIndex());
        session.seekToNext();
        assertEquals(0, raw.getCurrentMediaItemIndex());
    }

    @Test public void singleTrackQueueRestartsOnNextAndPrevious() {
        queue(1, 0);
        raw.seekTo(0, 20_000L);
        session.seekToNext();
        assertEquals(0, raw.getCurrentMediaItemIndex());
        assertEquals(0L, raw.getCurrentPosition());
        raw.seekTo(0, 20_000L);
        session.seekToPrevious();
        assertEquals(0L, raw.getCurrentPosition());
    }

    @Test public void previousRestartsAfterThreeSecondsElseGoesBack() {
        queue(3, 1);
        raw.seekTo(1, 10_000L);
        session.seekToPrevious();
        assertEquals(1, raw.getCurrentMediaItemIndex());
        assertEquals(0L, raw.getCurrentPosition());
        raw.seekTo(1, 1_000L);
        session.seekToPreviousMediaItem();
        assertEquals(0, raw.getCurrentMediaItemIndex());
    }

    @Test public void previousOnTheFirstTrackRestartsIt() {
        queue(3, 0);
        raw.seekTo(0, 1_000L);
        session.seekToPrevious();
        assertEquals(0, raw.getCurrentMediaItemIndex());
        assertEquals(0L, raw.getCurrentPosition());
    }

    @Test public void shuffleWrapsAroundInTheShuffleOrder() {
        queue(5, 0);
        raw.setShuffleModeEnabled(true);
        int first = raw.getCurrentTimeline().getFirstWindowIndex(true);
        int last = raw.getCurrentTimeline().getLastWindowIndex(true);
        raw.seekToDefaultPosition(last);
        session.seekToNext();
        assertEquals("next on the last of the shuffle order", first, raw.getCurrentMediaItemIndex());
        session.seekToNext();
        assertEquals(raw.getCurrentTimeline().getNextWindowIndex(first, Player.REPEAT_MODE_OFF, true),
                raw.getCurrentMediaItemIndex());
    }

    /** What the system sees: the session built by the service runs on the wrapper, and the posted notification has all three buttons. */
    @Test public void sessionAndNotificationOfferPreviousPlayPauseAndNextOnTheLastTrack() throws Exception {
        queue(3, 2);
        raw.prepare();
        raw.play();
        service = Robolectric.buildService(PlaybackService.class).create().startCommand(0, 1);
        assertTrue(service.get().session().getPlayer() instanceof SessionPlayer);
        assertSkipsAvailableOn(service.get().session().getPlayer());

        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = null;
        for (int i = 0; i < 400 && n == null; i++) {
            ShadowLooper.idleMainLooper(25, TimeUnit.MILLISECONDS);
            for (StatusBarNotification sbn : nm.getActiveNotifications()) n = sbn.getNotification();
            if (n == null) Thread.sleep(10);
        }
        assertNotNull("no media notification", n);
        assertEquals("previous, play/pause, next", 3, n.actions.length);
        assertEquals("compact view shows all three", 3, n.extras.getIntArray(Notification.EXTRA_COMPACT_ACTIONS).length);
    }

    private void assertSkipsAvailableOn(Player p) {
        for (int c : SKIPS) assertTrue("session player command " + c, p.isCommandAvailable(c));
    }
}
