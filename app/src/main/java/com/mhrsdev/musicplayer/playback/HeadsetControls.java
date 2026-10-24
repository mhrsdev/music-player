package com.mhrsdev.musicplayer.playback;

import android.media.AudioDeviceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;
import com.mhrsdev.musicplayer.domain.model.HeadsetAction;
import com.mhrsdev.musicplayer.domain.model.HeadsetConfig;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Everything a headset, earbud or car kit can do to the shared player, decided here instead of by Media3's stock
 * media-button handling (which only knows "double tap = next", and silently drops a key when ExoPlayer withholds the
 * matching command, e.g. NEXT on the last track). Main thread only.
 *
 * <ul>
 *   <li>The hook / play-pause key is the one button single-button headsets and earbuds have: clicks are counted in a
 *       short window and mapped by {@link HeadsetConfig} (single, double, triple, long press).</li>
 *   <li>Dedicated keys (play, pause, stop, next, previous, fast-forward, rewind) act at once and are not remapped.</li>
 *   <li>Playing on an empty player restores the saved queue first.</li>
 *   <li>As a {@link Player.Listener}: remembers a pause caused by unplugging / interruptions, to honour "resume after
 *       call" and, via {@link #onDevicesAdded}, "resume when the headset connects".</li>
 * </ul>
 * No timers beyond the one click-window callback, no polling.
 */
@OptIn(markerClass = UnstableApi.class)
public class HeadsetControls implements Player.Listener {
    static final long CLICK_WINDOW_MS = 350L;
    /** A pause caused by unplugging is resumed on reconnect only within this time. */
    static final long RESUME_WINDOW_MS = 10 * 60_000L;
    static final long SEEK_STEP_MS = 10_000L;

    @Nullable private static volatile Consumer<String> likeHandler;

    /** Wired by the app container: what the LIKE gesture does with the current track id. */
    public static void setLikeHandler(@Nullable Consumer<String> handler) {
        likeHandler = handler;
    }

    private final Player player;
    private final Supplier<HeadsetConfig> config;
    private final Supplier<MediaSession.MediaItemsWithStartPosition> saved;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable resolveClicks = this::resolveClicks;
    private int clicks;
    private boolean longPressHandled;
    private long pausedByDisconnectAt = -1L;
    private boolean suppressedByFocus;

    /**
     * @param config read at every decision, so a change in Settings applies at once
     * @param saved the persisted queue ({@code null} supplier result = nothing to resume), used when the player is empty
     */
    public HeadsetControls(Player player, Supplier<HeadsetConfig> config,
            Supplier<MediaSession.MediaItemsWithStartPosition> saved) {
        this.player = player;
        this.config = config;
        this.saved = saved;
    }

    public void release() {
        handler.removeCallbacks(resolveClicks);
    }

    // --- pure decisions (unit-tested) -----------------------------------------------------------

    /** Resume on connect: enabled, something is queued and paused, and the pause was an unplug a moment ago. */
    static boolean shouldResumeOnConnect(HeadsetConfig c, long pausedByDisconnectAt, long now, boolean hasQueue,
            boolean playWhenReady) {
        return c.resumeOnConnect() && hasQueue && !playWhenReady && pausedByDisconnectAt >= 0
                && now - pausedByDisconnectAt <= RESUME_WINDOW_MS;
    }

    /** "Resume after phone call" off: the automatic resume after an interruption is undone. */
    static boolean shouldRepause(HeadsetConfig c, boolean wasSuppressedByFocus) {
        return wasSuppressedByFocus && !c.resumeAfterCall();
    }

    /** Outputs a person plugs in or pairs (not the speaker / earpiece / HDMI the system adds on its own). */
    static boolean isHeadsetType(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID -> true;
            default -> false;
        };
    }

    // --- keys -----------------------------------------------------------------------------------

    /** True when the key was one of ours (down, repeat and up alike), so the stock handling must not run. */
    public boolean onKeyEvent(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && e.getRepeatCount() == 0;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (first) {
                    longPressHandled = false;
                    onClick();
                } else if (down && e.getRepeatCount() == 1) {
                    onLongPress();
                }
            }
            case KeyEvent.KEYCODE_MEDIA_PLAY -> { if (first) play(); }
            case KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> { if (first) pause(); }
            case KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> { if (first) next(); }
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> { if (first) previous(); }
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) seekBy(SEEK_STEP_MS); }
            case KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) seekBy(-SEEK_STEP_MS); }
            default -> { return false; }
        }
        return true;
    }

    void onClick() {
        if (longPressHandled) return;
        clicks++;
        handler.removeCallbacks(resolveClicks);
        if (clicks >= config.get().maxClicks()) resolveClicks(); else handler.postDelayed(resolveClicks, CLICK_WINDOW_MS);
    }

    private void resolveClicks() {
        handler.removeCallbacks(resolveClicks);
        int n = clicks;
        clicks = 0;
        if (n > 0) run(config.get().forClicks(n));
    }

    void onLongPress() {
        handler.removeCallbacks(resolveClicks);
        clicks = 0;
        longPressHandled = true;
        run(config.get().longPress());
    }

    // --- actions --------------------------------------------------------------------------------

    void run(HeadsetAction action) {
        switch (action) {
            case PLAY_PAUSE -> togglePlayPause();
            case NEXT -> next();
            case PREVIOUS -> previous();
            case SEEK_FORWARD -> seekBy(SEEK_STEP_MS);
            case SEEK_BACK -> seekBy(-SEEK_STEP_MS);
            case SHUFFLE -> player.setShuffleModeEnabled(!player.getShuffleModeEnabled());
            case LIKE -> like();
            case NONE -> { }
        }
    }

    boolean isPlaying() {
        return player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlaybackState() != Player.STATE_ENDED;
    }

    void togglePlayPause() {
        if (isPlaying()) pause(); else play();
    }

    void play() {
        if (player.getMediaItemCount() == 0) {
            MediaSession.MediaItemsWithStartPosition s = saved.get();
            if (s == null) return; // nothing to resume
            player.setMediaItems(s.mediaItems, s.startIndex, s.startPositionMs);
        }
        if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
        if (player.getPlaybackState() == Player.STATE_ENDED) player.seekToDefaultPosition();
        player.play();
    }

    void pause() {
        player.pause();
    }

    /** Same as the in-app and notification buttons: the last track wraps to the first. */
    void next() {
        SessionPlayer.skipNext(player);
    }

    void previous() {
        SessionPlayer.skipPrevious(player);
    }

    void seekBy(long deltaMs) {
        if (player.getMediaItemCount() == 0) return;
        long target = Math.max(0L, player.getCurrentPosition() + deltaMs);
        long duration = player.getDuration();
        if (duration != C.TIME_UNSET && target >= duration) {
            next();
        } else {
            player.seekTo(target);
        }
    }

    private void like() {
        MediaItem item = player.getCurrentMediaItem();
        Consumer<String> h = likeHandler;
        if (item != null && h != null) h.accept(item.mediaId);
    }

    // --- interruptions and connections ---------------------------------------------------------

    @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        pausedByDisconnectAt = !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                ? SystemClock.elapsedRealtime() : -1L;
    }

    /**
     * A call, alarm or other app took the audio focus for a moment: ExoPlayer holds playback ("suppressed") and carries
     * on by itself when the focus returns. With "resume after call" off that automatic carry-on is undone.
     */
    @Override public void onPlaybackSuppressionReasonChanged(int reason) {
        if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
            suppressedByFocus = true;
        } else if (suppressedByFocus) {
            suppressedByFocus = false;
            if (shouldRepause(config.get(), true)) player.pause();
        }
    }

    /** From the service's AudioDeviceCallback. */
    public void onDevicesAdded(AudioDeviceInfo[] added) {
        boolean headset = false;
        for (AudioDeviceInfo d : added) headset |= isHeadsetType(d.getType());
        if (headset && shouldResumeOnConnect(config.get(), pausedByDisconnectAt, SystemClock.elapsedRealtime(),
                player.getMediaItemCount() > 0, player.getPlayWhenReady())) {
            pausedByDisconnectAt = -1L;
            play();
        }
    }

    /** True while a quick click run is still being counted. */
    boolean hasPendingClicks() {
        return clicks > 0;
    }
}
