package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import java.io.IOException;

/** Minimal HTTP GET so the LRCLIB client can be tested without a network. */
public interface HttpFetcher {

    /** @return the response body, or null for 404; IOException for any other failure */
    @Nullable
    String get(String url, CancelToken cancel) throws IOException;
}
