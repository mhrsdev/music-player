package com.mhrsdev.musicplayer.data.update;

import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/** SHA-256 of a file and the {@code sha256sum} line format ({@code <hex>  <filename>}). */
final class Sha256 {
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private Sha256() { }

    static boolean isHex(String s) {
        return HEX.matcher(s).matches();
    }

    /** @return the lowercase hex digest from a checksum file's first token, or null if it is not one */
    @Nullable
    static String parseChecksumFile(@Nullable String text) {
        if (text == null) return null;
        String first = text.trim().split("\\s+", 2)[0];
        return isHex(first) ? first.toLowerCase(Locale.ROOT) : null;
    }

    static String of(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Android has SHA-256
        }
    }
}
