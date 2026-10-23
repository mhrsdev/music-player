package com.mhrsdev.musicplayer.data.update;

import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.function.IntConsumer;

/** The two network operations the updater needs, so the logic runs against a fake on the JVM. */
interface UpdateHttp {

    /** {@code body} is only read for a 200 (null otherwise); {@code etag} is the response's ETag header. */
    record Response(int code, @Nullable String etag, @Nullable String body) { }

    /** GET; sends If-None-Match when {@code etag} is given. Any HTTP status is a normal result, only I/O errors throw. */
    Response get(String url, @Nullable String etag) throws IOException;

    /** Streams {@code url} into {@code dest}, reporting whole percents (0-100) as they change. */
    void download(String url, File dest, IntConsumer percent) throws IOException;
}
