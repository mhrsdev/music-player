package com.mhrsdev.musicplayer.data.artwork;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The real {@link ArtworkSource.Backend}: ContentResolver, MediaStore and MediaMetadataRetriever. */
final class AndroidArtworkBackend implements ArtworkSource.Backend {
    private static final Uri AUDIO = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

    private final Context context;

    AndroidArtworkBackend(Context context) {
        this.context = context;
    }

    @Override @Nullable public InputStream open(Uri uri) {
        try {
            return context.getContentResolver().openInputStream(uri);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Override @Nullable public Bitmap thumbnail(Uri audio, int sizePx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            return context.getContentResolver().loadThumbnail(audio, new Size(sizePx, sizePx), null);
        } catch (IOException | RuntimeException e) {
            return null; // no art in the file, or the provider cannot make one
        }
    }

    @Override @Nullable public byte[] embeddedPicture(Uri audio) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, audio);
            return retriever.getEmbeddedPicture();
        } catch (RuntimeException e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
                // nothing left to free
            }
        }
    }

    @Override public List<ArtworkSource.TrackRef> albumTracks(long albumId, int limit) {
        return query(MediaStore.Audio.Media.ALBUM_ID + " = ? AND " + MediaStore.Audio.Media.IS_MUSIC + " != 0",
                String.valueOf(albumId), MediaStore.Audio.Media.TRACK, limit);
    }

    @Override public List<ArtworkSource.TrackRef> track(long audioId) {
        return query(MediaStore.Audio.Media._ID + " = ?", String.valueOf(audioId), null, 1);
    }

    @SuppressWarnings("deprecation") // DATA is the only way to the sibling cover file, still readable on all versions
    private List<ArtworkSource.TrackRef> query(String selection, String arg, @Nullable String order, int limit) {
        List<ArtworkSource.TrackRef> out = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(AUDIO, new String[] {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA},
                selection, new String[] {arg}, order)) {
            while (c != null && c.moveToNext() && out.size() < limit) {
                out.add(new ArtworkSource.TrackRef(Uri.withAppendedPath(AUDIO, String.valueOf(c.getLong(0))), c.getString(1)));
            }
        } catch (RuntimeException e) {
            // no permission yet: no tracks, no art
        }
        return out;
    }

    @Override @Nullable public InputStream folderImage(String path) {
        for (String candidate : ArtworkSource.folderCandidates(path)) {
            File f = new File(candidate);
            if (!f.isFile() || !f.canRead()) continue;
            try {
                return new FileInputStream(f);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }
}
