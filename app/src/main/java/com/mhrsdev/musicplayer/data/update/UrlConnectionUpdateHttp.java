package com.mhrsdev.musicplayer.data.update;

import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;

/**
 * {@link UpdateHttp} on the platform {@link HttpURLConnection}: short timeouts, capped JSON bodies, no resume.
 * Redirects (301/302/303/307/308: a renamed repository, release assets served from objects.githubusercontent.com) are
 * followed here, at most {@value #MAX_REDIRECTS} hops, never from https down to http.
 */
class UrlConnectionUpdateHttp implements UpdateHttp {
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_BODY = 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private final String userAgent;

    /** @param userAgent GitHub requires one, e.g. {@code "MusicPlayer/0.1.0 (https://github.com/mhrsdev/music-player-mowj)"} */
    UrlConnectionUpdateHttp(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override public Response get(String url, @Nullable String etag) throws IOException {
        HttpURLConnection conn = open(url, etag);
        try {
            int code = conn.getResponseCode();
            String body = null;
            if (code == HttpURLConnection.HTTP_OK) {
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    copy(in, out, -1, p -> { }, MAX_BODY);
                    body = out.toString(StandardCharsets.UTF_8.name());
                }
            }
            return new Response(code, conn.getHeaderField("ETag"), body);
        } finally {
            conn.disconnect();
        }
    }

    @Override public void download(String url, File dest, IntConsumer percent) throws IOException {
        HttpURLConnection conn = open(url, null);
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + conn.getResponseCode());
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
                copy(in, out, conn.getContentLengthLong(), percent, Long.MAX_VALUE);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** @return the connection of the final response after following redirects; the caller disconnects it */
    private HttpURLConnection open(String firstUrl, @Nullable String etag) throws IOException {
        URL url = new URL(firstUrl);
        for (int hop = 0; ; hop++) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept", "application/vnd.github+json, */*");
            if (etag != null) conn.setRequestProperty("If-None-Match", etag);
            int code = conn.getResponseCode();
            if (!isRedirect(code)) return conn;
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location == null || hop >= MAX_REDIRECTS) throw new IOException("bad redirect from " + url);
            URL next = new URL(url, location);
            if ("https".equals(url.getProtocol()) && !"https".equals(next.getProtocol())) throw new IOException("https downgrade to " + next);
            url = next;
        }
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    /**
     * Copies {@code in} to {@code out}, reporting whole percents of {@code total} (skipped when it is unknown, &lt;= 0).
     * Throws when more than {@code max} bytes arrive or the stream ends before {@code total} bytes.
     */
    static void copy(InputStream in, OutputStream out, long total, IntConsumer percent, long max) throws IOException {
        byte[] buf = new byte[32 * 1024];
        long done = 0;
        int last = -1;
        int n;
        percent.accept(0);
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            done += n;
            if (done > max) throw new IOException("response too large");
            int p = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
            if (p != last && total > 0) {
                last = p;
                percent.accept(p);
            }
        }
        if (total > 0 && done != total) throw new IOException("truncated download");
    }
}
