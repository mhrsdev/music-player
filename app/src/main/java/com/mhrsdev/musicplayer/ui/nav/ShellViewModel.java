package com.mhrsdev.musicplayer.ui.nav;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;

/** Shell state: the selected tab and the playback snapshot that drives the mini-player. */
public final class ShellViewModel extends ViewModel {
    private final PlayerController player;
    private final MutableLiveData<ScreenNavigator.Tab> tab = new MutableLiveData<>(ScreenNavigator.Tab.HOME);

    ShellViewModel(PlayerController player) {
        this.player = player;
    }

    public LiveData<PlaybackState> getPlayback() {
        return player.getState();
    }

    public LiveData<ScreenNavigator.Tab> getTab() {
        return tab;
    }

    public void selectTab(ScreenNavigator.Tab t) {
        if (tab.getValue() != t) tab.setValue(t);
    }

    public void togglePlayPause() {
        player.togglePlayPause();
    }

    public void next() {
        player.next();
    }

    public void previous() {
        player.previous();
    }

    public void pause() {
        player.pause();
    }

    public static ViewModelProvider.Factory factory(PlayerController player) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new ShellViewModel(player));
            }
        };
    }
}
