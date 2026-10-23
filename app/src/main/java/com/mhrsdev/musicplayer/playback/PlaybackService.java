package com.mhrsdev.musicplayer.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
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

    private final Player.Listener idleWatcher = new Player.Listener() {
        @Override public void onEvents(Player p, Player.Events events) {
            if (!events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) return;
            handler.removeCallbacks(idleStopTask);
            if (!isActive()) handler.postDelayed(idleStopTask, IDLE_STOP_DELAY_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        player = PlayerHolder.get(this);
        // Browse queries hit SQLite: one worker thread that exits after 10 s idle.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);
        browseExecutor = MoreExecutors.listeningDecorator(pool);
        MediaLibrarySession.Builder builder = new MediaLibrarySession.Builder(this, player,
                new BrowseCallback(new BrowseTree(getResources(), BrowseHolder.get())))
                .setBitmapLoader(new ArtworkLoader(this));
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, launch,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        session = builder.build();
        // Stock notification (prev / play-pause / next from the player's commands) with our channel and small icon.
        DefaultMediaNotificationProvider provider = new DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build();
        provider.setSmallIcon(R.drawable.ic_stat_playback);
        setMediaNotificationProvider(provider);
        player.addListener(idleWatcher);
        if (!isActive()) handler.postDelayed(idleStopTask, IDLE_STOP_DELAY_MS);
    }

    @Nullable @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(idleStopTask);
        player.removeListener(idleWatcher);
        browseExecutor.shutdown();
        PlayerHolder.store(this).saveNow();
        if (session != null) {
            session.release(); // the shared player stays alive for the controller
            session = null;
        }
        super.onDestroy();
    }

    /** Android Auto / Assistant browsing; the system UI and media buttons keep using the default session behaviour. */
    private final class BrowseCallback implements MediaLibrarySession.Callback {
        private final BrowseTree tree;

        BrowseCallback(BrowseTree tree) {
            this.tree = tree;
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
