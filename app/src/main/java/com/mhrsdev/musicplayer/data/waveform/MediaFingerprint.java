package com.mhrsdev.musicplayer.data.waveform;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.util.function.Function;

/**
 * {@code size:modified:duration} of the track's file, the cache validity key. Does I/O, so call it off the main
 * thread. Anything unreadable degrades to 0 for that part, never throws.
 */
public final class MediaFingerprint implements Function<Track, String> {
    private final ContentResolver resolver;

    public MediaFingerprint(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
    }

    @Override public String apply(Track track) {
        long size = 0, modified = 0;
        if (track.mediaUri() != null) {
            Uri uri = Uri.parse(track.mediaUri());
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                File f = new File(uri.getPath());
                size = f.length();
                modified = f.lastModified();
            } else {
                try (AssetFileDescriptor afd = resolver.openAssetFileDescriptor(uri, "r")) {
                    size = afd == null ? 0 : afd.getLength();
                } catch (Exception ignored) { /* unreadable: size stays 0 */ }
                try (Cursor c = resolver.query(uri, new String[] {MediaStore.MediaColumns.DATE_MODIFIED},
                        null, null, null)) {
                    if (c != null && c.moveToFirst()) modified = c.getLong(0);
                } catch (Exception ignored) { /* not a MediaStore uri: modified stays 0 */ }
            }
        }
        return size + ":" + modified + ":" + track.durationMs();
    }
}
