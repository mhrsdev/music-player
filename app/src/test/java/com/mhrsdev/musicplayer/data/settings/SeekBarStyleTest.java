package com.mhrsdev.musicplayer.data.settings;

import static org.junit.Assert.assertEquals;

import com.mhrsdev.musicplayer.ui.widget.SeekStyles;
import org.junit.Test;

public class SeekBarStyleTest {
    /** The data layer decides whether to decode audio for a style, the UI decides whether to draw it: they must agree. */
    @Test public void waveformRuleMatchesTheUiRule() {
        for (SeekBarStyle s : SeekBarStyle.values()) {
            assertEquals(s.name(), SeekStyles.usesWaveform(s), s.usesWaveform());
        }
    }
}
