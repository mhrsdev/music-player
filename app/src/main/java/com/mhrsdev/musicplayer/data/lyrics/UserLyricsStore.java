package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Lyrics the user chose for a track ("Add lyrics file" or a "Search manually" pick), kept in app storage as
 * {@code <dir>/<hash of track id>.lyr} (UTF-8, raw LRC/TTML/plain). Also a {@link LyricsSource}: it is the first local
 * source, so the user's choice wins from then on. Never filtered as junk (the user picked it).
 */
public class UserLyricsStore implements LyricsSource {
    public static final String LABEL = "Your lyrics";
    static final int MAX_BYTES = 512 * 1024;
    private final File dir;

    public UserLyricsStore(File dir) {
        this.dir = dir;
    }

    File fileFor(String trackId) {
        return new File(dir, Integer.toHexString(trackId.hashCode()) + "-" + trackId.length() + ".lyr");
    }

    /** Atomic write (temp file + rename). */
    public void save(String trackId, String text) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File target = fileFor(trackId);
        File tmp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) throw new IOException("cannot write " + target);
    }

    /** Text of a user-picked file: BOM / UTF-16 / UTF-8 / legacy charsets sniffed like sidecar files. */
    public static String decode(byte[] bytes) {
        return LyricsTextDecoder.decode(bytes);
    }

    public boolean delete(String trackId) {
        return fileFor(trackId).delete();
    }

    @Nullable
    @Override public Found load(Track track, CancelToken cancel) throws IOException {
        File f = fileFor(track.id());
        if (!f.isFile()) return null;
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] buf = new byte[(int) Math.min(r.length(), MAX_BYTES)];
            r.readFully(buf);
            return new Found(new String(buf, StandardCharsets.UTF_8), LABEL);
        }
    }
}
