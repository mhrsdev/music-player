package com.mhrsdev.musicplayer.ui.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/** Settings screen state. Each setter persists to the store and publishes a new immutable snapshot. */
public final class SettingsViewModel extends ViewModel {

    public record State(SettingsStore.Theme theme, boolean artworkColors, SettingsStore.SeekStyle seekStyle,
            String language, boolean onlineLyrics, int sleepDefaultMinutes, boolean batterySaver) { }

    /** Sleep timer presets in minutes; 0 = end of track. */
    public static final int[] SLEEP_PRESETS = {15, 30, 45, 60, 0};

    private final SettingsStore store;
    private final MutableLiveData<State> state = new MutableLiveData<>();

    SettingsViewModel(SettingsStore store) {
        this.store = store;
        publish();
    }

    private void publish() {
        state.setValue(new State(store.getTheme(), store.isArtworkColors(), store.getSeekStyle(), store.getLanguage(),
                store.isOnlineLyrics(), store.getSleepDefaultMinutes(), store.isBatterySaver()));
    }

    public LiveData<State> getState() { return state; }

    public void setTheme(SettingsStore.Theme t) { store.setTheme(t); publish(); }

    public void setArtworkColors(boolean on) { store.setArtworkColors(on); publish(); }

    public void setSeekStyle(SettingsStore.SeekStyle s) { store.setSeekStyle(s); publish(); }

    public void setLanguage(String tag) { store.setLanguage(tag); publish(); }

    public void setOnlineLyrics(boolean on) { store.setOnlineLyrics(on); publish(); }

    public void setSleepDefault(int minutes) { store.setSleepDefaultMinutes(minutes); publish(); }

    public void setBatterySaver(boolean on) { store.setBatterySaver(on); publish(); }

    public static ViewModelProvider.Factory factory(SettingsStore store) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new SettingsViewModel(store));
            }
        };
    }
}
