package com.mhrsdev.musicplayer.ui.settings;

import android.content.Context;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile.Preset;
import com.mhrsdev.musicplayer.databinding.SettingsPerformanceBinding;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Binds Settings &gt; Performance ({@code layout/settings_performance}) to {@link PerformanceViewModel}. Kept out of
 * {@link SettingsFragment} so the screen only creates it. A preset rewrites every switch; touching a switch or the
 * frame rate by hand turns the selection into "Custom" (shown only then).
 */
class PerformanceSection {
    private final Fragment host;
    private final SettingsPerformanceBinding b;
    private final PerformanceViewModel vm;
    private final View[] presetRows;
    private final Preset[] presets = {Preset.FULL, Preset.BALANCED, Preset.LIGHT, Preset.CUSTOM};
    private final TextView[] fpsChips;
    private final int[] fpsSubs = {R.string.settings_perf_fps_screen_sub, R.string.settings_perf_fps_60_sub,
            R.string.settings_perf_fps_30_sub};

    /** One effect switch: its view, its subtitle, how it reads and writes the profile, and whether reduce motion pauses it. */
    private record Effect(SwitchCompat view, TextView sub, Predicate<PerformanceProfile> get,
            BiFunction<PerformanceProfile, Boolean, PerformanceProfile> set, boolean pausedByReduceMotion) { }

    private final Effect[] effects;

    PerformanceSection(Fragment host, SettingsPerformanceBinding b, PerformanceViewModel vm) {
        this.host = host;
        this.b = b;
        this.vm = vm;
        presetRows = new View[] {b.perfFull, b.perfBalanced, b.perfLight, b.perfCustom};
        fpsChips = new TextView[] {b.perfFpsScreen, b.perfFps60, b.perfFps30};
        effects = new Effect[] {
                new Effect(b.perfTransitions, b.perfTransitionsSub, PerformanceProfile::transitions,
                        PerformanceProfile::withTransitions, true),
                new Effect(b.perfBounce, b.perfBounceSub, PerformanceProfile::bounce, PerformanceProfile::withBounce, true),
                new Effect(b.perfBackdrop, b.perfBackdropSub, PerformanceProfile::movingBackdrop,
                        PerformanceProfile::withMovingBackdrop, true),
                new Effect(b.perfLyricsBlur, b.perfLyricsBlurSub, PerformanceProfile::lyricsBlur,
                        PerformanceProfile::withLyricsBlur, true),
                // The word sweep carries timing, so it keeps running under reduce motion (MotionPolicy).
                new Effect(b.perfLyricsWords, b.perfLyricsWordsSub, PerformanceProfile::lyricsWords,
                        PerformanceProfile::withLyricsWords, false),
                new Effect(b.perfSeek, b.perfSeekSub, PerformanceProfile::seekMotion, PerformanceProfile::withSeekMotion, true),
        };
    }

    void bind() {
        b.perfPresets.setContentDescription(host.getString(R.string.settings_perf_presets_cd));
        for (int i = 0; i < presetRows.length; i++) {
            Preset p = presets[i];
            View row = presetRows[i];
            row.setAccessibilityDelegate(RADIO);
            if (p == Preset.CUSTOM) {
                row.setClickable(false); // a state, not a choice: it appears once a switch is changed by hand
                continue;
            }
            row.setOnClickListener(v -> {
                if (v.isSelected()) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                vm.choosePreset(p);
            });
        }
        b.perfReduceMotion.setOnCheckedChangeListener((v, on) -> vm.change(pr -> pr.withReduceMotion(on)));
        for (Effect e : effects) {
            e.view().setOnCheckedChangeListener((CompoundButton v, boolean on) -> vm.change(pr -> e.set().apply(pr, on)));
        }
        for (int i = 0; i < fpsChips.length; i++) {
            int fps = PerformanceProfile.FPS_CHOICES[i];
            fpsChips[i].setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                vm.change(pr -> pr.withMaxFps(fps));
            });
        }
        b.perfTips.setText(tips());
        vm.getProfile().observe(host.getViewLifecycleOwner(), this::render);
        vm.getBatteryMode().observe(host.getViewLifecycleOwner(), m -> renderSystemNote());
    }

    /** The phone's own switches can change while Settings is in the background. */
    void onResume() {
        renderSystemNote();
    }

    private void render(PerformanceProfile p) {
        Preset current = p.preset();
        for (int i = 0; i < presetRows.length; i++) presetRows[i].setSelected(presets[i] == current);
        b.perfCustom.setVisibility(current == Preset.CUSTOM ? View.VISIBLE : View.GONE);

        setChecked(b.perfReduceMotion, p.reduceMotion());
        boolean blurPossible = Build.VERSION.SDK_INT >= 31;
        for (Effect e : effects) {
            setChecked(e.view(), e.get().test(p));
            boolean enabled = !(e.pausedByReduceMotion() && p.reduceMotion()) && (e.view() != b.perfLyricsBlur || blurPossible);
            e.view().setEnabled(enabled);
            e.sub().setAlpha(enabled ? 1f : 0.5f);
        }
        b.perfEffectsPaused.setVisibility(p.reduceMotion() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < fpsChips.length; i++) {
            boolean on = PerformanceProfile.FPS_CHOICES[i] == p.maxFps();
            fpsChips[i].setSelected(on);
            if (on) b.perfFpsSub.setText(fpsSubs[i]);
        }
        renderSystemNote();
    }

    /** Explains why nothing moves when the phone (not this screen) is holding motion back. */
    private void renderSystemNote() {
        Context c = host.requireContext();
        float scale = android.provider.Settings.Global.getFloat(c.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        int note = scale == 0f ? R.string.settings_perf_note_system
                : MotionPolicy.batterySaver(c) ? R.string.settings_perf_note_saver : 0;
        b.perfSystemNote.setVisibility(note == 0 ? View.GONE : View.VISIBLE);
        if (note != 0) b.perfSystemNote.setText(note);
    }

    /** Tips as bullet paragraphs with a hanging indent (BulletSpan is a leading margin, so RTL hangs correctly too). */
    private CharSequence tips() {
        String[] items = host.getResources().getStringArray(R.array.settings_perf_tips);
        int gap = Math.round(10 * host.getResources().getDisplayMetrics().density);
        int color = b.perfTips.getCurrentTextColor();
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (int i = 0; i < items.length; i++) {
            int start = out.length();
            out.append(items[i]);
            if (i < items.length - 1) out.append('\n');
            out.setSpan(new BulletSpan(gap, color), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    private static void setChecked(CompoundButton v, boolean on) {
        if (v.isChecked() != on) v.setChecked(on);
    }

    /** Preset rows announce as radio buttons ("selected" / "not selected", 1 of 3). */
    private static final View.AccessibilityDelegate RADIO = new View.AccessibilityDelegate() {
        @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(RadioButton.class.getName());
            info.setCheckable(true);
            info.setChecked(host.isSelected());
        }
    };
}
