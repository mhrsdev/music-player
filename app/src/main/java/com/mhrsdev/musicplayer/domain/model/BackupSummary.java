package com.mhrsdev.musicplayer.domain.model;

/**
 * What a backup file holds (export, dry-run) or what a restore did. {@code error != NONE} means nothing was written.
 *
 * @param unmatched distinct tracks of the file that are not in this device's library (skipped on restore)
 * @param settings  number of settings in the file
 */
public record BackupSummary(int playlists, int likes, int plays, int settings, int unmatched, Error error) {

    public enum Error { NONE, CORRUPT, NEWER_VERSION, NO_PREVIEW, IO }

    public static BackupSummary failed(Error error) {
        return new BackupSummary(0, 0, 0, 0, 0, error);
    }

    public boolean ok() {
        return error == Error.NONE;
    }
}
