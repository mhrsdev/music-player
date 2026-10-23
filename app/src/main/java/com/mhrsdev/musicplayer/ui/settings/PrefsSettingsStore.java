package com.mhrsdev.musicplayer.ui.settings;

import android.content.Context;
import com.mhrsdev.musicplayer.data.settings.BatterySaverMode;
import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;
import com.mhrsdev.musicplayer.data.settings.ThemeMode;
import com.mhrsdev.musicplayer.ui.nav.Screens;

/**
 * The UI's {@link SettingsStore} view of the app's single settings store ({@code AppContainer.settings()}, the
 * SharedPreferences-backed {@code data.settings.SettingsStore} that the player, lyrics and scanner read too).
 * Holds no state of its own, so any number of instances stay consistent.
 */
public final class PrefsSettingsStore implements SettingsStore {
    private final com.mhrsdev.musicplayer.data.settings.SettingsStore data;

    public PrefsSettingsStore(Context context) {
        this(Screens.container(context).settings());
    }

    PrefsSettingsStore(com.mhrsdev.musicplayer.data.settings.SettingsStore data) {
        this.data = data;
    }

    @Override public Theme getTheme() { return Theme.valueOf(data.theme().getValue().name()); }

    @Override public void setTheme(Theme theme) { data.setTheme(ThemeMode.valueOf(theme.name())); }

    @Override public boolean isArtworkColors() { return data.artworkColors().getValue(); }

    @Override public void setArtworkColors(boolean on) { data.setArtworkColors(on); }

    @Override public SeekStyle getSeekStyle() { return SeekStyle.valueOf(data.seekBarStyle().getValue().name()); }

    @Override public void setSeekStyle(SeekStyle s) { data.setSeekBarStyle(SeekBarStyle.valueOf(s.name())); }

    @Override public String getLanguage() { return data.language().getValue(); }

    @Override public void setLanguage(String tag) { data.setLanguage(tag); }

    @Override public boolean isOnlineLyrics() { return data.lyricsOnline().getValue(); }

    @Override public void setOnlineLyrics(boolean on) { data.setLyricsOnline(on); }

    @Override public int getSleepDefaultMinutes() { return data.sleepTimerMinutes().getValue(); }

    @Override public void setSleepDefaultMinutes(int m) { data.setSleepTimerMinutes(m); }

    /** The single switch means "force on"; off returns to AUTO (follow the system's battery saver). */
    @Override public boolean isBatterySaver() { return data.batterySaver().getValue() == BatterySaverMode.ON; }

    @Override public void setBatterySaver(boolean on) { data.setBatterySaver(on ? BatterySaverMode.ON : BatterySaverMode.AUTO); }
}
