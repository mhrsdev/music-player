package com.mhrsdev.musicplayer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.Robolectric;

/** In-memory MediaStore audio table served through a ContentProvider registered as authority "media". */
public final class FakeMediaStore extends ContentProvider {
    private static final List<Map<String, Object>> ROWS = new ArrayList<>();

    /** Empties the table and registers the provider for the current Robolectric app. */
    public static void install() {
        ROWS.clear();
        Robolectric.buildContentProvider(FakeMediaStore.class).create("media");
    }

    /** Adds or replaces (by id) a row; times in seconds like MediaStore. */
    public static void put(long id, String name, long durationMs, boolean music, boolean pending, long modifiedSec,
            long size) {
        remove(id);
        Map<String, Object> r = new HashMap<>();
        r.put(MediaStore.Audio.Media._ID, id);
        r.put(MediaStore.Audio.Media.TITLE, name);
        r.put(MediaStore.Audio.Media.DISPLAY_NAME, name + ".mp3");
        r.put(MediaStore.Audio.Media.ARTIST, "Artist");
        r.put(MediaStore.Audio.Media.ARTIST_ID, 7L);
        r.put(MediaStore.Audio.Media.ALBUM, "Album");
        r.put(MediaStore.Audio.Media.ALBUM_ID, 9L);
        r.put(MediaStore.Audio.Media.DURATION, durationMs);
        r.put(MediaStore.Audio.Media.DATE_ADDED, 1000L);
        r.put(MediaStore.Audio.Media.DATE_MODIFIED, modifiedSec);
        r.put(MediaStore.Audio.Media.DATA, "/storage/emulated/0/Download/" + name + ".mp3");
        r.put(MediaStore.Audio.Media.SIZE, size);
        r.put(MediaStore.Audio.Media.IS_MUSIC, music ? 1 : 0);
        r.put(MediaStore.Audio.Media.IS_PENDING, pending ? 1 : 0);
        ROWS.add(r);
    }

    public static void put(long id, String name) {
        put(id, name, 180_000, true, false, 2000, 1000);
    }

    public static void remove(long id) {
        ROWS.removeIf(r -> ((Long) r.get(MediaStore.Audio.Media._ID)) == id);
    }

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor c = new MatrixCursor(projection);
        for (Map<String, Object> r : ROWS) {
            Object[] row = new Object[projection.length];
            for (int i = 0; i < row.length; i++) {
                Object v = r.get(projection[i]);
                row[i] = v != null ? v : (projection[i].equals(MediaStore.Audio.Media.TITLE) ? "" : 0);
            }
            c.addRow(row);
        }
        return c;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
