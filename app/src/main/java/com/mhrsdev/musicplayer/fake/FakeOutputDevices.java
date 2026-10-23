package com.mhrsdev.musicplayer.fake;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import com.mhrsdev.musicplayer.domain.repository.OutputDevices;
import java.util.List;

/** In-memory {@link OutputDevices}: whatever list it is given, a media volume of 0..15. Main thread only. */
public class FakeOutputDevices implements OutputDevices {
    private final MutableLiveData<List<OutputDevice>> devices = new MutableLiveData<>(List.of());
    private int volume = 9;

    public FakeOutputDevices() { }

    public FakeOutputDevices(List<OutputDevice> initial) {
        devices.setValue(initial);
    }

    public void set(List<OutputDevice> list) {
        devices.setValue(list);
    }

    @Override public LiveData<List<OutputDevice>> getDevices() { return devices; }

    @Override public int getVolume() { return volume; }

    @Override public int getMaxVolume() { return 15; }

    @Override public void setVolume(int step) { volume = Math.max(0, Math.min(15, step)); }

    @Override public void refresh() { }
}
