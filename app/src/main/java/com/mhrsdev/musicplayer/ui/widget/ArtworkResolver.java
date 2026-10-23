package com.mhrsdev.musicplayer.ui.widget;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Finds a cover for an artwork URI, robust on Android 10+ where {@code content://media/external/audio/albumart/<id>}
 * often does not open. Order for a track (audio media URI) or each track of an album (albumart URI):
 * <ol>
 *   <li>{@code ContentResolver.loadThumbnail} (API 29+; for an album first on {@code audio/albums/<id>})</li>
 *   <li>the legacy URI itself (works below API 29 and on many devices above)</li>
 *   <li>the picture embedded in the audio file ({@code MediaMetadataRetriever.getEmbeddedPicture})</li>
 *   <li>cover.jpg / folder.jpg / front.jpg / albumart.jpg (+ .png) next to the file, when the path is readable</li>
 * </ol>
 * Null means "no art": the caller draws the seeded gradient. Album art = the art of its first track that has any.
 * All platform access goes through {@link Backend} so the chain is testable on the JVM.
 */
public final class ArtworkResolver {

    /** A track as far as artwork is concerned: its content URI and (maybe) its file path. */
    public record AudioRef(Uri uri, @Nullable String path) { }

    /** Platform access; every method may return null / throw, which just moves on to the next source. */
    public interface Backend {
        /** API 29+ {@code loadThumbnail}; null below 29 or when there is none. */
        @Nullable Bitmap thumbnail(Uri uri, int sizePx) throws Exception;

        @Nullable InputStream open(Uri uri) throws Exception;

        @Nullable byte[] embeddedPicture(Uri audioUri) throws Exception;

        /** Up to {@code limit} tracks of the album, in track order. */
        List<AudioRef> albumTracks(String albumId, int limit) throws Exception;

        @Nullable AudioRef audio(String audioId) throws Exception;

        boolean isFile(File f);
    }

    static final int ALBUM_TRACK_LIMIT = 4;
    @VisibleForTesting static final String[] FOLDER_ART = {
            "cover.jpg", "folder.jpg", "front.jpg", "albumart.jpg", "cover.png", "folder.png", "front.png",
            "Cover.jpg", "Folder.jpg", "Front.jpg", "AlbumArt.jpg", "AlbumArtSmall.jpg"};

    private final Backend backend;
    /** Track that gave an album its cover, so other sizes of the same cover skip the failed probes before it. */
    private final android.util.LruCache<String, AudioRef> winners = new android.util.LruCache<>(256);

    public ArtworkResolver(Backend backend) { this.backend = backend; }

    @WorkerThread @Nullable
    public Bitmap resolve(String uriString, int sizePx) {
        Uri uri = Uri.parse(uriString);
        String albumId = mediaId(uri, "albumart");
        if (albumId != null) {
            Bitmap b = thumbnail(Uri.parse("content://media/external/audio/albums/" + albumId), sizePx);
            if (b == null) b = decodeStream(uri, sizePx);
            if (b != null) return b;
            AudioRef known = winners.get(albumId);
            if (known != null && (b = fromAudio(known, sizePx)) != null) return b;
            try {
                for (AudioRef ref : backend.albumTracks(albumId, ALBUM_TRACK_LIMIT)) {
                    b = fromAudio(ref, sizePx);
                    if (b != null) {
                        winners.put(albumId, ref);
                        return b;
                    }
                }
            } catch (Exception ignored) {
                // no permission / provider gone: no art
            }
            return null;
        }
        String audioId = mediaId(uri, "media");
        if (audioId != null) {
            AudioRef ref = null;
            try { ref = backend.audio(audioId); } catch (Exception ignored) { }
            return fromAudio(ref != null ? ref : new AudioRef(uri, null), sizePx);
        }
        return decodeStream(uri, sizePx);
    }

    @Nullable private Bitmap fromAudio(AudioRef ref, int sizePx) {
        Bitmap b = thumbnail(ref.uri(), sizePx);
        if (b != null) return b;
        try {
            byte[] pic = backend.embeddedPicture(ref.uri());
            if (pic != null && pic.length > 0) b = decodeBytes(pic, sizePx);
        } catch (Exception ignored) { }
        if (b != null) return b;
        File dir = ref.path() == null ? null : new File(ref.path()).getParentFile();
        if (dir == null) return null;
        for (String name : FOLDER_ART) {
            File f = new File(dir, name);
            if (backend.isFile(f)) {
                b = decodeStream(Uri.fromFile(f), sizePx);
                if (b != null) return b;
            }
        }
        return null;
    }

    @Nullable private Bitmap thumbnail(Uri uri, int sizePx) {
        try {
            return backend.thumbnail(uri, sizePx);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** {@code content://media/<volume>/audio/<kind>/<id>} -> id, else null. */
    @VisibleForTesting @Nullable
    static String mediaId(Uri uri, String kind) {
        if (!"content".equals(uri.getScheme()) || !"media".equals(uri.getAuthority())) return null;
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 4 || !"audio".equals(seg.get(1)) || !kind.equals(seg.get(2))) return null;
        String id = seg.get(3);
        for (int i = 0; i < id.length(); i++) if (!Character.isDigit(id.charAt(i))) return null;
        return id.isEmpty() ? null : id;
    }

    @Nullable private Bitmap decodeStream(Uri uri, int sizePx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = backend.open(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0) return null;
            prepare(o, sizePx);
            try (InputStream in = backend.open(uri)) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    @Nullable private static Bitmap decodeBytes(byte[] data, int sizePx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0) return null;
            prepare(o, sizePx);
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Downsample to about {@code sizePx}; RGB_565 for opaque JPEG at row/card sizes (half the memory). */
    private static void prepare(BitmapFactory.Options o, int sizePx) {
        o.inSampleSize = ArtworkLoader.sampleSize(o.outWidth, o.outHeight, sizePx);
        boolean opaque = "image/jpeg".equals(o.outMimeType);
        o.inPreferredConfig = sizePx <= 320 && opaque || sizePx <= 160 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        o.inJustDecodeBounds = false;
    }
}
