package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.domain.model.HeadsetAction;
import com.mhrsdev.musicplayer.domain.model.HeadsetConfig;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
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
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextWrapper;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowService;

/**
 * Headset / Bluetooth media buttons end to end on the real service, session and ExoPlayer: key events in, player
 * state out. Robolectric has no platform media-session dispatch, so keys are fed to the exact object the session
 * callback hands them to ({@code HeadsetControls}) and, for the restart path, through the service's own
 * ACTION_MEDIA_BUTTON intent handling.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class HeadsetButtonsTest {
    private Context app;
    private List<Track> tracks;
    private ServiceController<PlaybackService> service;

    @Before public void setUp() throws Exception {
        PlayerHolder.resetForTest();
        app = RuntimeEnvironment.getApplication();
        File wav = new File(app.getCacheDir(), "tone.wav");
        writeSilentWav(wav); // 2 minutes: tracks must not end by themselves while a test runs (timing-dependent otherwise)
        tracks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tracks.add(new Track("t" + i, "Song " + i, "ar", "Artist", "al", "Album", 120_000L, null, 0,
                    android.net.Uri.fromFile(wav).toString(), false, false, 0, 0, 0L));
        }
        setConfig(HeadsetConfig.DEFAULT);
    }

    @After public void tearDown() throws Exception {
        if (service != null) service.destroy();
        PlayerHolder.resetForTest();
    }

    private void setConfig(HeadsetConfig c) {
        new SettingsStore(app).setHeadset(c);
    }

    /** A playing queue of three tracks and a running service, like after pressing play in the app. */
    private HeadsetControls startPlaying(int index) {
        ExoPlayerController controller = new ExoPlayerController(app, null, null);
        controller.play(tracks, index, null);
        service = Robolectric.buildService(PlaybackService.class).create();
        settle();
        return service.get().headset();
    }

    private ExoPlayer player() {
        return PlayerHolder.get(app);
    }

    private static void settle() {
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
    }

    private static void press(HeadsetControls h, int keyCode) {
        h.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        h.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    // --- the toggle key and its gestures --------------------------------------------------------

    @Test public void hookAndPlayPauseSingleClickTogglesPlayback() {
        HeadsetControls h = startPlaying(0);
        assertTrue(player().getPlayWhenReady());
        press(h, KeyEvent.KEYCODE_HEADSETHOOK);
        assertTrue("single click must wait for a possible second click", player().getPlayWhenReady());
        settle();
        assertFalse(player().getPlayWhenReady());
        press(h, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        settle();
        assertTrue(player().getPlayWhenReady());
    }

    @Test public void doubleClickIsNextAndTripleClickIsPrevious() {
        HeadsetControls h = startPlaying(1);
        press(h, KeyEvent.KEYCODE_HEADSETHOOK);
        press(h, KeyEvent.KEYCODE_HEADSETHOOK);
        settle();
        assertEquals(2, player().getCurrentMediaItemIndex());
        assertTrue("double click must not toggle", player().getPlayWhenReady());
        // "previous" restarts the track instead when more than 3 s in, and a slow runner can be past that by now: pause and rewind.
        player().pause();
        player().seekTo(0L);
        for (int i = 0; i < 3; i++) press(h, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertEquals("third click resolves at once", 1, player().getCurrentMediaItemIndex());
    }

    @Test public void gesturesFollowTheSettings() {
        setConfig(HeadsetConfig.DEFAULT.withGesture(0, HeadsetAction.NONE).withGesture(1, HeadsetAction.SEEK_FORWARD));
        HeadsetControls h = startPlaying(0);
        press(h, KeyEvent.KEYCODE_HEADSETHOOK);
        settle();
        assertTrue("single mapped to none", player().getPlayWhenReady());
        press(h, KeyEvent.KEYCODE_HEADSETHOOK);
        press(h, KeyEvent.KEYCODE_HEADSETHOOK);
        settle();
        assertEquals("double = seek, not next", 0, player().getCurrentMediaItemIndex());
        assertTrue("seeked about 10 s forward: " + player().getCurrentPosition(), player().getCurrentPosition() >= 10_000L);
    }

    @Test public void longPressRunsItsMappedActionOnly() {
        setConfig(HeadsetConfig.DEFAULT.withGesture(3, HeadsetAction.SHUFFLE));
        HeadsetControls h = startPlaying(0);
        h.onKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, 0));
        h.onKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, 1));
        h.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
        settle();
        assertTrue(player().getShuffleModeEnabled());
        assertTrue("the long press must not also toggle playback", player().getPlayWhenReady());
    }

    @Test public void likeGestureReportsTheCurrentTrack() {
        List<String> liked = new ArrayList<>();
        HeadsetControls.setLikeHandler(liked::add);
        setConfig(HeadsetConfig.DEFAULT.withGesture(0, HeadsetAction.LIKE));
        HeadsetControls h = startPlaying(2);
        press(h, KeyEvent.KEYCODE_HEADSETHOOK);
        settle();
        HeadsetControls.setLikeHandler(null);
        assertEquals(List.of("t2"), liked);
    }

    // --- dedicated keys -------------------------------------------------------------------------

    @Test public void dedicatedKeysActAtOnce() {
        HeadsetControls h = startPlaying(0);
        press(h, KeyEvent.KEYCODE_MEDIA_PAUSE);
        assertFalse(player().getPlayWhenReady());
        press(h, KeyEvent.KEYCODE_MEDIA_PLAY);
        assertTrue(player().getPlayWhenReady());
        press(h, KeyEvent.KEYCODE_MEDIA_NEXT);
        assertEquals(1, player().getCurrentMediaItemIndex());
        press(h, KeyEvent.KEYCODE_MEDIA_NEXT);
        assertEquals(2, player().getCurrentMediaItemIndex());
        press(h, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        assertEquals(1, player().getCurrentMediaItemIndex());
        press(h, KeyEvent.KEYCODE_MEDIA_STOP);
        assertFalse("stop pauses (keeps queue and position) so play can continue", player().getPlayWhenReady());
        assertEquals(3, player().getMediaItemCount());
        assertFalse("unknown keys are not ours", h.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)));
    }

    @Test public void fastForwardAndRewindSeekTenSeconds() {
        HeadsetControls h = startPlaying(0);
        press(h, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        long afterForward = player().getCurrentPosition();
        assertEquals(0, player().getCurrentMediaItemIndex());
        assertTrue("forward 10 s: " + afterForward, afterForward >= 10_000L);
        press(h, KeyEvent.KEYCODE_MEDIA_REWIND);
        assertTrue("back 10 s: " + player().getCurrentPosition(), player().getCurrentPosition() < afterForward - 5_000L);
    }

    @Test public void seekingPastTheEndMovesOnToTheNextTrack() {
        HeadsetControls h = startPlaying(0);
        h.seekBy(10 * 120_000L);
        assertEquals(1, player().getCurrentMediaItemIndex());
    }

    @Test public void nextOnTheLastTrackRestartsInsteadOfBeingDropped() {
        HeadsetControls h = startPlaying(2);
        assertFalse(player().hasNextMediaItem());
        press(h, KeyEvent.KEYCODE_MEDIA_NEXT);
        assertEquals(2, player().getCurrentMediaItemIndex());
        assertTrue("restarted: " + player().getCurrentPosition(), player().getCurrentPosition() < 5_000L);
        assertTrue(player().getPlayWhenReady());
    }

    @Test public void keysWorkWhilePausedAfterTheIdleStop() {
        HeadsetControls h = startPlaying(0);
        press(h, KeyEvent.KEYCODE_MEDIA_PAUSE);
        // Playback-state events after the pause may re-arm the 60 s timer: advance until it has fired.
        for (int i = 0; i < 5 && player().getPlaybackState() != Player.STATE_IDLE; i++) {
            ShadowLooper.idleMainLooper(PlaybackService.IDLE_STOP_DELAY_MS + 1_000, TimeUnit.MILLISECONDS);
        }
        assertEquals("the idle stop released the codec", Player.STATE_IDLE, player().getPlaybackState());
        press(h, KeyEvent.KEYCODE_MEDIA_PLAY);
        settle();
        assertTrue(player().getPlayWhenReady());
        assertTrue(player().getPlaybackState() != Player.STATE_IDLE);
    }

    // --- empty player: resume the saved queue -----------------------------------------------------

    private void saveQueue(int index, long positionMs) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), "queue.bin"))) {
            PlaybackStore.encode(tracks, out);
        }
        app.getSharedPreferences("playback_state", Context.MODE_PRIVATE).edit()
                .putInt("index", index).putLong("position", positionMs).commit();
    }

    @Test public void playOnAnEmptyPlayerRestoresTheSavedQueue() throws Exception {
        saveQueue(1, 500L);
        service = Robolectric.buildService(PlaybackService.class).create();
        HeadsetControls h = service.get().headset();
        player().clearMediaItems();
        assertEquals(0, player().getMediaItemCount());
        press(h, KeyEvent.KEYCODE_MEDIA_PLAY);
        settle();
        assertEquals(3, player().getMediaItemCount());
        assertEquals(1, player().getCurrentMediaItemIndex());
        assertTrue(player().getPlayWhenReady());
    }

    @Test public void sessionPlaybackResumptionReturnsTheSavedQueueAtItsPosition() throws Exception {
        saveQueue(2, 700L);
        service = Robolectric.buildService(PlaybackService.class).create();
        // What a Media3 controller's play() on an empty player does: ask the session callback for the queue.
        androidx.media3.session.MediaSession.MediaItemsWithStartPosition r =
                PlayerHolder.store(app).resumption();
        assertNotNull(r);
        assertEquals(3, r.mediaItems.size());
        assertEquals(2, r.startIndex);
        assertEquals(700L, r.startPositionMs);
        assertEquals("t2", r.mediaItems.get(2).mediaId);
    }

    /** What Media3 does for a controller's / key's play() on an empty player: it asks the session callback for the queue. */
    @Test public void sessionCallbackResumptionFeedsAnEmptyPlayer() throws Exception {
        service = Robolectric.buildService(PlaybackService.class).create();
        player().clearMediaItems(); // also resets the saved index, so save after it
        saveQueue(1, 0L);
        androidx.media3.session.MediaSession.MediaItemsWithStartPosition r = service.get().callback()
                .onPlaybackResumption(service.get().session(), null, true).get(5, TimeUnit.SECONDS);
        assertEquals(3, r.mediaItems.size());
        assertEquals(1, r.startIndex);
    }

    @Test public void sessionCallbackResumptionFailsCleanlyWithNothingSaved() throws Exception {
        service = Robolectric.buildService(PlaybackService.class).create();
        try {
            service.get().callback().onPlaybackResumption(service.get().session(), null, true).get(5, TimeUnit.SECONDS);
            org.junit.Assert.fail("expected a failure: nothing to resume");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof UnsupportedOperationException);
        }
    }

    // --- stopped service ------------------------------------------------------------------------

    @Test public void mediaButtonIntentRestartsAStoppedServiceAndPlays() throws Exception {
        saveQueue(0, 0L);
        service = Robolectric.buildService(PlaybackService.class);
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(new ComponentName(app, PlaybackService.class));
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
        service.create().withIntent(intent).startCommand(0, 1);
        for (int i = 0; i < 100 && !player().getPlayWhenReady(); i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(20);
        }
        settle();
        assertTrue("PLAY from a stopped service did not start playback", player().getPlayWhenReady());
        ShadowService shadow = (ShadowService) Shadow.extract(service.get());
        for (int i = 0; i < 100 && !shadow.isLastForegroundNotificationAttached(); i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(20);
        }
        assertTrue("service did not reach the foreground", shadow.isLastForegroundNotificationAttached());
    }

    @Test public void receiverStartsTheServiceOnlyWhenThereIsAQueueToResume() throws Exception {
        MediaButtonRestartReceiver receiver = new MediaButtonRestartReceiver();
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        ShadowContextWrapper ctx = Shadow.extract(app);

        receiver.onReceive(app, intent);
        assertNull("nothing saved: a foreground start would only crash", ctx.getNextStartedService());

        saveQueue(0, 0L);
        receiver.onReceive(app, intent);
        Intent started = ctx.getNextStartedService();
        assertNotNull("saved queue: the press must restart the service", started);
        assertEquals(PlaybackService.class.getName(), started.getComponent().getClassName());
        assertEquals(Intent.ACTION_MEDIA_BUTTON, started.getAction());
    }

    private static void writeSilentWav(File file) throws Exception {
        int rate = 8000, samples = rate * 120;
        ByteBuffer b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVEfmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2)
                .putShort((short) 16).put("data".getBytes()).putInt(samples * 2);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(b.array());
        }
    }
}
