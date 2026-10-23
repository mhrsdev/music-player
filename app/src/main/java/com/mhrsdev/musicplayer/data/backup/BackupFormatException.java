package com.mhrsdev.musicplayer.data.backup;

import java.io.IOException;

/** The file is not a backup this app can read. */
public final class BackupFormatException extends IOException {
    /** True when the file is a valid backup from a newer app version. */
    public final boolean newerVersion;

    BackupFormatException(String message, boolean newerVersion, Throwable cause) {
        super(message, cause);
        this.newerVersion = newerVersion;
    }
}
