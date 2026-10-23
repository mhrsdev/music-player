package com.mhrsdev.musicplayer.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** SharedPreferences-backed {@link SettingsStore}. Cheap to construct; reads are in-memory after first load. */
public final class PrefsSettingsStore implements SettingsStore {
    private static final String FILE = "settings";
    private final SharedPreferences prefs;

    public PrefsSettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private <E extends Enum<E>> E readEnum(String key, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, prefs.getString(key, fallback.name()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override public Theme getTheme() { return readEnum("theme", Theme.class, Theme.SYSTEM); }

    @Override public void setTheme(Theme theme) { prefs.edit().putString("theme", theme.name()).apply(); }

    @Override public boolean isArtworkColors() { return prefs.getBoolean("artwork_colors", true); }

    @Override public void setArtworkColors(boolean on) { prefs.edit().putBoolean("artwork_colors", on).apply(); }

    @Override public SeekStyle getSeekStyle() { return readEnum("seek_style", SeekStyle.class, SeekStyle.WAVY); }

    @Override public void setSeekStyle(SeekStyle s) { prefs.edit().putString("seek_style", s.name()).apply(); }

    @Override public String getLanguage() { return prefs.getString("language", LANG_SYSTEM); }

    @Override public void setLanguage(String tag) { prefs.edit().putString("language", tag).apply(); }

    @Override public boolean isOnlineLyrics() { return prefs.getBoolean("online_lyrics", true); }

    @Override public void setOnlineLyrics(boolean on) { prefs.edit().putBoolean("online_lyrics", on).apply(); }

    @Override public int getSleepDefaultMinutes() { return prefs.getInt("sleep_default", 30); }

    @Override public void setSleepDefaultMinutes(int m) { prefs.edit().putInt("sleep_default", m).apply(); }

    @Override public boolean isBatterySaver() { return prefs.getBoolean("battery_saver", false); }

    @Override public void setBatterySaver(boolean on) { prefs.edit().putBoolean("battery_saver", on).apply(); }
}
