package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Survives process death: the queue (JSON), current index/position, shuffle/repeat and the "playing from" label
 * live in one small SharedPreferences file. Written on changes only, never on position ticks: the (larger) queue
 * is debounced after timeline changes; index/position/modes are saved on item transitions, pause, mode changes
 * and by {@link #saveNow()} (UI went to background / service stopping). Restore happens paused and unprepared.
 */
final class PlaybackStore implements Player.Listener {
    private static final String FILE = "playback_state";
    private static final String K_QUEUE = "queue", K_INDEX = "index", K_POSITION = "position",
            K_SHUFFLE = "shuffle", K_REPEAT = "repeat", K_FROM = "from";
    private static final long QUEUE_SAVE_DELAY_MS = 1_000L;

    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveQueueTask = this::saveQueue;
    @Nullable private ExoPlayer player;
    private boolean queueDirty;

    PlaybackStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Nullable String playingFrom() {
        return prefs.getString(K_FROM, null);
    }

    void setPlayingFrom(@Nullable String from) {
        prefs.edit().putString(K_FROM, from).apply();
    }

    /** Loads the persisted queue into {@code target} (paused, not prepared) and starts listening to it. */
    void restoreAndAttach(ExoPlayer target) {
        player = target;
        List<Track> tracks = parseQueue(prefs.getString(K_QUEUE, null));
        if (!tracks.isEmpty()) {
            List<MediaItem> items = new ArrayList<>(tracks.size());
            for (Track t : tracks) items.add(TrackMapper.toMediaItem(t));
            int index = Math.max(0, Math.min(prefs.getInt(K_INDEX, 0), items.size() - 1));
            target.setMediaItems(items, index, Math.max(0L, prefs.getLong(K_POSITION, 0L)));
            target.setShuffleModeEnabled(prefs.getBoolean(K_SHUFFLE, false));
            target.setRepeatMode(prefs.getInt(K_REPEAT, Player.REPEAT_MODE_OFF));
        }
        queueDirty = false;
        target.addListener(this);
    }

    /** Flushes everything now (cheap when nothing changed). */
    void saveNow() {
        if (player == null) return;
        handler.removeCallbacks(saveQueueTask);
        if (queueDirty) saveQueue();
        saveState();
    }

    @Override public void onEvents(Player p, Player.Events events) {
        if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
            queueDirty = true;
            handler.removeCallbacks(saveQueueTask);
            handler.postDelayed(saveQueueTask, QUEUE_SAVE_DELAY_MS);
        }
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED) || (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !p.isPlaying())) {
            saveState();
        }
    }

    private void saveQueue() {
        if (player == null) return;
        queueDirty = false;
        List<Track> tracks = new ArrayList<>(player.getMediaItemCount());
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            Track t = TrackMapper.fromMediaItem(player.getMediaItemAt(i));
            if (t != null) tracks.add(t);
        }
        prefs.edit().putString(K_QUEUE, toJson(tracks)).apply();
    }

    private void saveState() {
        if (player == null) return;
        long position = player.getCurrentPosition();
        prefs.edit()
                .putInt(K_INDEX, Math.max(0, player.getCurrentMediaItemIndex()))
                .putLong(K_POSITION, position == C.TIME_UNSET ? 0L : position)
                .putBoolean(K_SHUFFLE, player.getShuffleModeEnabled())
                .putInt(K_REPEAT, player.getRepeatMode())
                .apply();
    }

    // --- JSON codec (package-private for tests) -----------------------------------------------

    static String toJson(List<Track> tracks) {
        JSONArray array = new JSONArray();
        try {
            for (Track t : tracks) {
                array.put(new JSONObject()
                        .put("id", t.id()).put("title", t.title())
                        .put("artistId", t.artistId()).put("artist", t.artistName())
                        .put("albumId", t.albumId()).put("album", t.albumTitle())
                        .put("duration", t.durationMs()).put("artwork", t.artworkUri())
                        .put("seed", t.placeholderSeed()).put("uri", t.mediaUri())
                        .put("explicit", t.explicit()).put("liked", t.liked())
                        .put("year", t.year()).put("number", t.trackNumber()).put("added", t.addedAtMs()));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e); // only thrown for non-finite numbers, which Tracks never hold
        }
        return array.toString();
    }

    /** Never throws: corrupt data simply restores an empty queue. */
    static List<Track> parseQueue(@Nullable String json) {
        List<Track> tracks = new ArrayList<>();
        if (json == null) return tracks;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String uri = o.optString("uri", "");
                if (uri.isEmpty()) continue;
                tracks.add(new Track(o.getString("id"), o.optString("title"), o.optString("artistId"),
                        o.optString("artist"), o.optString("albumId"), o.optString("album"),
                        o.optLong("duration"), nullable(o, "artwork"), o.optInt("seed"), uri,
                        o.optBoolean("explicit"), o.optBoolean("liked"), o.optInt("year"), o.optInt("number"),
                        o.optLong("added")));
            }
        } catch (JSONException e) {
            tracks.clear();
        }
        return tracks;
    }

    @Nullable private static String nullable(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }
}
