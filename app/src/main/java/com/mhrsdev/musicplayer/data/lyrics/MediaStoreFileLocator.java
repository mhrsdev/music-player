package com.mhrsdev.musicplayer.data.lyrics;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;

/** {@link AudioFileLocator} that resolves {@code content://media/...} URIs to their file path via MediaStore. */
public final class MediaStoreFileLocator implements AudioFileLocator {
    private final ContentResolver resolver;

    public MediaStoreFileLocator(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @Nullable
    @Override public File locate(Track track) {
        String uri = track.mediaUri();
        if (uri == null || !uri.startsWith("content://")) return FILE_ONLY.locate(track);
        // DATA is deprecated but is the only way to get a path for the sidecar lookup; null/unreadable = skip.
        try (Cursor c = resolver.query(Uri.parse(uri), new String[] {MediaStore.MediaColumns.DATA}, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            String path = c.getString(0);
            return path == null ? null : new File(path);
        } catch (RuntimeException e) { // SecurityException, IllegalArgumentException: not accessible
            return null;
        }
    }
}
