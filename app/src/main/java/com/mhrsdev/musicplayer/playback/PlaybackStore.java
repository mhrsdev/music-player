package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
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
import java.util.Collections;
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
 *
 * <p>Queue window (C1, D32): a 10k-track queue as 10k MediaItems costs ~10-20 MB and ~400 ms of main thread at every
 * start (Android 17's per-app memory limiter kills outliers). The player holds only a window of {@link #RADIUS} items
 * either side of the playing one; the rest stays as {@link Track}s in {@link #before()} / {@link #after()} (views of one
 * immutable list, no copy) and is turned into items when playback nears the window edge ({@link #extend}), the queue is
 * edited or shuffle / repeat-all need the whole queue ({@link #hydrateAll}). The saved queue and index are always the
 * whole queue. An external controller that replaces the queue ({@link #noteExternalEdit}) drops the hidden rest.
 */
@OptIn(markerClass = UnstableApi.class)
class PlaybackStore implements Player.Listener {
    private static final String FILE = "playback_state";
    private static final String QUEUE_FILE = "queue.bin";
    private static final String K_INDEX = "index", K_POSITION = "position",
            K_SHUFFLE = "shuffle", K_REPEAT = "repeat", K_FROM = "from";
    private static final long QUEUE_SAVE_DELAY_MS = 1_000L;
    /** Items kept in the player on each side of the playing one; the window grows by this much near its edge. */
    static final int RADIUS = 200;
    /** Playback this close to a window edge (that has hidden items behind it) extends the window. */
    static final int EDGE = 50;

    /** Single writer, thread exits when idle: writes stay ordered, nothing lingers between saves. */
    private static final ThreadPoolExecutor WRITER = new com.mhrsdev.musicplayer.diag.SafeExecutor(1, 1, 10, TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(), r -> new Thread(r, "queue-store"), com.mhrsdev.musicplayer.BuildConfig.DEBUG);
    static { WRITER.allowCoreThreadTimeOut(true); }

    private final SharedPreferences prefs;
    private final AtomicFile queueFile;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveQueueTask = this::saveQueue;
    @Nullable private ExoPlayer player;
    private boolean queueDirty;
    /** Queue items not in the player: the ones before its first item and after its last (main thread only). */
    private List<Track> before = Collections.emptyList(), after = Collections.emptyList();
    /** Bumped whenever {@link #before}/{@link #after} change, so a background hydration can tell it went stale. */
    private int epoch;
    private boolean hydrating;
    private volatile boolean externalEdit;

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

    /** Loads the persisted queue (a window of it) into {@code target}, paused and not prepared, and starts listening. */
    void restoreAndAttach(ExoPlayer target) {
        player = target;
        List<Track> tracks = readQueueFile();
        if (!tracks.isEmpty()) {
            target.setShuffleModeEnabled(prefs.getBoolean(K_SHUFFLE, false));
            target.setRepeatMode(prefs.getInt(K_REPEAT, Player.REPEAT_MODE_OFF));
            show(PlaybackState.frozen(tracks), Math.max(0, Math.min(prefs.getInt(K_INDEX, 0), tracks.size() - 1)),
                    Math.max(0L, prefs.getLong(K_POSITION, 0L)));
        }
        queueDirty = false;
        target.addListener(this);
    }

    /** Hidden queue items before / after the player's window; together with the player's items they are the queue. */
    List<Track> before() {
        return before;
    }

    List<Track> after() {
        return after;
    }

    /**
     * Replaces the player's queue with {@code all} (immutable: the hidden parts are views of it) playing {@code index}
     * at {@code positionMs}: only the window around {@code index} becomes MediaItems.
     */
    void show(List<Track> all, int index, long positionMs) {
        int lo = Math.max(0, index - RADIUS), hi = Math.min(all.size(), index + RADIUS + 1);
        externalEdit = false; // an earlier, never applied "external edit" must not collapse this window
        before = all.subList(0, lo);
        after = all.subList(hi, all.size());
        epoch++;
        player.setMediaItems(items(all.subList(lo, hi)), index - lo, positionMs);
        hydrateWholeIfNeeded();
    }

    private static List<MediaItem> items(List<Track> tracks) {
        List<MediaItem> items = new ArrayList<>(tracks.size());
        for (Track t : tracks) items.add(TrackMapper.toMediaItem(t));
        return items;
    }

    /** Playback moved near a window edge that has hidden items behind it: turn the next {@link #RADIUS} into items. */
    private void extend() {
        int at = player.getCurrentMediaItemIndex(), count = player.getMediaItemCount();
        if (!after.isEmpty() && count - at <= EDGE) {
            int n = Math.min(RADIUS, after.size());
            List<Track> next = after.subList(0, n);
            after = after.subList(n, after.size());
            epoch++;
            player.addMediaItems(items(next));
        }
        if (!before.isEmpty() && at < EDGE) {
            int n = Math.min(RADIUS, before.size());
            List<Track> previous = before.subList(before.size() - n, before.size());
            before = before.subList(0, before.size() - n);
            epoch++;
            player.addMediaItems(0, items(previous));
        }
    }

    /** Everything hidden becomes items now (main thread): queue edits and shuffle need the whole queue in the player. */
    void hydrateAll() {
        if (player == null || (before.isEmpty() && after.isEmpty())) return;
        List<MediaItem> head = items(before), tail = items(after);
        before = after = Collections.emptyList();
        epoch++;
        add(tail, head);
    }

    private void add(List<MediaItem> tail, List<MediaItem> head) {
        if (!tail.isEmpty()) player.addMediaItems(tail);
        if (!head.isEmpty()) player.addMediaItems(0, head);
    }

    /** Shuffle and repeat-all move through the whole queue, so they cannot run on a window: hydrate in the background. */
    private void hydrateWholeIfNeeded() {
        if (hydrating || (before.isEmpty() && after.isEmpty())) return;
        if (!player.getShuffleModeEnabled() && player.getRepeatMode() != Player.REPEAT_MODE_ALL) return;
        hydrating = true;
        int stamp = epoch;
        List<Track> head = before, tail = after;
        WRITER.execute(() -> {
            List<MediaItem> h = items(head), t = items(tail); // the expensive part, off the main thread
            handler.post(() -> {
                hydrating = false;
                if (stamp != epoch || player == null) return; // the queue changed meanwhile: the next change re-checks
                before = after = Collections.emptyList();
                epoch++;
                add(t, h);
            });
        });
    }

    /** Appends to the hidden tail (no MediaItem needed): "add to queue" on a windowed queue. */
    void appendHidden(Track track) {
        externalEdit = false;
        List<Track> tail = new ArrayList<>(after.size() + 1);
        tail.addAll(after);
        tail.add(track);
        after = tail;
        epoch++;
        markQueueDirty();
    }

    /** An external controller (Android Auto, Assistant) is about to change the player's queue: the hidden rest is stale. */
    void noteExternalEdit() {
        externalEdit = true;
    }

    /** The app itself edits the queue now: a pending external-edit mark (one that was never applied) is void. */
    void ownEdit() {
        externalEdit = false;
    }

    /** Tests: waits until every queued disk write / background hydration has run. */
    @androidx.annotation.VisibleForTesting
    static void awaitBackground() throws Exception {
        WRITER.submit(() -> { }).get();
    }

    /**
     * The saved queue with the saved index and position, or null when there is none. Reads files only, so it may run
     * on any thread: it backs both the startup restore and {@code MediaSession.Callback.onPlaybackResumption}.
     */
    @Nullable MediaSession.MediaItemsWithStartPosition resumption() {
        List<Track> tracks = readQueueFile();
        if (tracks.isEmpty()) return null;
        List<MediaItem> items = new ArrayList<>(tracks.size());
        for (Track t : tracks) items.add(TrackMapper.toMediaItem(t));
        int index = Math.max(0, Math.min(prefs.getInt(K_INDEX, 0), items.size() - 1));
        externalEdit = true; // the caller sets these into an empty player (this may run off the main thread)
        return new MediaSession.MediaItemsWithStartPosition(items, index, Math.max(0L, prefs.getLong(K_POSITION, 0L)));
    }

    /** Cheap check (no parsing) for "is there anything a media button could resume": the queue file exists and is not empty. */
    static boolean hasSavedQueue(Context context) {
        File f = new File(context.getFilesDir(), QUEUE_FILE);
        return f.length() > 16; // magic + table size + track count is 12 bytes: anything more holds tracks
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
        if (externalEdit) { // the queue was replaced from outside: whatever was hidden no longer belongs to it
            externalEdit = false;
            before = after = Collections.emptyList();
            epoch++;
        }
        markQueueDirty();
    }

    private void markQueueDirty() {
        queueDirty = true;
        handler.removeCallbacks(saveQueueTask);
        handler.postDelayed(saveQueueTask, QUEUE_SAVE_DELAY_MS);
    }

    @Override public void onEvents(Player p, Player.Events events) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY)) extend();
        if (events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) hydrateWholeIfNeeded();
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
        List<Track> head = before, tail = after;
        WRITER.execute(() -> writeQueueFile(head, items, tail));
    }

    private void writeQueueFile(List<Track> head, List<MediaItem> items, List<Track> tail) {
        List<Track> tracks = new ArrayList<>(head.size() + items.size() + tail.size());
        tracks.addAll(head);
        for (MediaItem item : items) {
            Track t = TrackMapper.fromMediaItem(item);
            if (t != null) tracks.add(t);
        }
        tracks.addAll(tail);
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
                .putInt(K_INDEX, before.size() + Math.max(0, player.getCurrentMediaItemIndex()))
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
