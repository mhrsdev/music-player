package com.mhrsdev.musicplayer.ui.queue;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.SleepTimerSheetBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Sleep timer sheet: presets 5–90 min + "End of track" as chips (the running choice is highlighted), a custom
 * minutes stepper (±5) with Start, and Turn off. A tap applies immediately and closes the sheet.
 */
public final class SleepTimerSheet extends BottomSheetDialogFragment {
    public static final String TAG = "sleep_timer";
    static final int CUSTOM_STEP = 5, CUSTOM_MIN = 5, CUSTOM_MAX = 240;

    private SleepTimerSheetBinding b;
    private QueueViewModel vm;
    private int custom = 20;
    private NumberFormat nf;

    public static void show(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) new SleepTimerSheet().show(fm, TAG);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SleepTimerSheetBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onStart() {
        super.onStart();
        View sheet = getDialog() == null ? null : getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackground(null);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer c = ((MusicPlayerApp) requireActivity().getApplication()).getContainer();
        vm = new ViewModelProvider(this, QueueViewModel.factory(c.player, c.library)).get(QueueViewModel.class);
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        nf = NumberFormat.getIntegerInstance(loc);
        int def = c.settings().sleepTimerMinutes().getValue();
        if (def >= CUSTOM_MIN) custom = Math.min(CUSTOM_MAX, def);
        if (savedInstanceState != null) custom = savedInstanceState.getInt("custom", custom);

        int[] presets = QueueViewModel.SLEEP_PRESETS_MIN;
        for (int i = 0; i <= presets.length; i++) {
            LinearLayout row = i < 4 ? b.sleepRow1 : b.sleepRow2;
            boolean end = i == presets.length;
            TextView chip = chip(end ? getString(R.string.queue_sleep_end_of_track)
                    : getString(R.string.queue_sleep_preset_short, nf.format(presets[i])), end ? 2 : 1, row.getChildCount() > 0);
            chip.setTag(end ? 0 : presets[i]);
            final int minutes = end ? 0 : presets[i];
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (minutes == 0) vm.setSleepTimerEndOfTrack(); else vm.setSleepTimer(minutes);
                dismissAllowingStateLoss();
            });
            row.addView(chip);
        }
        b.sleepMinus.setOnClickListener(v -> stepCustom(-CUSTOM_STEP));
        b.sleepPlus.setOnClickListener(v -> stepCustom(CUSTOM_STEP));
        b.sleepStart.setOnClickListener(v -> {
            vm.setSleepTimer(custom);
            dismissAllowingStateLoss();
        });
        b.sleepOff.setOnClickListener(v -> {
            vm.cancelSleepTimer();
            dismissAllowingStateLoss();
        });
        renderCustom();
        vm.getUi().observe(getViewLifecycleOwner(), this::render);
    }

    private TextView chip(String text, float weight, boolean gap) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(52));
        t.setPadding(dp(6), dp(8), dp(6), dp(8));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(Tokens.color(requireContext(), R.attr.mpOnSurface));
        t.setBackgroundResource(R.drawable.sleep_chip_bg);
        t.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        if (gap) lp.setMarginStart(dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    private void stepCustom(int delta) {
        custom = clampCustom(custom + delta);
        b.sleepCustomValue.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        renderCustom();
    }

    static int clampCustom(int minutes) { return Math.max(CUSTOM_MIN, Math.min(CUSTOM_MAX, minutes)); }

    private void renderCustom() {
        b.sleepCustomValue.setText(getString(R.string.queue_sleep_preset, nf.format(custom)));
        b.sleepMinus.setEnabled(custom > CUSTOM_MIN);
        b.sleepMinus.setAlpha(custom > CUSTOM_MIN ? 1f : 0.38f);
        b.sleepPlus.setEnabled(custom < CUSTOM_MAX);
        b.sleepPlus.setAlpha(custom < CUSTOM_MAX ? 1f : 0.38f);
    }

    private void render(QueueViewModel.UiState ui) {
        SleepTimer.Kind k = ui.sleepKind();
        b.sleepStatus.setText(k == SleepTimer.Kind.COUNTDOWN ? getString(R.string.sleep_status_min, nf.format(ui.sleepMinutes()))
                : k == SleepTimer.Kind.END_OF_TRACK ? getString(R.string.sleep_status_end) : getString(R.string.sleep_status_off));
        b.sleepOff.setVisibility(k == SleepTimer.Kind.OFF ? View.GONE : View.VISIBLE);
        for (LinearLayout row : new LinearLayout[]{b.sleepRow1, b.sleepRow2}) {
            for (int i = 0; i < row.getChildCount(); i++) {
                View chip = row.getChildAt(i);
                boolean on = k == SleepTimer.Kind.END_OF_TRACK && Integer.valueOf(0).equals(chip.getTag());
                chip.setActivated(on);
                chip.setSelected(on);
            }
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("custom", custom);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroyView() {
        b = null;
        super.onDestroyView();
    }
}
