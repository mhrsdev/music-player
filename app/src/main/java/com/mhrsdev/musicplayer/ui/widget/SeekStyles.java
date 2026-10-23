package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;

import androidx.annotation.StringRes;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;

/**
 * The one place that maps a {@link SeekBarStyle} to its view, name, one-line description and whether it draws the
 * loudness envelope (only then does the player ask the WaveformRepository to extract it: CPU/battery).
 */
public class SeekStyles {
    private SeekStyles() {}

    public static BaseSeekView create(Context c, SeekBarStyle style) {
        return switch (style) {
            case WAVEFORM -> new WaveformSeekView(c);
            case BARS -> new BarsSeekView(c);
            case RIBBON -> new RibbonSeekView(c);
            case PULSE_DOTS -> new PulseDotsSeekView(c);
            case LED_LADDER -> new LedLadderSeekView(c);
            case MOUNTAIN -> new MountainSeekView(c);
            case BEADS -> new BeadsSeekView(c);
            case LIQUID -> new LiquidSeekView(c);
            default -> new WavySeekView(c);
        };
    }

    /** True when the style draws (or reacts to) the real loudness envelope. */
    public static boolean usesWaveform(SeekBarStyle style) {
        return switch (style) {
            case WAVEFORM, BARS, PULSE_DOTS, LED_LADDER, MOUNTAIN, LIQUID -> true;
            default -> false;
        };
    }

    /** True when the style has decorative motion while playing (shown as a hint in the picker). */
    public static boolean animates(SeekBarStyle style) {
        return switch (style) {
            case WAVY, PULSE_DOTS, LIQUID -> true;
            default -> false;
        };
    }

    @StringRes public static int nameRes(SeekBarStyle style) {
        return switch (style) {
            case WAVEFORM -> R.string.settings_seek_waveform;
            case BARS -> R.string.settings_seek_bars;
            case RIBBON -> R.string.seek_style_ribbon;
            case PULSE_DOTS -> R.string.seek_style_pulse;
            case LED_LADDER -> R.string.seek_style_ladder;
            case MOUNTAIN -> R.string.seek_style_mountain;
            case BEADS -> R.string.seek_style_beads;
            case LIQUID -> R.string.seek_style_liquid;
            default -> R.string.settings_seek_wavy;
        };
    }

    @StringRes public static int descRes(SeekBarStyle style) {
        return switch (style) {
            case WAVEFORM -> R.string.seek_style_waveform_desc;
            case BARS -> R.string.seek_style_bars_desc;
            case RIBBON -> R.string.seek_style_ribbon_desc;
            case PULSE_DOTS -> R.string.seek_style_pulse_desc;
            case LED_LADDER -> R.string.seek_style_ladder_desc;
            case MOUNTAIN -> R.string.seek_style_mountain_desc;
            case BEADS -> R.string.seek_style_beads_desc;
            case LIQUID -> R.string.seek_style_liquid_desc;
            default -> R.string.seek_style_wavy_desc;
        };
    }

    /** Static families for surfaces that cannot animate or host a View (home-screen widget progress). */
    public enum Family { PILL, SEGMENTS, THREAD }

    public static Family family(SeekBarStyle style) {
        return switch (style) {
            case BARS, PULSE_DOTS, LED_LADDER -> Family.SEGMENTS;
            case WAVY, WAVEFORM, MOUNTAIN, BEADS -> Family.THREAD;
            default -> Family.PILL;
        };
    }

    /** Stored name to style; unknown or null (removed value, foreign backup) falls back to {@link SeekBarStyle#WAVY}. */
    public static SeekBarStyle parse(String name) {
        if (name != null) {
            for (SeekBarStyle s : SeekBarStyle.values()) if (s.name().equals(name)) return s;
        }
        return SeekBarStyle.WAVY;
    }
}
