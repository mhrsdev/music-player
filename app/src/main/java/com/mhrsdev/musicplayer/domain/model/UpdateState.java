package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/**
 * Where the update flow is. {@code release} is set for AVAILABLE, DOWNLOADING, VERIFYING, READY_TO_INSTALL and for an
 * ERROR that can be retried without checking again; {@code progress} (0-100) only for DOWNLOADING.
 */
public record UpdateState(Kind kind, @Nullable ReleaseInfo release, int progress, @Nullable ErrorKind error) {

    public enum Kind { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, VERIFYING, READY_TO_INSTALL, ERROR }

    /** NETWORK / RATE_LIMITED / BAD_RESPONSE: check failed. The rest: download or install failed. */
    public enum ErrorKind { NETWORK, RATE_LIMITED, BAD_RESPONSE, DOWNLOAD, CHECKSUM, INVALID_APK, DIFFERENT_SIGNATURE, INSTALL }

    public static final UpdateState IDLE = new UpdateState(Kind.IDLE, null, 0, null);
    public static final UpdateState CHECKING = new UpdateState(Kind.CHECKING, null, 0, null);
    public static final UpdateState UP_TO_DATE = new UpdateState(Kind.UP_TO_DATE, null, 0, null);

    public static UpdateState available(ReleaseInfo r) {
        return new UpdateState(Kind.AVAILABLE, r, 0, null);
    }

    public static UpdateState downloading(ReleaseInfo r, int percent) {
        return new UpdateState(Kind.DOWNLOADING, r, percent, null);
    }

    public static UpdateState verifying(ReleaseInfo r) {
        return new UpdateState(Kind.VERIFYING, r, 100, null);
    }

    public static UpdateState readyToInstall(ReleaseInfo r) {
        return new UpdateState(Kind.READY_TO_INSTALL, r, 100, null);
    }

    public static UpdateState error(ErrorKind kind, @Nullable ReleaseInfo r) {
        return new UpdateState(Kind.ERROR, r, 0, kind);
    }
}
