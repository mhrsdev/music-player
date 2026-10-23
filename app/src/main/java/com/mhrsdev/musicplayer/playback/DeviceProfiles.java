package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.SoundProfile;
import java.util.ArrayList;
import java.util.List;

/**
 * Sound profiles per output device, in their own small preferences file ("device_profiles"): the value for a device
 * key is one line {@code eq;preset;l1,l2,..;bass;virt;loud;speed}. Unreadable lines read as "no profile".
 */
public class DeviceProfiles {
    private static final String FILE = "device_profiles";
    private static final String P = "p:", N = "n:";

    private final SharedPreferences prefs;

    public DeviceProfiles(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Nullable public SoundProfile get(String deviceKey) {
        return decode(prefs.getString(P + deviceKey, null));
    }

    public boolean has(String deviceKey) {
        return prefs.contains(P + deviceKey);
    }

    public void save(String deviceKey, @Nullable String deviceName, SoundProfile profile) {
        prefs.edit().putString(P + deviceKey, encode(profile)).putString(N + deviceKey, deviceName == null ? "" : deviceName).apply();
    }

    public void remove(String deviceKey) {
        prefs.edit().remove(P + deviceKey).remove(N + deviceKey).apply();
    }

    /** How many devices have a saved sound (the applier only listens for routes when this is above zero). */
    public int count() {
        int n = 0;
        for (String k : prefs.getAll().keySet()) if (k.startsWith(P)) n++;
        return n;
    }

    // --- codec (pure, unit-tested) ---------------------------------------------------------------

    static String encode(SoundProfile p) {
        StringBuilder levels = new StringBuilder();
        for (int l : p.bandLevelsMb()) {
            if (levels.length() > 0) levels.append(',');
            levels.append(l);
        }
        return (p.eqEnabled() ? 1 : 0) + ";" + p.preset() + ";" + levels + ";" + p.bass() + ";" + p.virtualizer() + ";"
                + p.loudnessMb() + ";" + p.speed();
    }

    @Nullable static SoundProfile decode(@Nullable String line) {
        if (line == null) return null;
        String[] f = line.split(";", -1);
        if (f.length != 7) return null;
        try {
            List<Integer> levels = new ArrayList<>();
            if (!f[2].isEmpty()) for (String l : f[2].split(",")) levels.add(Integer.parseInt(l));
            return new SoundProfile(f[0].equals("1"), Integer.parseInt(f[1]), List.copyOf(levels),
                    Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), Float.parseFloat(f[6]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
