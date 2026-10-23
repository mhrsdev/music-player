package com.mhrsdev.musicplayer.data.update;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;

/** Persistent updater state: the user's options, the last successful check, the skipped tag and the ETag-cached response. */
interface UpdateStore {

    UpdateSettings settings();

    void putSettings(UpdateSettings settings);

    @Nullable
    String skippedTag();

    void putSkippedTag(@Nullable String tag);

    /** URL the cached ETag / body belong to (null when nothing is cached). */
    @Nullable
    String cacheUrl();

    @Nullable
    String cacheEtag();

    @Nullable
    String cacheBody();

    /** Pass nulls to drop the cache. */
    void putCache(@Nullable String url, @Nullable String etag, @Nullable String body);
}
