package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** {@link HttpFetcher} on the platform {@link HttpURLConnection}: short timeouts, capped body, cancellable. */
public final class UrlConnectionFetcher implements HttpFetcher {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private final String userAgent;

    /** @param userAgent LRCLIB asks clients to identify themselves, e.g. {@code "MusicPlayer/0.1.0 (https://github.com/mhrsdev/music-player)"} */
    public UrlConnectionFetcher(String userAgent) {
        this.userAgent = userAgent;
    }

    @Nullable
    @Override public String get(String url, CancelToken cancel) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        cancel.setOnCancel(conn::disconnect);
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (code / 100 != 2) throw new IOException("HTTP " + code);
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BODY) throw new IOException("response too large");
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            cancel.setOnCancel(null);
            conn.disconnect();
        }
    }
}
