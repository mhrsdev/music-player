package com.mhrsdev.musicplayer.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.mhrsdev.musicplayer.R;

/**
 * Hosts the MediaSession on the process-wide {@link PlayerHolder} player: media notification, lock screen,
 * headset / Bluetooth media buttons. It is started by {@link ExoPlayerController} (or by the system for a media
 * button) and stops itself {@link #IDLE_STOP_DELAY_MS} after playback stops, releasing the codec first; the queue
 * and position are already persisted by {@link PlaybackStore}, so the next play restores from there.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaSessionService {
    static final String CHANNEL_ID = "playback";
    static final long IDLE_STOP_DELAY_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idleStopTask = this::stopIfIdle;
    private ExoPlayer player;
    @Nullable private MediaSession session;

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
        MediaSession.Builder builder = new MediaSession.Builder(this, player)
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
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(idleStopTask);
        player.removeListener(idleWatcher);
        PlayerHolder.store(this).saveNow();
        if (session != null) {
            session.release(); // the shared player stays alive for the controller
            session = null;
        }
        super.onDestroy();
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
