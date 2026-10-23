package com.mhrsdev.musicplayer.data.update;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.IntConsumer;

/** In-memory stand-ins for the updater's seams (network, preferences, platform glue). */
final class FakeUpdates {
    private FakeUpdates() { }

    static final class Http implements UpdateHttp {
        final Map<String, Queue<Response>> answers = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
        /** One entry per get(): "url|etag". */
        final List<String> requests = new ArrayList<>();
        final List<String> downloads = new ArrayList<>();
        boolean offline;

        void answer(String url, int code, @Nullable String etag, @Nullable String body) {
            answers.computeIfAbsent(url, k -> new ArrayDeque<>()).add(new Response(code, etag, body));
        }

        @Override public Response get(String url, @Nullable String etag) throws IOException {
            requests.add(url + "|" + etag);
            if (offline) throw new IOException("offline");
            Queue<Response> q = answers.get(url);
            if (q == null || q.isEmpty()) throw new IOException("no answer for " + url);
            return q.size() > 1 ? q.remove() : q.peek();
        }

        @Override public void download(String url, File dest, IntConsumer percent) throws IOException {
            downloads.add(url);
            byte[] bytes = files.get(url);
            if (offline || bytes == null) throw new IOException("cannot download " + url);
            percent.accept(0);
            percent.accept(50);
            Files.write(dest.toPath(), bytes);
            percent.accept(100);
        }
    }

    static final class Store implements UpdateStore {
        UpdateSettings settings = new UpdateSettings(true, true, false, 0);
        String skipped, cacheUrl, cacheEtag, cacheBody;

        @Override public UpdateSettings settings() { return settings; }
        @Override public void putSettings(UpdateSettings s) { settings = s; }
        @Nullable @Override public String skippedTag() { return skipped; }
        @Override public void putSkippedTag(@Nullable String tag) { skipped = tag; }
        @Nullable @Override public String cacheUrl() { return cacheUrl; }
        @Nullable @Override public String cacheEtag() { return cacheEtag; }
        @Nullable @Override public String cacheBody() { return cacheBody; }
        @Override public void putCache(@Nullable String url, @Nullable String etag, @Nullable String body) {
            cacheUrl = url;
            cacheEtag = etag;
            cacheBody = body;
        }
    }

    static final class Inspector implements ApkInspector {
        Verdict verdict = Verdict.OK;
        @Override public Verdict inspect(File apk) { return verdict; }
    }

    static final class Installer implements ApkInstaller {
        final List<File> installed = new ArrayList<>();
        boolean fail;
        @Override public void install(File apk) throws IOException {
            if (fail) throw new IOException("session failed");
            installed.add(apk);
        }
    }

    static final class Net implements Connectivity {
        boolean online = true;
        boolean metered;
        @Override public boolean isOnline() { return online; }
        @Override public boolean isMetered() { return metered; }
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
