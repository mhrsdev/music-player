package com.mhrsdev.musicplayer.data.lyrics;

import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class LyricsTextDecoderTest {
    private static final String FA = "[00:01.00]سلام دنیا، چگونه\u200Cای؟ گژپ";

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @Test public void utf8WithAndWithoutBom() {
        assertEquals(FA, LyricsTextDecoder.decode(FA.getBytes(StandardCharsets.UTF_8)));
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        assertEquals(FA, LyricsTextDecoder.decode(concat(bom, FA.getBytes(StandardCharsets.UTF_8))));
    }

    @Test public void utf16BothEndiansWithBom() {
        assertEquals(FA, LyricsTextDecoder.decode(concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                FA.getBytes(StandardCharsets.UTF_16LE))));
        assertEquals(FA, LyricsTextDecoder.decode(concat(new byte[] {(byte) 0xFE, (byte) 0xFF},
                FA.getBytes(StandardCharsets.UTF_16BE))));
    }

    @Test public void legacyWindows1256PersianIsDetected() {
        // cp1256 has no Persian yeh (U+06CC); old files used Arabic yeh (U+064A)
        String fa = "[00:01.00]سلام دنيا چگونه\u200Cاي گژپ";
        assertEquals(fa, LyricsTextDecoder.decode(fa.getBytes(Charset.forName("windows-1256"))));
    }

    @Test public void legacyLatinFallsBackToIso88591() {
        String latin = "[00:01.00]café déjà vu";
        assertEquals(latin, LyricsTextDecoder.decode(latin.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test public void emptyInput() {
        assertEquals("", LyricsTextDecoder.decode(new byte[0]));
    }
}
