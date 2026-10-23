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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.service.notification.StatusBarNotification;
import androidx.media3.common.Player;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.ArtworkResolver;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextWrapper;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowService;

/**
 * Regression for "no media notification / lock-screen controls": playing through the real controller must end up
 * with {@link PlaybackService} in the foreground and a notification on the playback channel with title and artist.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, application = Application.class)
public class PlaybackNotificationTest {
    private static final String MEDIA_URI = "content://media/external/audio/media/7";

    /** Provider with no art anywhere; tests override the one source they exercise. */
    private static class NoArtBackend implements ArtworkResolver.Backend {
        @Override public Bitmap thumbnail(android.net.Uri uri, int sizePx) { return null; }
        @Override public java.io.InputStream open(android.net.Uri uri) { return null; }
        @Override public byte[] embeddedPicture(android.net.Uri audioUri) { return null; }
        @Override public java.util.List<ArtworkResolver.AudioRef> albumTracks(String albumId, int limit) { return Collections.emptyList(); }
        @Override public ArtworkResolver.AudioRef audio(String audioId) { return null; }
        @Override public boolean isFile(File f) { return false; }
    }

    private Context app;
    private ExoPlayerController controller;
    private ServiceController<PlaybackService> service;

    /** Plays one track through the real controller and runs the service the controller asked for. */
    private StatusBarNotification playAndStartService() throws Exception {
        return playAndStartService(null);
    }

    private StatusBarNotification playAndStartService(String artworkUri) throws Exception {
        app = RuntimeEnvironment.getApplication();
        File wav = new File(app.getCacheDir(), "tone.wav");
        writeSilentWav(wav);
        Track track = new Track("1", "Song Title", "ar", "Artist Name", "al", "Album", 300_000L, artworkUri, 0,
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
        // Give a loaded machine time to reach READY, but BUFFERING is also "ongoing"; IDLE / ENDED would not be.
        for (int i = 0; i < 200 && PlayerHolder.get(app).getPlaybackState() != Player.STATE_READY; i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(25);
        }
        int state = PlayerHolder.get(app).getPlaybackState();
        assertTrue("player state " + state, state == Player.STATE_READY || state == Player.STATE_BUFFERING);
        return posted;
    }

    @After public void tearDown() {
        if (controller != null) controller.pause();
        if (service != null) service.destroy(); // releases the session: its id must be unique per test
    }

    private ShadowService shadow() {
        return (ShadowService) Shadow.extract(service.get());
    }

    /** A MediaStore track whose only cover is the picture embedded in the file: the notification must carry it. */
    @Test public void notificationGetsCoverFromEmbeddedPicture() throws Exception {
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        Bitmap big = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
        big.eraseColor(Color.RED);
        big.compress(Bitmap.CompressFormat.PNG, 100, png);
        ArtworkLoader.backendOverride = new NoArtBackend() {
            @Override public java.util.List<ArtworkResolver.AudioRef> albumTracks(String albumId, int limit) {
                return Collections.singletonList(new ArtworkResolver.AudioRef(android.net.Uri.parse(MEDIA_URI), null));
            }
            @Override public byte[] embeddedPicture(android.net.Uri audioUri) { return png.toByteArray(); }
        };
        try {
            playAndStartService("content://media/external/audio/albumart/3");
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            boolean cover = false;
            for (int i = 0; i < 200 && !cover; i++) { // the cover arrives asynchronously and re-posts
                ShadowLooper.idleMainLooper();
                for (StatusBarNotification sbn : nm.getActiveNotifications()) {
                    cover |= sbn.getNotification().getLargeIcon() != null;
                }
                if (!cover) Thread.sleep(25);
            }
            assertTrue("notification has no cover", cover);
        } finally {
            ArtworkLoader.backendOverride = null;
        }
    }

    @Test public void loaderProducesSmallRgb565FromEmbeddedPicture() throws Exception {
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        Bitmap big = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888);
        big.eraseColor(Color.BLUE);
        big.compress(Bitmap.CompressFormat.PNG, 100, png);
        ArtworkLoader.backendOverride = new NoArtBackend() {
            @Override public byte[] embeddedPicture(android.net.Uri audioUri) { return png.toByteArray(); }
        };
        try {
            ArtworkLoader loader = new ArtworkLoader(RuntimeEnvironment.getApplication());
            Bitmap b = loader.loadBitmap(android.net.Uri.parse(MEDIA_URI)).get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(Bitmap.Config.RGB_565, b.getConfig());
            assertTrue(Math.max(b.getWidth(), b.getHeight()) <= ArtworkLoader.MAX_PX);
            assertEquals(b, loader.loadBitmap(android.net.Uri.parse(MEDIA_URI)).get()); // second call: cache
            try {
                new ArtworkLoader(RuntimeEnvironment.getApplication()).loadBitmap(android.net.Uri.parse("file:///none.jpg")).get();
                org.junit.Assert.fail("expected no artwork");
            } catch (java.util.concurrent.ExecutionException expected) { /* Media3 shows the notification without a cover */ }
        } finally {
            ArtworkLoader.backendOverride = null;
        }
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

    /** 5 minutes of 8 kHz 8-bit mono: long enough that the track never ends while a slow CI machine runs the test. */
    private static void writeSilentWav(File file) throws Exception {
        int rate = 8000, samples = rate * 300;
        ByteBuffer b = ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + samples).put("WAVEfmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate).putShort((short) 1)
                .putShort((short) 8).put("data".getBytes()).putInt(samples);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(b.array());
        }
    }
}
