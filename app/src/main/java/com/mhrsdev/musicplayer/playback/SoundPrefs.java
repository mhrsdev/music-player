package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.content.SharedPreferences;
import com.mhrsdev.musicplayer.domain.model.PlaybackOptions;
import com.mhrsdev.musicplayer.domain.model.ReplayGainMode;
import com.mhrsdev.musicplayer.domain.model.SoundState;

/** SharedPreferences ("sound") behind the audio effects and the playback options. Main thread; writes are async. */
class SoundPrefs {
    private final SharedPreferences prefs;

    SoundPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("sound", Context.MODE_PRIVATE);
    }

    boolean eqEnabled() { return prefs.getBoolean("eq_on", false); }
    void eqEnabled(boolean on) { prefs.edit().putBoolean("eq_on", on).apply(); }

    /** Index of the device preset, or {@link SoundState#PRESET_CUSTOM}. */
    int preset() { return prefs.getInt("preset", SoundState.PRESET_CUSTOM); }
    void preset(int p) { prefs.edit().putInt("preset", p).apply(); }

    /** The user's own curve (kept while a device preset is shown, so switching back restores it). */
    String customLevels() { return prefs.getString("custom_levels", ""); }
    void customLevels(String csv) { prefs.edit().putString("custom_levels", csv).apply(); }

    int bass() { return prefs.getInt("bass", 0); }
    void bass(int v) { prefs.edit().putInt("bass", v).apply(); }
    int virtualizer() { return prefs.getInt("virt", 0); }
    void virtualizer(int v) { prefs.edit().putInt("virt", v).apply(); }
    int loudnessMb() { return prefs.getInt("loud", 0); }
    void loudnessMb(int v) { prefs.edit().putInt("loud", v).apply(); }

    PlaybackOptions options() {
        ReplayGainMode mode;
        try {
            mode = ReplayGainMode.valueOf(prefs.getString("rg", ReplayGainMode.OFF.name()));
        } catch (IllegalArgumentException e) {
            mode = ReplayGainMode.OFF;
        }
        return new PlaybackOptions(PlaybackOptions.snap(prefs.getFloat("speed", 1f)),
                PlaybackOptions.snap(prefs.getFloat("pitch", 1f)), prefs.getBoolean("skip_silence", false), mode);
    }

    void options(PlaybackOptions o) {
        prefs.edit().putFloat("speed", o.speed()).putFloat("pitch", o.pitch())
                .putBoolean("skip_silence", o.skipSilence()).putString("rg", o.replayGain().name()).apply();
    }
}
