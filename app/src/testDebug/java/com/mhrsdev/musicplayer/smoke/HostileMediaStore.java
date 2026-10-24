package com.mhrsdev.musicplayer.smoke;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.Robolectric;

/**
 * MediaStore "audio" table full of hostile metadata, served as authority "media": null / empty / huge / RTL / emoji /
 * zero-width titles, 0 and negative durations, odd years, duplicate ids, paths to files that do not exist, and as many
 * ordinary rows as asked. Files (thumbnails, audio) never open: every artwork probe and every play fails like a
 * deleted file would.
 */
public class HostileMediaStore extends ContentProvider {
    private static final List<Map<String, Object>> ROWS = new ArrayList<>();

    public static void install(int ordinaryTracks) {
        ROWS.clear();
        String longText = "Very long title ".repeat(120);
        row(1, null, null, null, 0, 0);
        row(2, "", "", "", -1, -5);
        row(3, longText, longText, longText, 1, 99999);
        row(4, "آهنگ‌های من ‏​", "موج‌ها",
                "آلبوم ۱۴۰۳", 245_000, 1403);
        row(5, "🎵🔥 Emoji 👍🏽 ‍❤️", "🎤", "💿", 61_000, 2020);
        row(6, "CGtoIELTS_63", "<unknown>", "Download", 1_800_000, 0);
        row(7, "1_30664177684", "<unknown>", "Telegram Audio", Long.MAX_VALUE / 4, 0);
        row(8, "​​​", "‏", "‮", 3_000, 0);
        row(9, "Line\nbreak\tand\rcontrol\u0000", "A\nB", "C\tD", 200_000, 1999);
        row(9, "Duplicate id 9", "Dup", "Dup", 100_000, 2001); // MediaStore never does this; a broken provider might
        row(10, "x", "y", null, 1, 1970);
        for (int i = 0; i < ordinaryTracks; i++) {
            long id = 1000 + i;
            String album = i % 7 == 0 ? null : "Album " + (i % 230);
            row(id, "Track " + i + (i % 11 == 0 ? " آهنگ" : ""), "Artist " + (i % 57), album,
                    30_000 + (i * 7919L) % 600_000, 1950 + i % 76);
        }
        Robolectric.buildContentProvider(HostileMediaStore.class).create("media");
    }

    private static void row(long id, String title, String artist, String album, long durationMs, int year) {
        Map<String, Object> r = new HashMap<>();
        r.put(MediaStore.Audio.Media._ID, id);
        r.put(MediaStore.Audio.Media.TITLE, title);
        r.put(MediaStore.Audio.Media.DISPLAY_NAME, (title == null ? "noname" : title.replace('/', '_')) + ".mp3");
        r.put(MediaStore.Audio.Media.ARTIST, artist);
        r.put(MediaStore.Audio.Media.ARTIST_ID, artist == null ? 0L : (long) (artist.hashCode() & 0xffff));
        r.put(MediaStore.Audio.Media.ALBUM, album);
        r.put(MediaStore.Audio.Media.ALBUM_ID, album == null ? 0L : (long) (album.hashCode() & 0xffff));
        r.put(MediaStore.Audio.Media.DURATION, durationMs);
        r.put(MediaStore.Audio.Media.TRACK, (int) (id % 2000));
        r.put(MediaStore.Audio.Media.YEAR, year);
        r.put(MediaStore.Audio.Media.DATE_ADDED, 1_700_000_000L - id * 37);
        r.put(MediaStore.Audio.Media.DATE_MODIFIED, 1_700_000_000L - id * 37);
        r.put(MediaStore.Audio.Media.DATA, "/storage/emulated/0/Music/" + (album == null ? "" : album.hashCode() + "/")
                + id + ".mp3");
        r.put(MediaStore.Audio.Media.SIZE, 4_000_000L + id);
        r.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        r.put(MediaStore.Audio.Media.IS_PENDING, 0);
        r.put(MediaStore.Audio.Media.GENRE, id % 3 == 0 ? null : id % 3 == 1 ? "Pop" : "پاپ");
        ROWS.add(r);
    }

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        if (projection == null) projection = new String[] {MediaStore.Audio.Media._ID};
        MatrixCursor c = new MatrixCursor(projection);
        for (Map<String, Object> r : ROWS) {
            if (args != null && args.length == 1 && selection != null && selection.contains(MediaStore.Audio.Media.ALBUM_ID)
                    && !args[0].equals(String.valueOf(r.get(MediaStore.Audio.Media.ALBUM_ID)))) continue;
            Object[] row = new Object[projection.length];
            for (int i = 0; i < row.length; i++) row[i] = r.get(projection[i]);
            c.addRow(row);
        }
        return c;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        return null;
    }

    @Override public String getType(Uri uri) { return "audio/mpeg"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
