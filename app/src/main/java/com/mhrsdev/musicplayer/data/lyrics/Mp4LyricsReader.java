package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Reads the iTunes-style {@code moov/udta/meta/ilst/\u00A9lyr} atom of an M4A/MP4 file (plain text). */
final class Mp4LyricsReader {
    private static final int MAX_ATOM = 1024 * 1024;
    private static final String[] PATH = {"moov", "udta", "meta", "ilst", "\u00A9lyr", "data"};

    private Mp4LyricsReader() { }

    @Nullable
    static String read(RandomAccessFile f) throws IOException {
        byte[] data = find(f, 0, f.length(), 0);
        if (data == null || data.length <= 8) return null; // data atom body: 4 bytes type/flags + 4 bytes locale + text
        String s = new String(data, 8, data.length - 8, StandardCharsets.UTF_8).trim();
        return s.isEmpty() ? null : s;
    }

    /** Body of the atom at {@code PATH[depth]} inside [start, end), or null. */
    @Nullable
    private static byte[] find(RandomAccessFile f, long start, long end, int depth) throws IOException {
        byte[] h = new byte[8];
        long pos = start;
        while (pos + 8 <= end) {
            f.seek(pos);
            f.readFully(h);
            long size = ((h[0] & 0xFFL) << 24) | ((h[1] & 0xFF) << 16) | ((h[2] & 0xFF) << 8) | (h[3] & 0xFF);
            String type = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
            long header = 8;
            if (size == 1) { // 64-bit size follows
                byte[] big = new byte[8];
                f.readFully(big);
                size = 0;
                for (byte x : big) size = (size << 8) | (x & 0xFF);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end) return null;
            if (type.equals(PATH[depth])) {
                long bodyStart = pos + header + (type.equals("meta") ? 4 : 0); // meta is a full box
                if (depth == PATH.length - 1) {
                    long len = pos + size - bodyStart;
                    if (len > MAX_ATOM) return null;
                    byte[] body = new byte[(int) len];
                    f.seek(bodyStart);
                    f.readFully(body);
                    return body;
                }
                return find(f, bodyStart, pos + size, depth + 1);
            }
            pos += size;
        }
        return null;
    }
}
