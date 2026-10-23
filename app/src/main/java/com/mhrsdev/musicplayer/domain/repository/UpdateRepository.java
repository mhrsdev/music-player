package com.mhrsdev.musicplayer.domain.repository;

import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;
import com.mhrsdev.musicplayer.domain.model.UpdateState;

/**
 * Self-update from GitHub Releases: check, download + verify, hand the APK to the system installer. All methods are
 * called on the main thread; work runs in the background and results arrive through the LiveData. There is no
 * background polling: checks happen on foreground (at most daily) or when the user asks.
 */
public interface UpdateRepository {

    LiveData<UpdateState> state();

    /** The release to advertise (banner), or null: none newer, skipped, or not checked yet. */
    LiveData<ReleaseInfo> availableUpdate();

    LiveData<UpdateSettings> settings();

    void setAutoCheck(boolean on);

    /** Changing it re-checks right away, because the answer depends on it. */
    void setIncludePrereleases(boolean on);

    void setWifiOnly(boolean on);

    /** Manual check: ignores the 24 h throttle, the Wi-Fi-only setting and a skipped version. */
    void checkNow();

    /** From AVAILABLE, or an ERROR that carries a release: downloads, verifies and moves to READY_TO_INSTALL. */
    void download();

    /** From READY_TO_INSTALL: the caller has checked that installs from this app are allowed. Opens the system dialog. */
    void install();

    /** Stops advertising the current release (until a newer one appears). */
    void skipVersion();

    /** Call on every foreground start: runs the automatic check when it is enabled and due. Cheap when it is not. */
    void checkIfDue();
}
