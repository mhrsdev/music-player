package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/**
 * One installable GitHub release. {@code sha256Hex} (GitHub's asset digest) and {@code sha256Url} (the {@code .sha256}
 * asset) are alternatives; at least one is always present, releases that cannot be verified are never offered.
 */
public record ReleaseInfo(String tag, boolean prerelease, String notes, String publishedAt, String apkUrl,
        long apkSize, @Nullable String sha256Url, @Nullable String sha256Hex) {

    /** The tag without its leading {@code v}, as shown to the user. */
    public String versionName() {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }
}
