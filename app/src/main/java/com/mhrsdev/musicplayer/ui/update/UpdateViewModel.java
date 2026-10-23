package com.mhrsdev.musicplayer.ui.update;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;
import com.mhrsdev.musicplayer.domain.model.UpdateState;
import com.mhrsdev.musicplayer.domain.repository.UpdateRepository;

/** Update screen: the repository's state and settings, and the user's intents. The flow itself lives in the repository. */
public final class UpdateViewModel extends ViewModel {
    private final UpdateRepository updates;

    UpdateViewModel(UpdateRepository updates) {
        this.updates = updates;
    }

    public LiveData<UpdateState> getState() { return updates.state(); }

    public LiveData<UpdateSettings> getSettings() { return updates.settings(); }

    public void checkNow() { updates.checkNow(); }

    public void download() { updates.download(); }

    public void install() { updates.install(); }

    public void skipVersion() { updates.skipVersion(); }

    public void setAutoCheck(boolean on) { updates.setAutoCheck(on); }

    public void setIncludePrereleases(boolean on) { updates.setIncludePrereleases(on); }

    public void setWifiOnly(boolean on) { updates.setWifiOnly(on); }

    public static ViewModelProvider.Factory factory(UpdateRepository updates) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new UpdateViewModel(updates));
            }
        };
    }
}
