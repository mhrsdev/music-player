package com.mhrsdev.musicplayer.data.lyrics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Builds tiny ID3 / FLAC / MP4 files in memory for the embedded-lyrics tests. */
final class EmbeddedTagFixtures {
    private EmbeddedTagFixtures() { }

    static byte[] bytes(Object... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object p : parts) {
            if (p instanceof byte[]) out.writeBytes((byte[]) p);
            else if (p instanceof String) out.writeBytes(((String) p).getBytes(StandardCharsets.ISO_8859_1));
            else out.write((Integer) p);
        }
        return out.toByteArray();
    }

    static byte[] be32(long v) {
        return new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    static byte[] synchsafe(int v) {
        return new byte[] {(byte) (v >> 21 & 0x7F), (byte) (v >> 14 & 0x7F), (byte) (v >> 7 & 0x7F), (byte) (v & 0x7F)};
    }

    static byte[] utf16(String s) {
        return bytes(0xFF, 0xFE, s.getBytes(StandardCharsets.UTF_16LE));
    }

    /** USLT body with the given encoding byte, an empty descriptor and the already-encoded text. */
    static byte[] usltBody(int enc, byte[] encodedText) {
        boolean wide = enc == 1 || enc == 2;
        return bytes(enc, "eng", wide ? new byte[] {0, 0} : new byte[] {0}, encodedText);
    }

    /** SYLT body (millisecond timestamps, UTF-8) from alternating text / time pairs. */
    static byte[] syltBody(Object... textAndTime) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes(3, "eng", 2, 1, 0)); // enc, lang, ms timestamps, content type "lyrics", empty descriptor
        for (int i = 0; i < textAndTime.length; i += 2) {
            out.writeBytes(((String) textAndTime[i]).getBytes(StandardCharsets.UTF_8));
            out.write(0);
            out.writeBytes(be32((Integer) textAndTime[i + 1]));
        }
        return out.toByteArray();
    }

    /** ID3v2.3/2.4 tag from frames given as (id, body) pairs. */
    static byte[] id3(int version, Object... idAndBody) {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (int i = 0; i < idAndBody.length; i += 2) {
            byte[] body = (byte[]) idAndBody[i + 1];
            frames.writeBytes(bytes(idAndBody[i], version == 4 ? synchsafe(body.length) : be32(body.length), 0, 0, body));
        }
        byte[] f = frames.toByteArray();
        return bytes("ID3", version, 0, 0, synchsafe(f.length), f, new byte[20] /* padding + fake audio */);
    }

    /** ID3v2.2 tag (3-char ids, 3-byte sizes). */
    static byte[] id3v22(String id, byte[] body) {
        byte[] frame = bytes(id, body.length >> 16 & 0xFF, body.length >> 8 & 0xFF, body.length & 0xFF, body);
        return bytes("ID3", 2, 0, 0, synchsafe(frame.length), frame, new byte[10]);
    }

    static byte[] flac(String... comments) {
        ByteArrayOutputStream vc = new ByteArrayOutputStream();
        byte[] vendor = "test".getBytes(StandardCharsets.UTF_8);
        vc.writeBytes(le32(vendor.length));
        vc.writeBytes(vendor);
        vc.writeBytes(le32(comments.length));
        for (String c : comments) {
            byte[] b = c.getBytes(StandardCharsets.UTF_8);
            vc.writeBytes(le32(b.length));
            vc.writeBytes(b);
        }
        byte[] body = vc.toByteArray();
        byte[] streamInfo = new byte[34];
        return bytes("fLaC", 0, 0, 0, 34, streamInfo, 0x84, body.length >> 16 & 0xFF, body.length >> 8 & 0xFF,
                body.length & 0xFF, body, new byte[16]);
    }

    private static byte[] le32(int v) {
        return new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    static byte[] atom(String type, Object... body) {
        byte[] b = bytes(body);
        return bytes(be32(8 + b.length), type, b);
    }

    /** M4A with ftyp + moov/udta/meta/ilst/(c)lyr/data holding {@code text}; an unrelated mdat follows. */
    static byte[] m4a(String text) {
        byte[] data = atom("data", 0, 0, 0, 1, 0, 0, 0, 0, text.getBytes(StandardCharsets.UTF_8));
        byte[] lyr = atom("©lyr", data);
        byte[] ilst = atom("ilst", atom("©nam", atom("data", 0, 0, 0, 1, 0, 0, 0, 0, "title")), lyr);
        byte[] meta = atom("meta", 0, 0, 0, 0, ilst);
        byte[] moov = atom("moov", atom("udta", meta));
        return bytes(atom("ftyp", "M4A ", 0, 0, 0, 0, "M4A "), moov, atom("mdat", new byte[32]));
    }

    static File write(File dir, String name, byte[] content) throws IOException {
        File f = new File(dir, name);
        Files.write(f.toPath(), content);
        return f;
    }
}
