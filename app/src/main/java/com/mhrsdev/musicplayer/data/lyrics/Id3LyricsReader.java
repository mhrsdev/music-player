package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads lyrics from an ID3v2.2/2.3/2.4 tag at the start of a file: synchronised lyrics (SYLT, converted to LRC text,
 * millisecond timestamps only) win over unsynchronised lyrics (USLT). Walks frame headers with seeks, so large
 * artwork frames are skipped, not loaded. Not handled: tag-level unsynchronisation, compressed/encrypted frames.
 */
final class Id3LyricsReader {
    private static final int MAX_FRAME = 512 * 1024;

    private Id3LyricsReader() { }

    @Nullable
    static String read(RandomAccessFile f) throws IOException {
        byte[] h = new byte[10];
        f.seek(0);
        if (f.read(h) != 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') return null;
        int ver = h[3] & 0xFF;
        if (ver < 2 || ver > 4) return null;
        long end = 10 + synchsafe(h, 6);
        long pos = 10;
        if (ver >= 3 && (h[5] & 0x40) != 0) { // extended header
            byte[] e = new byte[4];
            f.readFully(e);
            pos += ver == 4 ? synchsafe(e, 0) : 4 + be(e, 0, 4);
        }
        int idLen = ver == 2 ? 3 : 4;
        int hdr = ver == 2 ? 6 : 10;
        String uslt = null;
        String sylt = null;
        byte[] fh = new byte[hdr];
        while (pos + hdr <= end) {
            f.seek(pos);
            f.readFully(fh);
            if (fh[0] == 0) break; // padding
            String id = new String(fh, 0, idLen, StandardCharsets.ISO_8859_1);
            long size = ver == 2 ? be(fh, 3, 3) : ver == 4 ? synchsafe(fh, 4) : be(fh, 4, 4);
            if (size <= 0 || pos + hdr + size > end) break;
            boolean isUslt = id.equals("USLT") || id.equals("ULT");
            boolean isSylt = id.equals("SYLT") || id.equals("SLT");
            int flags2 = ver == 2 ? 0 : fh[9] & 0xFF;
            boolean unreadable = (flags2 & 0x0C) != 0; // compressed or encrypted
            if ((isUslt && uslt == null || isSylt && sylt == null) && size <= MAX_FRAME && !unreadable) {
                byte[] data = new byte[(int) size];
                f.readFully(data);
                int from = ver == 4 && (flags2 & 0x01) != 0 ? 4 : 0; // data length indicator
                String text = isUslt ? uslt(data, from) : sylt(data, from);
                if (isUslt) uslt = text; else sylt = text;
            }
            pos += hdr + size;
        }
        return sylt != null ? sylt : uslt;
    }

    @Nullable
    static String uslt(byte[] d, int from) {
        if (d.length < from + 5) return null;
        int enc = d[from];
        int textStart = afterTerminator(d, from + 4, enc);
        String s = decode(d, textStart, d.length, enc).replace("\0", "").trim();
        return s.isEmpty() ? null : s;
    }

    /** SYLT -> LRC text. Entries are lines when none contains a newline, otherwise a newline starts the next line. */
    @Nullable
    static String sylt(byte[] d, int from) {
        if (d.length < from + 7 || d[from + 4] != 2) return null; // only millisecond timestamps
        int enc = d[from];
        int pos = afterTerminator(d, from + 6, enc);
        List<Long> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        boolean anyNewline = false;
        while (pos < d.length) {
            int term = indexOfTerminator(d, pos, enc);
            if (term < 0 || term + terminatorLength(enc) + 4 > d.length) break;
            String text = decode(d, pos, term, enc);
            int t = term + terminatorLength(enc);
            times.add(be(d, t, 4));
            texts.add(text);
            anyNewline |= text.indexOf('\n') >= 0;
            pos = t + 4;
        }
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        long lineTime = -1;
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            boolean breaksBefore = !anyNewline || t.startsWith("\n");
            if (breaksBefore && line.length() > 0) {
                appendLrc(out, lineTime, line);
                line.setLength(0);
            }
            if (line.length() == 0) lineTime = times.get(i);
            line.append(t.replace("\n", "").replace("\r", ""));
            if (anyNewline && t.endsWith("\n")) {
                appendLrc(out, lineTime, line);
                line.setLength(0);
            }
        }
        if (line.length() > 0) appendLrc(out, lineTime, line);
        return out.length() == 0 ? null : out.toString();
    }

    private static void appendLrc(StringBuilder out, long ms, CharSequence text) {
        out.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]", ms / 60000, ms / 1000 % 60, ms % 1000 / 10))
                .append(text).append('\n');
    }

    private static int terminatorLength(int enc) {
        return enc == 1 || enc == 2 ? 2 : 1;
    }

    private static int indexOfTerminator(byte[] d, int from, int enc) {
        int n = terminatorLength(enc);
        for (int i = from; i + n <= d.length; i += n) {
            if (d[i] == 0 && (n == 1 || d[i + 1] == 0)) return i;
        }
        return -1;
    }

    private static int afterTerminator(byte[] d, int from, int enc) {
        int i = indexOfTerminator(d, from, enc);
        return i < 0 ? d.length : i + terminatorLength(enc);
    }

    static String decode(byte[] d, int from, int to, int enc) {
        if (to <= from) return "";
        switch (enc) {
            case 1: // UTF-16 with BOM
                if (to - from >= 2 && (d[from] & 0xFF) == 0xFF && (d[from + 1] & 0xFF) == 0xFE) {
                    return new String(d, from + 2, to - from - 2, StandardCharsets.UTF_16LE);
                }
                if (to - from >= 2 && (d[from] & 0xFF) == 0xFE && (d[from + 1] & 0xFF) == 0xFF) {
                    return new String(d, from + 2, to - from - 2, StandardCharsets.UTF_16BE);
                }
                return new String(d, from, to - from, StandardCharsets.UTF_16LE);
            case 2:
                return new String(d, from, to - from, StandardCharsets.UTF_16BE);
            case 3:
                return new String(d, from, to - from, StandardCharsets.UTF_8);
            default:
                return new String(d, from, to - from, StandardCharsets.ISO_8859_1);
        }
    }

    private static long synchsafe(byte[] b, int off) {
        return ((b[off] & 0x7FL) << 21) | ((b[off + 1] & 0x7FL) << 14) | ((b[off + 2] & 0x7FL) << 7) | (b[off + 3] & 0x7FL);
    }

    private static long be(byte[] b, int off, int n) {
        long v = 0;
        for (int i = 0; i < n; i++) v = (v << 8) | (b[off + i] & 0xFF);
        return v;
    }
}
