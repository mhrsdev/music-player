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
    public static final int DEFAULT_MIN_TRACK_SECONDS = 30;

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
    private static final String K_NON_MUSIC = "include_non_music";
    private static final String K_DYNAMIC_COLOR = "dynamic_color";
    private static final String K_MIN_TRACK_SECONDS = "min_track_seconds";
    private static final String K_IMMERSIVE_PLAYER = "immersive_player";

    private static final int MAX_BACKUP_STRING = 32;
    private static final Map<String, Class<?>> BACKUP_TYPES = Map.of(
            K_THEME, String.class, K_ARTWORK_COLORS, Boolean.class, K_SEEK_BAR, String.class, K_LANGUAGE, String.class,
            K_LYRICS_ONLINE, Boolean.class, K_LYRICS_SIZE, String.class, K_SLEEP_MINUTES, Integer.class,
            K_BATTERY_SAVER, String.class, K_DYNAMIC_COLOR, Boolean.class);

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
    private final MutableLiveData<Boolean> includeNonMusic;
    private final MutableLiveData<Integer> minTrackSeconds;
    private final MutableLiveData<Boolean> immersivePlayer;
    private final MutableLiveData<PerformanceProfile> performance;

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
        includeNonMusic = new MutableLiveData<>(prefs.getBoolean(K_NON_MUSIC, false));
        minTrackSeconds = new MutableLiveData<>(prefs.getInt(K_MIN_TRACK_SECONDS, DEFAULT_MIN_TRACK_SECONDS));
        immersivePlayer = new MutableLiveData<>(prefs.getBoolean(K_IMMERSIVE_PLAYER, false));
        performance = new MutableLiveData<>(PerformancePrefs.read(prefs));
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

    /** Also scan ringtones, notifications, alarms and podcasts. Changing it needs a rescan. */
    public LiveData<Boolean> includeNonMusic() {
        return includeNonMusic;
    }

    public void setIncludeNonMusic(boolean value) {
        prefs.edit().putBoolean(K_NON_MUSIC, value).apply();
        publish(includeNonMusic, value);
    }

    /** The settings a backup carries (folder lists are device paths, so they stay out), by key. */
    public Map<String, Object> exportBackup() {
        Map<String, Object> all = new HashMap<>(prefs.getAll());
        all.keySet().removeIf(k -> !BACKUP_TYPES.containsKey(k) && !PerformancePrefs.BACKUP_TYPES.containsKey(k));
        return all;
    }

    /** Writes the known keys of {@code values} whose type matches, then refreshes every LiveData. Returns how many were applied. */
    public int importBackup(Map<String, Object> values) {
        SharedPreferences.Editor edit = prefs.edit();
        int applied = 0;
        Map<String, Class<?>> types = new HashMap<>(BACKUP_TYPES);
        types.putAll(PerformancePrefs.BACKUP_TYPES);
        for (Map.Entry<String, Class<?>> type : types.entrySet()) {
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
        publish(performance, PerformancePrefs.read(prefs));
        return applied;
    }

    /** Minimum track length in seconds the scanner keeps (0 = keep everything). Changing it needs a rescan. */
    public LiveData<Integer> minTrackSeconds() {
        return minTrackSeconds;
    }

    public void setMinTrackSeconds(int seconds) {
        prefs.edit().putInt(K_MIN_TRACK_SECONDS, Math.max(0, seconds)).apply();
        publish(minTrackSeconds, Math.max(0, seconds));
    }

    /**
     * Settings &gt; Performance: every effect switch and the frame-rate cap, including the app-level "reduce motion"
     * (decorative animation off regardless of the system setting).
     */
    public LiveData<PerformanceProfile> performance() {
        return performance;
    }

    public void setPerformance(PerformanceProfile value) {
        SharedPreferences.Editor e = prefs.edit();
        PerformancePrefs.write(e, value);
        e.apply();
        publish(performance, value);
    }

    /** Hide the status bar while the full player is open (swipe from the top shows it briefly). */
    public LiveData<Boolean> immersivePlayer() {
        return immersivePlayer;
    }

    public void setImmersivePlayer(boolean value) {
        prefs.edit().putBoolean(K_IMMERSIVE_PLAYER, value).apply();
        publish(immersivePlayer, value);
    }

    /** Material You wallpaper colours (API 31+) instead of the lapis identity; default off. Read at activity start. */
    public boolean isDynamicColor() {
        return prefs.getBoolean(K_DYNAMIC_COLOR, false);
    }

    public void setDynamicColor(boolean value) {
        prefs.edit().putBoolean(K_DYNAMIC_COLOR, value).apply();
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
