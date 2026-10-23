package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import androidx.media3.common.Player;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextWrapper;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowService;

/**
 * Regression for "no media notification / lock-screen controls": playing through the real controller must end up
 * with {@link PlaybackService} in the foreground and a notification on the playback channel with title and artist.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class PlaybackNotificationTest {
    private Context app;
    private ExoPlayerController controller;
    private ServiceController<PlaybackService> service;

    /** Plays one track through the real controller and runs the service the controller asked for. */
    private StatusBarNotification playAndStartService() throws Exception {
        app = RuntimeEnvironment.getApplication();
        File wav = new File(app.getCacheDir(), "tone.wav");
        writeSilentWav(wav);
        Track track = new Track("1", "Song Title", "ar", "Artist Name", "al", "Album", 2_000L, null, 0,
                android.net.Uri.fromFile(wav).toString(), false, false, 0, 0, 0L);
        controller = new ExoPlayerController(app, null, null);
        controller.play(Collections.singletonList(track), 0, null);

        Intent started = ((ShadowContextWrapper) Shadow.extract(app)).getNextStartedService();
        assertNotNull("controller never started PlaybackService", started);
        service = Robolectric.buildService(PlaybackService.class, started);
        service.create().startCommand(0, 1);

        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification posted = null;
        for (int i = 0; i < 200 && posted == null; i++) {
            ShadowLooper.idleMainLooper();
            for (StatusBarNotification n : nm.getActiveNotifications()) posted = n;
            if (posted == null) Thread.sleep(25);
        }
        assertNotNull("no media notification posted", posted);
        return posted;
    }

    @After public void tearDown() {
        if (controller != null) controller.pause();
        if (service != null) service.destroy(); // releases the session: its id must be unique per test
    }

    private ShadowService shadow() {
        return (ShadowService) Shadow.extract(service.get());
    }

    @Test public void playingThroughControllerPostsForegroundMediaNotification() throws Exception {
        Notification n = playAndStartService().getNotification();
        assertEquals(PlaybackService.CHANNEL_ID, n.getChannelId());
        assertEquals("Song Title", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Artist Name", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertNotNull("not a media-style notification", n.extras.get(Notification.EXTRA_MEDIA_SESSION));
        assertEquals(R.drawable.ic_stat_playback, n.getSmallIcon().getResId());
        assertTrue("service not in foreground", shadow().isLastForegroundNotificationAttached());
        assertTrue(PlayerHolder.get(app).getPlayWhenReady());
        assertTrue(PlayerHolder.get(app).getPlaybackState() != Player.STATE_IDLE);
    }

    /** Swiping the app from Recents while playing must neither stop the service nor pause the player. */
    @Test public void taskRemovedWhilePlayingKeepsPlayingInForeground() throws Exception {
        playAndStartService();
        service.get().onTaskRemoved(new Intent());
        ShadowLooper.idleMainLooper();
        assertFalse("service stopped while playing", shadow().isStoppedBySelf());
        assertTrue("player paused by task removal", PlayerHolder.get(app).getPlayWhenReady());
        assertTrue(shadow().isLastForegroundNotificationAttached());
    }

    /** Swiping the app away while paused releases the service (queue and position stay persisted). */
    @Test public void taskRemovedWhilePausedStopsService() throws Exception {
        playAndStartService();
        controller.pause();
        ShadowLooper.idleMainLooper();
        service.get().onTaskRemoved(new Intent());
        ShadowLooper.idleMainLooper();
        assertTrue("service kept running while paused", shadow().isStoppedBySelf());
    }

    private static void writeSilentWav(File file) throws Exception {
        int rate = 8000, samples = rate * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVEfmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2)
                .putShort((short) 16).put("data".getBytes()).putInt(samples * 2);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(b.array());
        }
    }
}
