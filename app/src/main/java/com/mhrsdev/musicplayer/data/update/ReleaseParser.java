package com.mhrsdev.musicplayer.data.update;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Maps the GitHub releases API (one release object, or an array of them) to {@link ReleaseInfo}. Skipped silently:
 * drafts, tags that are not versions, releases without a {@code MusicPlayer-v*.apk} asset on https, and releases that
 * offer no checksum (neither the {@code <apk>.sha256} asset nor the asset's {@code digest}).
 */
class ReleaseParser {
    private static final Pattern APK_NAME = Pattern.compile("MusicPlayer-v.+\\.apk");
    private static final String DIGEST_PREFIX = "sha256:";

    private ReleaseParser() { }

    static List<ReleaseInfo> parse(String json) throws JSONException {
        List<ReleaseInfo> out = new ArrayList<>();
        String t = json.trim();
        if (t.startsWith("[")) {
            JSONArray all = new JSONArray(t);
            for (int i = 0; i < all.length(); i++) add(out, all.getJSONObject(i));
        } else {
            add(out, new JSONObject(t));
        }
        return out;
    }

    private static void add(List<ReleaseInfo> out, JSONObject r) {
        if (r.optBoolean("draft")) return;
        String tag = r.optString("tag_name");
        SemVer version = SemVer.parse(tag);
        JSONArray assets = r.optJSONArray("assets");
        if (version == null || assets == null) return;

        JSONObject apk = null;
        for (int i = 0; i < assets.length() && apk == null; i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && APK_NAME.matcher(a.optString("name")).matches() && isHttps(a.optString("browser_download_url"))) apk = a;
        }
        if (apk == null) return;

        String sha256Url = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && (apk.optString("name") + ".sha256").equals(a.optString("name"))
                    && isHttps(a.optString("browser_download_url"))) sha256Url = a.optString("browser_download_url");
        }
        String digest = apk.optString("digest");
        String sha256Hex = digest.startsWith(DIGEST_PREFIX) && Sha256.isHex(digest.substring(DIGEST_PREFIX.length()))
                ? digest.substring(DIGEST_PREFIX.length()).toLowerCase(java.util.Locale.ROOT) : null;
        if (sha256Url == null && sha256Hex == null) return;

        out.add(new ReleaseInfo(tag, r.optBoolean("prerelease") || version.pre() != null, r.optString("body"),
                r.optString("published_at"), apk.optString("browser_download_url"), apk.optLong("size"), sha256Url, sha256Hex));
    }

    private static boolean isHttps(@Nullable String url) {
        return url != null && url.startsWith("https://");
    }
}
