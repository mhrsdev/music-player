package com.mhrsdev.musicplayer.domain.repository;

import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import com.mhrsdev.musicplayer.domain.model.RestoreMode;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Export / import of the user's own data (likes, playlists with order, play counts and history, settings) as one
 * versioned JSON file. The caller owns the file access (SAF streams); each stream is closed by the service. Results
 * arrive on the main thread; failures are reported in {@link BackupSummary#error()}, never thrown.
 */
public interface BackupService {
    /** Writes the backup to {@code out}; the summary says what went in. */
    void export(OutputStream out, Callback<BackupSummary> done);

    /** Dry run: parses the file, matches it against the library and remembers the plan for {@link #restore}. */
    void preview(InputStream in, Callback<BackupSummary> done);

    /** Applies the plan of the last successful {@link #preview}; the plan is consumed. */
    void restore(RestoreMode mode, Callback<BackupSummary> done);
}
