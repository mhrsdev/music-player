package com.mhrsdev.musicplayer.data.artwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Finds the cover for an artwork URI, whatever Android version or tagging the file has. The scanner stores
 * {@code content://media/external/audio/albumart/<albumId>} (or {@code .../audio/media/<id>} for untagged albums), and
 * on Android 10+ the former often cannot be opened, so for those the chain is: (0) open the URI as-is (still works on
 * some devices), then for the album's tracks in track order: (a) {@code ContentResolver.loadThumbnail} (API 29+),
 * (b) the picture embedded in the file, (c) {@code cover.jpg} / {@code folder.jpg} / ... next to the file when the
 * path is readable. The first hit wins; no hit means the caller draws its gradient placeholder. Other schemes (file://,
 * android.resource://) are opened directly.
 *
 * <p>Blocking: call from a background thread. Misses are remembered (bounded, per URI) so a cover-less album is
 * probed once, not on every bind; {@link #forgetMisses()} gives them another chance after a rescan or memory trim.
 */
public final class ArtworkSource {
    /** Tracks of one album probed before giving up. */
    static final int MAX_TRACKS_PROBED = 5;
    private static final String[] FOLDER_NAMES = {"cover", "folder", "front", "album", "albumart"};
    private static final String[] FOLDER_EXTENSIONS = {".jpg", ".jpeg", ".png"};

    /** A track's audio URI plus its file path (null when MediaStore hides it). */
    public record TrackRef(Uri uri, @Nullable String path) { }

    /** Everything that touches Android services; faked in tests. */
    interface Backend {
        @Nullable InputStream open(Uri uri);
        /** {@code ContentResolver.loadThumbnail} on API 29+, null elsewhere or on failure. */
        @Nullable Bitmap thumbnail(Uri audio, int sizePx);
        @Nullable byte[] embeddedPicture(Uri audio);
        /** First tracks of an album in track order. */
        List<TrackRef> albumTracks(long albumId, int limit);
        List<TrackRef> track(long audioId);
        /** Opens cover.jpg / folder.jpg ... beside {@code path}, null when none is readable. */
        @Nullable InputStream folderImage(String path);
    }

    /** Opens a fresh stream each call (bounds pass, then pixel pass). */
    private interface Opener {
        @Nullable InputStream open() throws IOException;
    }

    private static ArtworkSource instance;

    private final Backend backend;
    private final LruCache<String, Boolean> misses = new LruCache<>(512);
    private final LruCache<String, TrackRef> winners = new LruCache<>(256);

    ArtworkSource(Backend backend) {
        this.backend = backend;
    }

    public static synchronized ArtworkSource get(Context context) {
        if (instance == null) instance = new ArtworkSource(new AndroidArtworkBackend(context.getApplicationContext()));
        return instance;
    }

    /** @param sizePx wanted size of the shorter side (the result may be somewhat larger: power-of-two downsampling) */
    @Nullable
    public Bitmap load(String uriString, int sizePx, Bitmap.Config config) {
        if (misses.get(uriString) != null) return null;
        Bitmap bitmap = resolve(uriString, sizePx, config);
        if (bitmap == null) misses.put(uriString, Boolean.TRUE);
        return bitmap;
    }

    public void forgetMisses() {
        misses.evictAll();
    }

    @Nullable
    private Bitmap resolve(String s, int sizePx, Bitmap.Config config) {
        Uri uri = Uri.parse(s);
        boolean albumArt = s.contains("/audio/albumart/");
        boolean audio = s.contains("/audio/media/");
        if (!albumArt && !audio) return decode(() -> backend.open(uri), sizePx, config);
        if (albumArt) {
            Bitmap legacy = decode(() -> backend.open(uri), sizePx, config);
            if (legacy != null) return legacy;
        }
        long id = idOf(uri);
        if (id <= 0) return null;
        TrackRef winner = winners.get(s);
        if (winner != null) {
            Bitmap known = fromTrack(winner, sizePx, config);
            if (known != null) return known;
        }
        List<TrackRef> tracks = albumArt ? backend.albumTracks(id, MAX_TRACKS_PROBED) : backend.track(id);
        for (TrackRef track : tracks) {
            if (track.equals(winner)) continue;
            Bitmap bitmap = fromTrack(track, sizePx, config);
            if (bitmap != null) {
                winners.put(s, track);
                return bitmap;
            }
        }
        return null;
    }

    @Nullable
    private Bitmap fromTrack(TrackRef track, int sizePx, Bitmap.Config config) {
        Bitmap thumb = backend.thumbnail(track.uri(), sizePx);
        if (thumb != null) {
            Bitmap small = config == Bitmap.Config.RGB_565 && thumb.getConfig() != config ? thumb.copy(config, false) : null;
            if (small == null) return thumb;
            thumb.recycle();
            return small;
        }
        byte[] picture = backend.embeddedPicture(track.uri());
        if (picture != null) {
            Bitmap bitmap = decode(() -> new ByteArrayInputStream(picture), sizePx, config);
            if (bitmap != null) return bitmap;
        }
        if (track.path() != null) return decode(() -> backend.folderImage(track.path()), sizePx, config);
        return null;
    }

    @Nullable
    private static Bitmap decode(Opener opener, int sizePx, Bitmap.Config config) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = opener.open()) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0) return null;
            o.inSampleSize = sampleSize(o.outWidth, o.outHeight, sizePx);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = config;
            try (InputStream in = opener.open()) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, o);
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null; // missing file, no permission, corrupt picture
        }
    }

    /** Largest power of two that keeps the shorter side at or above {@code target}. */
    public static int sampleSize(int w, int h, int target) {
        int s = 1;
        if (w <= 0 || h <= 0 || target <= 0) return 1;
        int shorter = Math.min(w, h);
        while (shorter / (s * 2) >= target) s *= 2;
        return s;
    }

    private static long idOf(Uri uri) {
        try {
            return Long.parseLong(uri.getLastPathSegment());
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    static String[] folderCandidates(String path) {
        int slash = path.lastIndexOf('/');
        String dir = slash < 0 ? "" : path.substring(0, slash + 1);
        String[] out = new String[FOLDER_NAMES.length * FOLDER_EXTENSIONS.length];
        int i = 0;
        for (String name : FOLDER_NAMES) for (String ext : FOLDER_EXTENSIONS) out[i++] = dir + name + ext;
        return out;
    }
}
