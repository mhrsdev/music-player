package com.mhrsdev.musicplayer.data.scanner;

import com.mhrsdev.musicplayer.diag.Regexes;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.db.query.Seeds;
import com.mhrsdev.musicplayer.domain.model.Track;

/**
 * One MediaStore audio row, already read from the cursor; {@link #toTrack()} is the pure mapping to the domain.
 * {@code trackNumber} is MediaStore's raw value (disc * 1000 + track); the track queries split it on read.
 */
public record ScannedAudio(
        long id,
        @Nullable String title,
        @Nullable String displayName,
        @Nullable String artist,
        long artistId,
        @Nullable String album,
        long albumId,
        long durationMs,
        int trackNumber,
        int year,
        long addedAtMs,
        long modifiedMs,
        @Nullable String path,
        long sizeBytes,
        /** MediaStore IS_MUSIC. */
        boolean music,
        /** Ringtone, notification, alarm or podcast. */
        boolean special,
        /** Android 10+: the file is still being written (a running download). */
        boolean pending,
        /** MediaStore GENRE (API 30+), cleaned; null when unknown. */
        @Nullable String genre) {

    /** Rows / tests without a genre. */
    public ScannedAudio(long id, @Nullable String title, @Nullable String displayName, @Nullable String artist,
                        long artistId, @Nullable String album, long albumId, long durationMs, int trackNumber, int year,
                        long addedAtMs, long modifiedMs, @Nullable String path, long sizeBytes, boolean music,
                        boolean special, boolean pending) {
        this(id, title, displayName, artist, artistId, album, albumId, durationMs, trackNumber, year, addedAtMs,
                modifiedMs, path, sizeBytes, music, special, pending, null);
    }

    /** Trims; blank, "<unknown>" and ID3v1 numeric codes like "(17)" become null. */
    @Nullable public static String cleanGenre(@Nullable String raw) {
        if (raw == null) return null;
        String g = raw.trim();
        if (g.isEmpty() || UNKNOWN.equals(g) || GENRE_CODE.matcher(g).matches()) return null;
        return g.length() > 40 ? g.substring(0, 40) : g;
    }

    /** Compiled once: the scan calls {@link #cleanGenre} for every row. */
    private static final java.util.regex.Pattern GENRE_CODE = Regexes.compile("\\(?\\d+\\)?");

    /** MediaStore's placeholder for missing tags. */
    private static final String UNKNOWN = "<unknown>";
    private static final String AUDIO_URI = "content://media/external/audio/media/";
    private static final String ALBUM_ART_URI = "content://media/external/audio/albumart/";

    public Track toTrack() {
        String albumKey = String.valueOf(albumId);
        return new Track(String.valueOf(id), titleOrFileName(), String.valueOf(artistId),
                known(artist, Track.UNKNOWN_ARTIST), albumKey, known(album, Track.UNKNOWN_ALBUM), durationMs,
                albumId > 0 ? ALBUM_ART_URI + albumId : null, Seeds.of(albumKey), AUDIO_URI + id,
                false, false, year, trackNumber, addedAtMs);
    }

    private String titleOrFileName() {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        if (displayName == null || displayName.trim().isEmpty()) return "Unknown title";
        int dot = displayName.lastIndexOf('.');
        return dot > 0 ? displayName.substring(0, dot) : displayName;
    }

    private static String known(@Nullable String value, String fallback) {
        return value == null || value.trim().isEmpty() || UNKNOWN.equals(value) ? fallback : value.trim();
    }
}
