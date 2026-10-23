package com.mhrsdev.musicplayer.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.ScanResult;

/**
 * Library maintenance the screens need beyond {@link LibraryRepository}: scanning the device and the remaining
 * playlist edits. Fire-and-forget like the repository's writes; results arrive through the repository's LiveData.
 * Obtain it from {@code AppContainer.libraryAdmin()}.
 */
public interface LibraryAdmin {

    /** True while a scan runs (progress indicator). */
    LiveData<Boolean> isScanning();

    /** Manual rescan. {@code callback} (may be null) gets the result on the main thread; FAILED if one is already running. */
    void rescan(@Nullable Callback<ScanResult> callback);

    /** Scans only if the device's media changed since the last scan (or never scanned). Needs the audio permission. */
    void scanIfStale();

    void renamePlaylist(String playlistId, String name);

    void deletePlaylist(String playlistId);

    void removeFromPlaylist(String playlistId, String trackId);

    /** Moves the member at index {@code from} to index {@code to} (indices into the playlist's current order). */
    void moveInPlaylist(String playlistId, int from, int to);
}
