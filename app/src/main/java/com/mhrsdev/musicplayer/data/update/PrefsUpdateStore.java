package com.mhrsdev.musicplayer.data.update;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;

/** {@link UpdateStore} on its own SharedPreferences file. Defaults: auto-check on, pre-releases included (v0.x are all pre-releases). */
final class PrefsUpdateStore implements UpdateStore {
    private final SharedPreferences prefs;

    PrefsUpdateStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("updates", Context.MODE_PRIVATE);
    }

    @Override public UpdateSettings settings() {
        return new UpdateSettings(prefs.getBoolean("auto", true), prefs.getBoolean("pre", true),
                prefs.getBoolean("wifi_only", false), prefs.getLong("last_checked", 0));
    }

    @Override public void putSettings(UpdateSettings s) {
        prefs.edit().putBoolean("auto", s.autoCheck()).putBoolean("pre", s.includePrereleases())
                .putBoolean("wifi_only", s.wifiOnly()).putLong("last_checked", s.lastCheckedMs()).apply();
    }

    @Nullable @Override public String skippedTag() {
        return prefs.getString("skipped", null);
    }

    @Override public void putSkippedTag(@Nullable String tag) {
        prefs.edit().putString("skipped", tag).apply();
    }

    @Nullable @Override public String cacheUrl() {
        return prefs.getString("cache_url", null);
    }

    @Nullable @Override public String cacheEtag() {
        return prefs.getString("cache_etag", null);
    }

    @Nullable @Override public String cacheBody() {
        return prefs.getString("cache_body", null);
    }

    @Override public void putCache(@Nullable String url, @Nullable String etag, @Nullable String body) {
        prefs.edit().putString("cache_url", url).putString("cache_etag", etag).putString("cache_body", body).apply();
    }
}
