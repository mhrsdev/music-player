package com.mhrsdev.musicplayer.domain.repository;

import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import java.util.List;

/**
 * The audio outputs that are connected right now, and the media volume of the active one. Main thread only.
 * Observing {@link #getDevices()} is what registers the system's device callback; with no observer nothing listens.
 */
public interface OutputDevices {

    /** Connected outputs, the active route first; refreshed when a device is plugged or removed. */
    LiveData<List<OutputDevice>> getDevices();

    /** Media volume step of the active output (Android keeps one volume per device), 0..{@link #getMaxVolume()}. */
    int getVolume();

    int getMaxVolume();

    void setVolume(int step);

    /** Re-reads the devices (e.g. after BLUETOOTH_CONNECT was granted). */
    void refresh();
}
