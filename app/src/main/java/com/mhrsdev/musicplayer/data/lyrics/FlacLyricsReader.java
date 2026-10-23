package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Reads the LYRICS / UNSYNCEDLYRICS / SYNCEDLYRICS Vorbis comment of a FLAC file (synced text wins). */
final class FlacLyricsReader {
    private static final int MAX_BLOCK = 1024 * 1024;

    private FlacLyricsReader() { }

    @Nullable
    static String read(RandomAccessFile f) throws IOException {
        byte[] magic = new byte[4];
        f.seek(0);
        if (f.read(magic) != 4 || magic[0] != 'f' || magic[1] != 'L' || magic[2] != 'a' || magic[3] != 'C') return null;
        long pos = 4;
        boolean last = false;
        byte[] h = new byte[4];
        while (!last) {
            f.seek(pos);
            f.readFully(h);
            last = (h[0] & 0x80) != 0;
            int type = h[0] & 0x7F;
            int len = ((h[1] & 0xFF) << 16) | ((h[2] & 0xFF) << 8) | (h[3] & 0xFF);
            if (type == 4) return len > MAX_BLOCK ? null : comments(readBlock(f, len));
            pos += 4 + len;
        }
        return null;
    }

    private static byte[] readBlock(RandomAccessFile f, int len) throws IOException {
        byte[] b = new byte[len];
        f.readFully(b);
        return b;
    }

    @Nullable
    static String comments(byte[] b) {
        String plain = null;
        String synced = null;
        long vendor = le(b, 0);
        if (vendor < 0 || 4 + vendor + 4 > b.length) return null;
        int pos = (int) (4 + vendor); // skip vendor string
        long count = le(b, pos);
        pos += 4;
        for (long i = 0; i < count && pos + 4 <= b.length; i++) {
            long len = le(b, pos);
            pos += 4;
            if (len < 0 || pos + len > b.length) break;
            String kv = new String(b, pos, (int) len, StandardCharsets.UTF_8);
            pos += (int) len;
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            String key = kv.substring(0, eq).toUpperCase(Locale.ROOT);
            String value = kv.substring(eq + 1);
            if (value.trim().isEmpty()) continue;
            if (key.equals("SYNCEDLYRICS") && synced == null) synced = value;
            else if ((key.equals("LYRICS") || key.equals("UNSYNCEDLYRICS")) && plain == null) plain = value;
        }
        return synced != null ? synced : plain;
    }

    private static long le(byte[] b, int off) {
        if (off < 0 || off + 4 > b.length) return -1;
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8) | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }
}
