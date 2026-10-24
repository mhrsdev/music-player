package com.mhrsdev.musicplayer.data.settings;

/**
 * Look of the player's seek bar. Stored by {@link #name()} under "seek_bar_style"; values are only ever appended
 * (D25), so old prefs and backups keep loading, and an unknown stored name falls back to {@link #WAVY}.
 */
public enum SeekBarStyle {
    WAVY, WAVEFORM, BARS, RIBBON, PULSE_DOTS, LED_LADDER, MOUNTAIN, BEADS, LIQUID;

    /**
     * True when the style draws the real loudness envelope. Decides whether the app decodes audio for it at all
     * (waveform prefetch); {@code ui.widget.SeekStyles.usesWaveform} must agree (SeekBarStyleTest).
     */
    public boolean usesWaveform() {
        return switch (this) {
            case WAVEFORM, BARS, PULSE_DOTS, LED_LADDER, MOUNTAIN, LIQUID -> true;
            default -> false;
        };
    }
}
