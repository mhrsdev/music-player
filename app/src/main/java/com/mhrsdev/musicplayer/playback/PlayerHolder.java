package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;

/**
 * The one ExoPlayer of the process, shared by {@link ExoPlayerController} (UI side) and {@link PlaybackService}
 * (notification / media buttons). Both live in the same process, so no IPC or async MediaController handshake is
 * needed. It is created on first use (main thread), restored from {@link PlaybackStore} in the paused state, and
 * kept for the life of the process; the service only releases codecs with {@code stop()} when idle.
 */
@OptIn(markerClass = UnstableApi.class)
final class PlayerHolder {
    private static ExoPlayer player;
    private static PlaybackStore store;

    private PlayerHolder() {}

    /** Main thread only. */
    static synchronized ExoPlayer get(Context context) {
        if (player == null) {
            Context app = context.getApplicationContext();
            // Local files: small, bounded buffers (a few seconds, <= 4 MB) instead of the streaming defaults.
            LoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMsForLocalPlayback(2_000, 8_000, 500, 1_000)
                    .setTargetBufferBytes(4 * 1024 * 1024)
                    .setBackBuffer(0, false)
                    .build();
            // Audio only: no video/text/metadata renderers to initialise or hold memory for.
            ExoPlayer p = new ExoPlayer.Builder(app,
                    (handler, video, audio, text, metadata) -> new Renderer[] {
                            new MediaCodecAudioRenderer(app, MediaCodecSelector.DEFAULT, handler, audio)})
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(app,
                            new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)))
                    .setLoadControl(loadControl)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(), /* handleAudioFocus= */ true)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL) // CPU wake lock only while actually playing local files
                    .build();
            store = new PlaybackStore(app);
            store.restoreAndAttach(p);
            player = p;
        }
        return player;
    }

    /**
     * Tests only: a player shared between tests keeps stale loader state and its callbacks race the looper reset;
     * one left by an earlier test class (MainActivity builds one) never leaves BUFFERING under the next test's looper.
     * Robolectric can kill the playback thread while it unregisters the audio-becoming-noisy receiver ("Receiver not
     * registered"); release() then waits for a reply that never comes (its timeout rides the frozen fake clock), so it
     * runs on a helper thread and is abandoned after 2 s instead of hanging the whole test task.
     */
    @VisibleForTesting
    static synchronized void resetForTest() throws InterruptedException {
        ExoPlayer old = player;
        player = null;
        store = null;
        if (old == null) return;
        Thread release = new Thread(old::release, "release-test-player");
        release.setDaemon(true);
        release.start();
        release.join(2_000);
    }

    static synchronized PlaybackStore store(Context context) {
        get(context);
        return store;
    }
}
