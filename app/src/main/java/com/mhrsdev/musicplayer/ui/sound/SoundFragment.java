package com.mhrsdev.musicplayer.ui.sound;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.SoundFragmentBinding;
import com.mhrsdev.musicplayer.databinding.SoundSliderRowBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.PlaybackOptions;
import com.mhrsdev.musicplayer.domain.model.ReplayGainMode;
import com.mhrsdev.musicplayer.domain.model.SoundState;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Sound: equalizer with presets, bass / virtualizer / loudness, playback speed, skip silence and ReplayGain.
 * Opened by class name, so it keeps the public no-arg constructor. Observing the effects state is what attaches the
 * native effects (and probes the device); leaving the screen lets them go again.
 */
public class SoundFragment extends Fragment {
    private static final int LOUDNESS_STEP_MB = 50;
    private static final int SPEED_STEPS = Math.round((PlaybackOptions.MAX - PlaybackOptions.MIN) / PlaybackOptions.STEP);
    private static final Map<String, Integer> PRESET_NAMES = Map.of(
            "normal", R.string.sound_preset_normal, "classical", R.string.sound_preset_classical,
            "dance", R.string.sound_preset_dance, "flat", R.string.sound_preset_flat,
            "folk", R.string.sound_preset_folk, "heavy metal", R.string.sound_preset_heavy_metal,
            "hip hop", R.string.sound_preset_hip_hop, "jazz", R.string.sound_preset_jazz,
            "pop", R.string.sound_preset_pop, "rock", R.string.sound_preset_rock);

    private SoundFragmentBinding b;
    private SoundViewModel vm;
    private Slider bass;
    private Slider virtualizer;
    private Slider loudness;
    private Slider speed;
    private TextView[] presetChips = new TextView[0];
    @Nullable private List<String> shownPresets;
    private TextView[] speedChips;

    public SoundFragment() { }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = SoundFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, SoundViewModel.factory(c.audioEffects(), c.player)).get(SoundViewModel.class);

        b.eqSwitch.setOnCheckedChangeListener((v, on) -> vm.setEqualizerEnabled(on));
        b.equalizer.setListener(vm::setBand);
        bass = new Slider(b.bassSlot, R.string.sound_bass, SoundState.MAX_STRENGTH, vm::setBassBoost);
        virtualizer = new Slider(b.virtSlot, R.string.sound_virtualizer, SoundState.MAX_STRENGTH, vm::setVirtualizer);
        loudness = new Slider(b.loudSlot, R.string.sound_loudness, SoundState.MAX_LOUDNESS_MB / LOUDNESS_STEP_MB,
                p -> vm.setLoudnessGain(p * LOUDNESS_STEP_MB));
        speed = new Slider(b.speedSlot, R.string.sound_speed, SPEED_STEPS,
                p -> vm.setSpeed(PlaybackOptions.MIN + p * PlaybackOptions.STEP));

        NumberFormat nf = NumberFormat.getNumberInstance(locale());
        nf.setMaximumFractionDigits(2);
        float[] chips = SoundViewModel.SPEED_CHIPS;
        speedChips = new TextView[chips.length];
        for (int i = 0; i < chips.length; i++) {
            float speedValue = chips[i];
            speedChips[i] = addChip(b.speedChips, getString(R.string.sound_speed_value, nf.format(speedValue)),
                    v -> vm.setSpeed(speedValue));
        }
        b.skipSilence.setOnCheckedChangeListener((v, on) -> vm.setSkipSilence(on));
        TextView[] rg = {b.rgOff, b.rgTrack, b.rgAlbum};
        for (int i = 0; i < rg.length; i++) {
            ReplayGainMode mode = ReplayGainMode.values()[i]; // OFF, TRACK, ALBUM in the same order as the chips
            rg[i].setOnClickListener(v -> vm.setReplayGain(mode));
        }
        b.reset.setOnClickListener(v -> vm.reset());

        vm.getSound().observe(getViewLifecycleOwner(), this::renderSound);
        vm.getOptions().observe(getViewLifecycleOwner(), o -> {
            speed.set(Math.round((o.speed() - PlaybackOptions.MIN) / PlaybackOptions.STEP),
                    getString(R.string.sound_speed_value, nf.format(o.speed())));
            for (int i = 0; i < speedChips.length; i++) {
                speedChips[i].setSelected(Math.abs(chips[i] - o.speed()) < PlaybackOptions.STEP / 2);
            }
            if (b.skipSilence.isChecked() != o.skipSilence()) b.skipSilence.setChecked(o.skipSilence());
            for (int i = 0; i < rg.length; i++) rg[i].setSelected(ReplayGainMode.values()[i] == o.replayGain());
        });
    }

    private void renderSound(SoundState s) {
        b.unsupported.setVisibility(s.anySupported() ? View.GONE : View.VISIBLE);
        b.eqGroup.setVisibility(s.equalizerSupported() ? View.VISIBLE : View.GONE);
        b.effectsGroup.setVisibility(s.bassBoostSupported() || s.virtualizerSupported() || s.loudnessSupported()
                ? View.VISIBLE : View.GONE);
        b.bassSlot.setVisibility(s.bassBoostSupported() ? View.VISIBLE : View.GONE);
        b.virtSlot.setVisibility(s.virtualizerSupported() ? View.VISIBLE : View.GONE);
        b.loudSlot.setVisibility(s.loudnessSupported() ? View.VISIBLE : View.GONE);

        if (b.eqSwitch.isChecked() != s.equalizerEnabled()) b.eqSwitch.setChecked(s.equalizerEnabled());
        b.equalizer.setVisibility(s.bands().isEmpty() ? View.GONE : View.VISIBLE);
        int[] centers = new int[s.bands().size()];
        int[] levels = new int[centers.length];
        for (int i = 0; i < centers.length; i++) {
            centers[i] = s.bands().get(i).centerHz();
            levels[i] = s.bands().get(i).levelMb();
        }
        b.equalizer.setActiveLook(s.equalizerEnabled());
        b.equalizer.setBands(centers, levels, s.minLevelMb(), s.maxLevelMb());
        renderPresets(s);

        NumberFormat percent = NumberFormat.getPercentInstance(locale());
        bass.set(s.bassBoost(), percent.format(s.bassBoost() / (double) SoundState.MAX_STRENGTH));
        virtualizer.set(s.virtualizer(), percent.format(s.virtualizer() / (double) SoundState.MAX_STRENGTH));
        loudness.set(s.loudnessGainMb() / LOUDNESS_STEP_MB,
                getString(R.string.sound_db, EqCurve.formatDb(s.loudnessGainMb(), locale())));
    }

    /** Device presets plus "Custom"; the chips are rebuilt only when the preset list itself changes. */
    private void renderPresets(SoundState s) {
        if (!s.presets().equals(shownPresets)) {
            shownPresets = s.presets();
            b.presetRow.removeAllViews();
            presetChips = new TextView[s.presets().size() + 1];
            for (int i = 0; i < s.presets().size(); i++) {
                int index = i;
                presetChips[i] = addChip(b.presetRow, presetLabel(s.presets().get(i)), v -> vm.selectPreset(index));
            }
            presetChips[s.presets().size()] = addChip(b.presetRow, getString(R.string.sound_preset_custom),
                    v -> vm.selectPreset(SoundState.PRESET_CUSTOM));
        }
        int selected = s.preset() == SoundState.PRESET_CUSTOM ? presetChips.length - 1 : s.preset();
        for (int i = 0; i < presetChips.length; i++) presetChips[i].setSelected(i == selected);
        TextView chip = presetChips[selected];
        chip.post(() -> chip.requestRectangleOnScreen(new Rect(0, 0, chip.getWidth(), chip.getHeight()))); // reveal it in the scrolling row
    }

    private String presetLabel(String deviceName) {
        Integer res = PRESET_NAMES.get(deviceName.toLowerCase(Locale.ROOT).trim());
        return res == null ? deviceName : getString(res);
    }

    private TextView addChip(ViewGroup row, String text, View.OnClickListener click) {
        float d = getResources().getDisplayMetrics().density;
        TextView chip = new TextView(new ContextThemeWrapper(requireContext(), R.style.Search_Chip), null, 0);
        chip.setText(text);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                Math.round(48 * d));
        lp.setMarginEnd(Math.round(8 * d));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(click);
        row.addView(chip);
        return chip;
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
        shownPresets = null;
    }

    /** One title + value + native SeekBar row (slider semantics and 48 dp touch height come with SeekBar). */
    private class Slider {
        private final SoundSliderRowBinding row;

        Slider(FrameLayout slot, @StringRes int title, int max, IntConsumer onUser) {
            row = SoundSliderRowBinding.inflate(getLayoutInflater(), slot, true);
            row.title.setText(title);
            row.seek.setMax(max);
            row.seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) onUser.accept(progress);
                }

                @Override public void onStartTrackingTouch(SeekBar bar) { }

                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
        }

        void set(int progress, String valueText) {
            if (row.seek.getProgress() != progress) row.seek.setProgress(progress);
            row.value.setText(valueText);
            ViewCompat.setStateDescription(row.seek, valueText);
            row.seek.setContentDescription(row.title.getText());
        }
    }
}
