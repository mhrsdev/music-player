package com.mhrsdev.musicplayer.data.update;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import com.mhrsdev.musicplayer.domain.model.UpdateState.ErrorKind;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.json.JSONException;

/**
 * Looks up releases on GitHub with ETag caching: an unchanged answer is a bodyless 304 (which does not count against
 * GitHub's rate limit), so a check costs almost nothing. The cache is keyed by URL, so switching "include
 * pre-releases" never reuses the other answer.
 */
final class UpdateChecker {
    private final UpdateHttp http;
    private final UpdateStore store;
    private final String latestUrl;
    private final String listUrl;

    /** @param repo {@code owner/name} of the GitHub repository that publishes the releases */
    UpdateChecker(UpdateHttp http, UpdateStore store, String repo) {
        this.http = http;
        this.store = store;
        latestUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
        listUrl = "https://api.github.com/repos/" + repo + "/releases?per_page=5";
    }

    String latestUrl() {
        return latestUrl;
    }

    String listUrl() {
        return listUrl;
    }

    private String url(boolean includePrereleases) {
        return includePrereleases ? listUrl : latestUrl;
    }

    /** Network (conditional) fetch; updates the cache on a 200. */
    List<ReleaseInfo> fetch(boolean includePrereleases) throws UpdateException {
        String url = url(includePrereleases);
        boolean cached = url.equals(store.cacheUrl()) && store.cacheEtag() != null && store.cacheBody() != null;
        UpdateHttp.Response r;
        try {
            r = http.get(url, cached ? store.cacheEtag() : null);
        } catch (IOException e) {
            throw new UpdateException(ErrorKind.NETWORK, e);
        }
        switch (r.code()) {
            case 200:
                List<ReleaseInfo> releases = parse(r.body());
                store.putCache(url, r.etag(), r.body());
                return releases;
            case 304:
                if (!cached) throw new UpdateException(ErrorKind.BAD_RESPONSE, "304 without a cache");
                return parse(store.cacheBody());
            case 404: // /latest answers 404 while there is no stable release
                store.putCache(null, null, null);
                return Collections.emptyList();
            case 403:
            case 429:
                throw new UpdateException(ErrorKind.RATE_LIMITED, "HTTP " + r.code());
            default:
                throw new UpdateException(ErrorKind.BAD_RESPONSE, "HTTP " + r.code());
        }
    }

    /** The last answer for this option without touching the network, or null if there is none. */
    @Nullable
    List<ReleaseInfo> cached(boolean includePrereleases) {
        if (!url(includePrereleases).equals(store.cacheUrl()) || store.cacheBody() == null) return null;
        try {
            return ReleaseParser.parse(store.cacheBody());
        } catch (JSONException e) {
            return null;
        }
    }

    /** The highest version above {@code current} (pre-releases only when allowed), or null. */
    @Nullable
    static ReleaseInfo newest(List<ReleaseInfo> releases, boolean includePrereleases, SemVer current) {
        ReleaseInfo best = null;
        SemVer bestVersion = current;
        for (ReleaseInfo r : releases) {
            SemVer v = SemVer.parse(r.tag());
            if (v == null || (r.prerelease() && !includePrereleases) || v.compareTo(bestVersion) <= 0) continue;
            best = r;
            bestVersion = v;
        }
        return best;
    }

    private static List<ReleaseInfo> parse(@Nullable String body) throws UpdateException {
        try {
            return ReleaseParser.parse(body == null ? "" : body);
        } catch (JSONException e) {
            throw new UpdateException(ErrorKind.BAD_RESPONSE, e);
        }
    }
}
