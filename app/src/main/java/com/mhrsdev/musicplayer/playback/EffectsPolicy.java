package com.mhrsdev.musicplayer.playback;

/** Pure rules of {@link AudioEffectsEngine}: when effects are worth holding, flatness, level (de)serialisation. */
class EffectsPolicy {
    private EffectsPolicy() {}

    /**
     * Native effect objects are held only while someone looks at the sound screen, or while playing with at least
     * one effect actually changing the sound. Otherwise they are released (battery, RAM, no idle DSP in the mixer).
     */
    static boolean shouldHold(boolean observed, boolean playing, boolean anyEffectActive) {
        return observed || (playing && anyEffectActive);
    }

    static boolean isFlat(int[] levels) {
        for (int l : levels) if (l != 0) return false;
        return true;
    }

    /**
     * An effect that changes the sound: equalizer enabled and not flat (levels not probed yet, i.e. empty, count as
     * not flat), or a non-zero strength / gain.
     */
    static boolean anyActive(boolean eqEnabled, int[] eqLevels, int bass, int virtualizer, int loudnessMb) {
        return (eqEnabled && (eqLevels.length == 0 || !isFlat(eqLevels))) || bass > 0 || virtualizer > 0 || loudnessMb > 0;
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    static String formatLevels(int[] levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.length; i++) sb.append(i == 0 ? "" : ",").append(levels[i]);
        return sb.toString();
    }

    /** Parses {@link #formatLevels}; anything malformed or of another band count than {@code bandCount} -> all zero. */
    static int[] parseLevels(String csv, int bandCount, int min, int max) {
        int[] out = new int[bandCount];
        if (csv == null || csv.isEmpty()) return out;
        String[] parts = csv.split(",");
        if (parts.length != bandCount) return out;
        try {
            for (int i = 0; i < bandCount; i++) out[i] = clamp(Integer.parseInt(parts[i].trim()), min, max);
        } catch (NumberFormatException e) {
            return new int[bandCount];
        }
        return out;
    }
}
