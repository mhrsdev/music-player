package com.mhrsdev.musicplayer.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import androidx.core.content.IntentCompat;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.SessionError;
import androidx.media3.session.MediaSession;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.diag.Breadcrumbs;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Hosts the MediaSession on the process-wide {@link PlayerHolder} player: media notification, lock screen,
 * headset / Bluetooth media buttons. It is started by {@link ExoPlayerController} (or by the system for a media
 * button) and stops itself {@link #IDLE_STOP_DELAY_MS} after playback stops, releasing the codec first; the queue
 * and position are already persisted by {@link PlaybackStore}, so the next play restores from there.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaLibraryService {
    static final String CHANNEL_ID = "playback";
    static final long IDLE_STOP_DELAY_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idleStopTask = this::stopIfIdle;
    private ExoPlayer player;
    @Nullable private MediaLibrarySession session;
    private ListeningExecutorService browseExecutor;
    private HeadsetControls headset;
    private MediaLibrarySession.Callback callback;
    private AudioManager audioManager;
    @Nullable private ArtworkLoader bitmapLoader;

    /** Event-driven and only registered while this service lives: a headset showing up can resume paused playback. */
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            headset.onDevicesAdded(added);
        }
    };

    private final Player.Listener idleWatcher = new Player.Listener() {
        /** Diagnostics (D30): why playback paused (focus loss, noisy, end, user...), into the crash/exit log. */
        @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            Breadcrumbs.add("player " + (playWhenReady ? "play" : "pause") + " reason " + reason);
        }

        @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
            Breadcrumbs.add("player error " + error.getErrorCodeName());
        }

        /** E.g. audio focus held by a call, or output routed nowhere: playback waits silently (Android 17 hardening). */
        @Override public void onPlaybackSuppressionReasonChanged(int reason) {
            if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) Breadcrumbs.add("playback suppressed reason " + reason);
        }

        @Override public void onEvents(Player p, Player.Events events) {
            if (!events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) return;
            handler.removeCallbacks(idleStopTask);
            if (!isActive()) handler.postDelayed(idleStopTask, idleDelayMs());
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        player = PlayerHolder.get(this);
        PlaybackStore store = PlayerHolder.store(this);
        headset = new HeadsetControls(player, () -> SettingsStore.readHeadset(this), store::resumption);
        player.addListener(headset);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        // Browse queries hit SQLite: one worker thread that exits after 10 s idle.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);
        browseExecutor = MoreExecutors.listeningDecorator(pool);
        callback = new BrowseCallback(new BrowseTree(getResources(), BrowseHolder.get()), store);
        MediaLibrarySession.Builder builder = new MediaLibrarySession.Builder(this, player, callback)
                .setBitmapLoader(bitmapLoader = new ArtworkLoader(this));
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, launch,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        session = builder.build();
        // Without this nobody owns the session: onGetSession only runs when a MediaController connects, and the app
        // has none (shared in-process player), so Media3 would never post the notification or go foreground.
        addSession(session);
        // Stock notification (prev / play-pause / next from the player's commands) with our channel and small icon.
        DefaultMediaNotificationProvider provider = new DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build();
        provider.setSmallIcon(R.drawable.ic_stat_playback);
        setMediaNotificationProvider(provider);
        player.addListener(idleWatcher);
        // Android 12+: Media3 already catches the refusal (no crash); keep a trace of it for Diagnostics.
        setListener(new Listener() {
            @Override public void onForegroundServiceStartNotAllowedException() {
                Breadcrumbs.add("FGS start refused (background)");
            }
        });
        if (!isActive()) handler.postDelayed(idleStopTask, idleDelayMs());
    }

    /** For tests: the session the system sees. */
    @Nullable MediaLibrarySession session() {
        return session;
    }

    /** For tests: the session callback (browse, playback resumption, media buttons). */
    MediaLibrarySession.Callback callback() {
        return callback;
    }

    /** For tests: the button / interruption logic behind the session callback. */
    HeadsetControls headset() {
        return headset;
    }

    @Nullable @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    /** Every start (play, or the notification permission just granted) re-posts the notification: it is dropped while denied. */
    @Override public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        int result = super.onStartCommand(intent, flags, startId);
        triggerNotificationUpdate();
        return result;
    }

    @Override public void onTaskRemoved(@Nullable Intent rootIntent) {
        Breadcrumbs.add("service onTaskRemoved playing=" + player.isPlaying());
        super.onTaskRemoved(rootIntent);
    }

    /** UI gone or memory tight: drop the cached notification covers (the posted notification keeps its own). */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN && bitmapLoader != null) bitmapLoader.trim();
    }

    @Override public void onDestroy() {
        Breadcrumbs.add("service onDestroy");
        clearListener();
        handler.removeCallbacks(idleStopTask);
        player.removeListener(idleWatcher);
        player.removeListener(headset);
        headset.release();
        if (audioManager != null) audioManager.unregisterAudioDeviceCallback(deviceCallback);
        browseExecutor.shutdown();
        PlayerHolder.store(this).saveNow();
        if (session != null) {
            session.release(); // the shared player stays alive for the controller
            session = null;
        }
        super.onDestroy();
    }

    /** One minute normally; longer when "resume when the headset connects" is on, so a reconnect finds the service. */
    private long idleDelayMs() {
        return SettingsStore.readHeadset(this).resumeOnConnect() ? HeadsetControls.RESUME_WINDOW_MS : IDLE_STOP_DELAY_MS;
    }

    /**
     * Android Auto / Assistant browsing, headset buttons and playback resumption. The notification's own buttons and
     * everything else keep the stock session behaviour.
     */
    private class BrowseCallback implements MediaLibrarySession.Callback {
        private final BrowseTree tree;
        private final PlaybackStore store;

        BrowseCallback(BrowseTree tree, PlaybackStore store) {
            this.tree = tree;
            this.store = store;
        }

        /**
         * Headset / Bluetooth / wired keys (and the intent a stopped service is restarted with): handled by
         * {@link HeadsetControls}. Keys from the media notification are left to the stock handler, which must not
         * wait to count clicks.
         */
        @Override public boolean onMediaButtonEvent(MediaSession s, MediaSession.ControllerInfo controller, Intent intent) {
            if (s.isMediaNotificationController(controller)) return false;
            KeyEvent key = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent.class);
            return key != null && headset.onKeyEvent(key);
        }

        /**
         * Play with an empty player (a headset PLAY after the queue was cleared, Assistant, the system's resume
         * card): the saved queue at the saved position. Failing makes Media3 just call play().
         */
        @Override public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
                MediaSession s, MediaSession.ControllerInfo controller, boolean isForPlayback) {
            return browseExecutor.submit(() -> {
                MediaSession.MediaItemsWithStartPosition saved = store.resumption();
                if (saved == null) throw new UnsupportedOperationException("no saved queue");
                return saved;
            });
        }

        @Override public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(MediaLibrarySession s,
                MediaSession.ControllerInfo browser, @Nullable LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params));
        }

        @Override public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(MediaLibrarySession s,
                MediaSession.ControllerInfo browser, String parentId, int page, int pageSize,
                @Nullable LibraryParams params) {
            return browseExecutor.submit(() -> {
                List<MediaItem> all = tree.children(parentId);
                int from = Math.min(all.size(), Math.max(0, page) * Math.max(1, pageSize));
                int to = Math.min(all.size(), from + Math.max(1, pageSize));
                return LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params);
            });
        }

        @Override public ListenableFuture<LibraryResult<MediaItem>> onGetItem(MediaLibrarySession s,
                MediaSession.ControllerInfo browser, String mediaId) {
            return browseExecutor.submit(() -> {
                MediaItem item = tree.item(mediaId);
                return item == null ? LibraryResult.<MediaItem>ofError(SessionError.ERROR_BAD_VALUE)
                        : LibraryResult.ofItem(item, null);
            });
        }

        /** Controllers send bare media ids (no URI): resolve them to playable items; unknown ids are dropped. */
        @Override public ListenableFuture<List<MediaItem>> onAddMediaItems(MediaSession s,
                MediaSession.ControllerInfo controller, List<MediaItem> mediaItems) {
            return browseExecutor.submit(() -> {
                List<MediaItem> out = new ArrayList<>(mediaItems.size());
                for (MediaItem item : mediaItems) {
                    MediaItem resolved = item.localConfiguration != null ? item : tree.item(item.mediaId);
                    if (resolved != null && resolved.localConfiguration != null) out.add(resolved);
                }
                return out;
            });
        }
    }

    private boolean isActive() {
        return player.getPlayWhenReady() && (player.getPlaybackState() == Player.STATE_READY
                || player.getPlaybackState() == Player.STATE_BUFFERING);
    }

    private void stopIfIdle() {
        if (isActive()) return;
        if (player.getPlaybackState() != Player.STATE_IDLE) player.stop(); // frees codec + audio track, keeps queue
        stopSelf();
    }
}
