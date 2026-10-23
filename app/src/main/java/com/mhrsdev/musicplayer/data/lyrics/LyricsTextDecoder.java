package com.mhrsdev.musicplayer.data.lyrics;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Turns the bytes of a lyrics file into text: UTF-8 / UTF-16 BOMs, then strict UTF-8, then a legacy 8-bit fallback.
 * Old Persian/Arabic {@code .lrc} files are usually Windows-1256, so that is chosen when most non-ASCII characters decode
 * to Arabic-script letters; anything else falls back to ISO-8859-1.
 */
final class LyricsTextDecoder {
    private LyricsTextDecoder() { }

    static String decode(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            String arabic = new String(b, legacyArabic());
            return mostlyArabic(arabic) ? arabic : new String(b, StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean mostlyArabic(String s) {
        int nonAscii = 0;
        int arabic = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) continue;
            nonAscii++;
            if (c >= 0x0600 && c <= 0x06FF) arabic++;
        }
        return nonAscii > 0 && arabic * 2 >= nonAscii;
    }

    private static Charset legacyArabic() {
        try {
            return Charset.forName("windows-1256");
        } catch (RuntimeException e) { // unsupported on this runtime
            return StandardCharsets.ISO_8859_1;
        }
    }
}
