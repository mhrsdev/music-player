package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import com.mhrsdev.musicplayer.domain.model.Track;
import android.util.AtomicFile;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Survives process death: current index/position, shuffle/repeat and the "playing from" label live in one small
 * SharedPreferences file; the queue (which can hold 10k tracks) lives in its own compact binary file, so it is
 * never parsed into the preferences map at process start. Written on changes only, never on position ticks: the
 * queue is debounced after playlist edits (not after the per-track duration updates ExoPlayer reports as timeline
 * changes) and encoded on a background thread from an immutable snapshot of the MediaItems; index/position/modes
 * are saved on item transitions, pause, mode changes and by {@link #saveNow()} (UI went to background / service
 * stopping). Restore happens paused and unprepared.
 */
final class PlaybackStore implements Player.Listener {
    private static final String FILE = "playback_state";
    private static final String QUEUE_FILE = "queue.bin";
    private static final String K_INDEX = "index", K_POSITION = "position",
            K_SHUFFLE = "shuffle", K_REPEAT = "repeat", K_FROM = "from";
    private static final long QUEUE_SAVE_DELAY_MS = 1_000L;

    /** Single writer, thread exits when idle: writes stay ordered, nothing lingers between saves. */
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(), r -> new Thread(r, "queue-store"));
    static { WRITER.allowCoreThreadTimeOut(true); }

    private final SharedPreferences prefs;
    private final AtomicFile queueFile;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveQueueTask = this::saveQueue;
    @Nullable private ExoPlayer player;
    private boolean queueDirty;

    PlaybackStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        queueFile = new AtomicFile(new File(context.getFilesDir(), QUEUE_FILE));
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
        List<Track> tracks = readQueueFile();
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

    /** Only real edits (add / remove / move / replace): duration updates arrive as SOURCE_UPDATE on every track change. */
    @Override public void onTimelineChanged(Timeline timeline, int reason) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return;
        queueDirty = true;
        handler.removeCallbacks(saveQueueTask);
        handler.postDelayed(saveQueueTask, QUEUE_SAVE_DELAY_MS);
    }

    @Override public void onEvents(Player p, Player.Events events) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED) || (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !p.isPlaying())) {
            saveState();
        }
    }

    /** Main thread: only copies the item references (cheap); decoding and disk I/O run on {@link #WRITER}. */
    private void saveQueue() {
        if (player == null) return;
        queueDirty = false;
        int count = player.getMediaItemCount();
        List<MediaItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) items.add(player.getMediaItemAt(i));
        WRITER.execute(() -> writeQueueFile(items));
    }

    private void writeQueueFile(List<MediaItem> items) {
        List<Track> tracks = new ArrayList<>(items.size());
        for (MediaItem item : items) {
            Track t = TrackMapper.fromMediaItem(item);
            if (t != null) tracks.add(t);
        }
        java.io.FileOutputStream file = null;
        try {
            file = queueFile.startWrite();
            OutputStream out = new BufferedOutputStream(file, 64 * 1024);
            encode(tracks, out);
            out.flush();
            queueFile.finishWrite(file);
        } catch (IOException e) {
            if (file != null) queueFile.failWrite(file); // old queue file stays intact
        }
    }

    private List<Track> readQueueFile() {
        try (InputStream in = new BufferedInputStream(queueFile.openRead(), 64 * 1024)) {
            return decode(in);
        } catch (IOException e) {
            return new ArrayList<>(); // first run (no file) or unreadable
        }
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

    // --- binary codec (package-private for tests) -----------------------------------------------
    // Layout: MAGIC, string table (count, UTF strings), track count, then per track 8 table refs (-1 = null) and the
    // numbers. Repeated artist / album / artwork strings are stored once, so 10k tracks stay around 1.5 MB.

    private static final int MAGIC = 0x4D515101; // "MQQ" + format version 1

    static void encode(List<Track> tracks, OutputStream os) throws IOException {
        Map<String, Integer> table = new HashMap<>();
        for (Track t : tracks) writeTrack(t, table, null); // pass 1 fills the table
        String[] strings = new String[table.size()];
        for (Map.Entry<String, Integer> e : table.entrySet()) strings[e.getValue()] = e.getKey();
        DataOutputStream out = new DataOutputStream(os);
        out.writeInt(MAGIC);
        out.writeInt(strings.length);
        for (String s : strings) out.writeUTF(s);
        out.writeInt(tracks.size());
        for (Track t : tracks) writeTrack(t, table, out);
        out.flush();
    }

    private static void writeTrack(Track t, Map<String, Integer> table, @Nullable DataOutputStream out) throws IOException {
        int[] refs = {ref(table, t.id()), ref(table, t.title()), ref(table, t.artistId()), ref(table, t.artistName()),
                ref(table, t.albumId()), ref(table, t.albumTitle()), ref(table, t.artworkUri()), ref(table, t.mediaUri())};
        if (out == null) return;
        for (int r : refs) out.writeInt(r);
        out.writeLong(t.durationMs());
        out.writeInt(t.placeholderSeed());
        out.writeByte((t.explicit() ? 1 : 0) | (t.liked() ? 2 : 0));
        out.writeInt(t.year());
        out.writeInt(t.trackNumber());
        out.writeLong(t.addedAtMs());
    }

    private static int ref(Map<String, Integer> table, @Nullable String s) {
        if (s == null) return -1;
        Integer index = table.get(s);
        if (index == null) table.put(s, index = table.size());
        return index;
    }

    /** Never throws: corrupt data simply restores an empty queue. Tracks without a media URI are dropped. */
    static List<Track> decode(InputStream is) {
        List<Track> tracks = new ArrayList<>();
        try {
            DataInputStream in = new DataInputStream(is);
            if (in.readInt() != MAGIC) return tracks;
            String[] strings = new String[in.readInt()];
            for (int i = 0; i < strings.length; i++) strings[i] = in.readUTF();
            int count = in.readInt();
            tracks = new ArrayList<>(Math.min(count, 100_000));
            for (int i = 0; i < count; i++) {
                String id = str(strings, in.readInt()), title = str(strings, in.readInt()),
                        artistId = str(strings, in.readInt()), artist = str(strings, in.readInt()),
                        albumId = str(strings, in.readInt()), album = str(strings, in.readInt()),
                        artwork = str(strings, in.readInt()), uri = str(strings, in.readInt());
                long duration = in.readLong();
                int seed = in.readInt(), flags = in.readByte(), year = in.readInt(), number = in.readInt();
                long added = in.readLong();
                if (id == null || uri == null || uri.isEmpty()) continue;
                tracks.add(new Track(id, nz(title), nz(artistId), nz(artist), nz(albumId), nz(album), duration,
                        artwork, seed, uri, (flags & 1) != 0, (flags & 2) != 0, year, number, added));
            }
        } catch (IOException | RuntimeException e) {
            tracks.clear();
        }
        return tracks;
    }

    @Nullable private static String str(String[] strings, int ref) {
        return ref < 0 ? null : strings[ref]; // out-of-range ref -> RuntimeException -> empty queue
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }
}
