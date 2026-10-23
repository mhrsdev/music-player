package com.mhrsdev.musicplayer.data.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User settings on SharedPreferences. Every setting is a {@code LiveData} that is never empty (defaults apply) and
 * updates when its setter is called; setters may be called from any thread and persist asynchronously ({@code apply}).
 * Values are loaded once in the constructor, so reading {@code getValue()} from a background thread is fine.
 */
public final class SettingsStore {
    public static final int DEFAULT_SLEEP_TIMER_MINUTES = 30;

    private static final String PREFS = "settings";
    private static final String K_THEME = "theme";
    private static final String K_ARTWORK_COLORS = "artwork_colors";
    private static final String K_SEEK_BAR = "seek_bar_style";
    private static final String K_LANGUAGE = "language";
    private static final String K_LYRICS_ONLINE = "lyrics_online";
    private static final String K_LYRICS_SIZE = "lyrics_text_size";
    private static final String K_SLEEP_MINUTES = "sleep_timer_minutes";
    private static final String K_BATTERY_SAVER = "battery_saver";
    private static final String K_INCLUDE = "folders_include";
    private static final String K_EXCLUDE = "folders_exclude";

    private static final int MAX_BACKUP_STRING = 32;
    private static final Map<String, Class<?>> BACKUP_TYPES = Map.of(
            K_THEME, String.class, K_ARTWORK_COLORS, Boolean.class, K_SEEK_BAR, String.class, K_LANGUAGE, String.class,
            K_LYRICS_ONLINE, Boolean.class, K_LYRICS_SIZE, String.class, K_SLEEP_MINUTES, Integer.class,
            K_BATTERY_SAVER, String.class);

    private final SharedPreferences prefs;
    private final MutableLiveData<ThemeMode> theme;
    private final MutableLiveData<Boolean> artworkColors;
    private final MutableLiveData<SeekBarStyle> seekBarStyle;
    private final MutableLiveData<String> language;
    private final MutableLiveData<Boolean> lyricsOnline;
    private final MutableLiveData<LyricsTextSize> lyricsTextSize;
    private final MutableLiveData<Integer> sleepTimerMinutes;
    private final MutableLiveData<BatterySaverMode> batterySaver;
    private final MutableLiveData<Set<String>> includedFolders;
    private final MutableLiveData<Set<String>> excludedFolders;

    public SettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        theme = new MutableLiveData<>(readEnum(K_THEME, ThemeMode.SYSTEM));
        artworkColors = new MutableLiveData<>(prefs.getBoolean(K_ARTWORK_COLORS, true));
        seekBarStyle = new MutableLiveData<>(readEnum(K_SEEK_BAR, SeekBarStyle.WAVY));
        language = new MutableLiveData<>(prefs.getString(K_LANGUAGE, ""));
        lyricsOnline = new MutableLiveData<>(prefs.getBoolean(K_LYRICS_ONLINE, true));
        lyricsTextSize = new MutableLiveData<>(readEnum(K_LYRICS_SIZE, LyricsTextSize.MEDIUM));
        sleepTimerMinutes = new MutableLiveData<>(prefs.getInt(K_SLEEP_MINUTES, DEFAULT_SLEEP_TIMER_MINUTES));
        batterySaver = new MutableLiveData<>(readEnum(K_BATTERY_SAVER, BatterySaverMode.AUTO));
        includedFolders = new MutableLiveData<>(readSet(K_INCLUDE));
        excludedFolders = new MutableLiveData<>(readSet(K_EXCLUDE));
    }

    public LiveData<ThemeMode> theme() {
        return theme;
    }

    public void setTheme(ThemeMode value) {
        prefs.edit().putString(K_THEME, value.name()).apply();
        publish(theme, value);
    }

    /** Tint the player with colors taken from the artwork. */
    public LiveData<Boolean> artworkColors() {
        return artworkColors;
    }

    public void setArtworkColors(boolean value) {
        prefs.edit().putBoolean(K_ARTWORK_COLORS, value).apply();
        publish(artworkColors, value);
    }

    public LiveData<SeekBarStyle> seekBarStyle() {
        return seekBarStyle;
    }

    public void setSeekBarStyle(SeekBarStyle value) {
        prefs.edit().putString(K_SEEK_BAR, value.name()).apply();
        publish(seekBarStyle, value);
    }

    /** BCP-47 language tag, or the empty string to follow the system language. */
    public LiveData<String> language() {
        return language;
    }

    public void setLanguage(String languageTag) {
        prefs.edit().putString(K_LANGUAGE, languageTag).apply();
        publish(language, languageTag);
    }

    /** Whether missing lyrics may be fetched from LRCLIB (network, on demand, cached). */
    public LiveData<Boolean> lyricsOnline() {
        return lyricsOnline;
    }

    public void setLyricsOnline(boolean value) {
        prefs.edit().putBoolean(K_LYRICS_ONLINE, value).apply();
        publish(lyricsOnline, value);
    }

    public LiveData<LyricsTextSize> lyricsTextSize() {
        return lyricsTextSize;
    }

    public void setLyricsTextSize(LyricsTextSize value) {
        prefs.edit().putString(K_LYRICS_SIZE, value.name()).apply();
        publish(lyricsTextSize, value);
    }

    /** Minutes preselected when the sleep timer sheet opens. */
    public LiveData<Integer> sleepTimerMinutes() {
        return sleepTimerMinutes;
    }

    public void setSleepTimerMinutes(int minutes) {
        prefs.edit().putInt(K_SLEEP_MINUTES, minutes).apply();
        publish(sleepTimerMinutes, minutes);
    }

    public LiveData<BatterySaverMode> batterySaver() {
        return batterySaver;
    }

    public void setBatterySaver(BatterySaverMode value) {
        prefs.edit().putString(K_BATTERY_SAVER, value.name()).apply();
        publish(batterySaver, value);
    }

    /** Absolute directory paths the scanner is limited to; empty = the whole device. Changing it needs a rescan. */
    public LiveData<Set<String>> includedFolders() {
        return includedFolders;
    }

    public void setIncludedFolders(Set<String> folders) {
        prefs.edit().putStringSet(K_INCLUDE, new HashSet<>(folders)).apply();
        publish(includedFolders, Collections.unmodifiableSet(new HashSet<>(folders)));
    }

    /** Absolute directory paths the scanner skips (wins over includes). Changing it needs a rescan. */
    public LiveData<Set<String>> excludedFolders() {
        return excludedFolders;
    }

    public void setExcludedFolders(Set<String> folders) {
        prefs.edit().putStringSet(K_EXCLUDE, new HashSet<>(folders)).apply();
        publish(excludedFolders, Collections.unmodifiableSet(new HashSet<>(folders)));
    }

    /** The settings a backup carries (folder lists are device paths, so they stay out), by key. */
    public Map<String, Object> exportBackup() {
        Map<String, Object> all = new HashMap<>(prefs.getAll());
        all.keySet().retainAll(BACKUP_TYPES.keySet());
        return all;
    }

    /** Writes the known keys of {@code values} whose type matches, then refreshes every LiveData. Returns how many were applied. */
    public int importBackup(Map<String, Object> values) {
        SharedPreferences.Editor edit = prefs.edit();
        int applied = 0;
        for (Map.Entry<String, Class<?>> type : BACKUP_TYPES.entrySet()) {
            Object v = values.get(type.getKey());
            if (v == null || !type.getValue().isInstance(v)) continue;
            if (v instanceof String s) edit.putString(type.getKey(), s.length() <= MAX_BACKUP_STRING ? s : "");
            else if (v instanceof Boolean b) edit.putBoolean(type.getKey(), b);
            else edit.putInt(type.getKey(), (Integer) v);
            applied++;
        }
        edit.apply();
        publish(theme, readEnum(K_THEME, ThemeMode.SYSTEM));
        publish(artworkColors, prefs.getBoolean(K_ARTWORK_COLORS, true));
        publish(seekBarStyle, readEnum(K_SEEK_BAR, SeekBarStyle.WAVY));
        publish(language, prefs.getString(K_LANGUAGE, ""));
        publish(lyricsOnline, prefs.getBoolean(K_LYRICS_ONLINE, true));
        publish(lyricsTextSize, readEnum(K_LYRICS_SIZE, LyricsTextSize.MEDIUM));
        publish(sleepTimerMinutes, prefs.getInt(K_SLEEP_MINUTES, DEFAULT_SLEEP_TIMER_MINUTES));
        publish(batterySaver, readEnum(K_BATTERY_SAVER, BatterySaverMode.AUTO));
        return applied;
    }

    private <E extends Enum<E>> E readEnum(String key, E fallback) {
        try {
            String stored = prefs.getString(key, null);
            if (stored == null) return fallback;
            return Enum.valueOf(fallback.getDeclaringClass(), stored);
        } catch (IllegalArgumentException | ClassCastException removedValueOrOldType) {
            return fallback;
        }
    }

    private Set<String> readSet(String key) {
        return Collections.unmodifiableSet(new HashSet<>(prefs.getStringSet(key, Collections.emptySet())));
    }

    private static <T> void publish(MutableLiveData<T> live, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) live.setValue(value); else live.postValue(value);
    }
}
