package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import com.mhrsdev.musicplayer.domain.model.NowPlaying;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.function.Consumer;

/**
 * Tells outside observers (home-screen widgets) when the track or the play/pause state changes. Event driven on the
 * shared player: no timers beyond one 200 ms coalescing post, and nothing at all for position ticks or seeks.
 */
@OptIn(markerClass = UnstableApi.class)
public class NowPlayingBridge {
    private static final long COALESCE_MS = 200L;

    private NowPlayingBridge() {}

    /** Main thread. {@code sink} is called on the main thread after each relevant change. */
    public static void attach(Context context, Consumer<NowPlaying> sink) {
        ExoPlayer player = PlayerHolder.get(context);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable push = () -> sink.accept(of(player));
        player.addListener(new Player.Listener() {
            @Override public void onEvents(Player p, Player.Events events) {
                if (!events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    return;
                }
                handler.removeCallbacks(push);
                handler.postDelayed(push, COALESCE_MS);
            }
        });
    }

    /** Current state, or null when the queue is empty. Main thread. */
    @Nullable
    public static NowPlaying snapshot(Context context) {
        return of(PlayerHolder.get(context));
    }

    @Nullable
    private static NowPlaying of(ExoPlayer p) {
        MediaItem item = p.getCurrentMediaItem();
        Track t = TrackMapper.fromMediaItem(item);
        if (t == null) return null;
        int state = p.getPlaybackState();
        boolean playing = p.getPlayWhenReady() && (state == Player.STATE_READY || state == Player.STATE_BUFFERING);
        long duration = p.getDuration() > 0 ? p.getDuration() : t.durationMs();
        return new NowPlaying(t.title(), t.artistName(), t.artworkUri(), playing, Math.max(0L, p.getCurrentPosition()),
                duration);
    }
}
