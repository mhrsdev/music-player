package com.mhrsdev.musicplayer.ui.settings;

/**
 * User preferences the UI reads and writes. {@link PrefsSettingsStore} keeps them in SharedPreferences; the
 * player/lyrics tasks read the same keys through this interface. Values are plain enums/primitives.
 */
public interface SettingsStore {

    enum Theme { SYSTEM, LIGHT, DARK, BLACK }

    enum SeekStyle { WAVY, WAVEFORM, BARS }

    /** BCP-47 tag, or "" for the system language. */
    String LANG_SYSTEM = "";

    Theme getTheme();

    void setTheme(Theme theme);

    boolean isArtworkColors();

    void setArtworkColors(boolean on);

    SeekStyle getSeekStyle();

    void setSeekStyle(SeekStyle style);

    String getLanguage();

    void setLanguage(String tag);

    boolean isOnlineLyrics();

    void setOnlineLyrics(boolean on);

    /** Minutes; 0 means "end of track". */
    int getSleepDefaultMinutes();

    void setSleepDefaultMinutes(int minutes);

    boolean isBatterySaver();

    void setBatterySaver(boolean on);
}
