package com.mhrsdev.musicplayer.ui.diagnostics;

import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.text.NumberFormat;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.DiagnosticsFragmentBinding;
import com.mhrsdev.musicplayer.diag.Diagnostics;
import com.mhrsdev.musicplayer.diag.ExitRecord;
import com.mhrsdev.musicplayer.diag.PerfProbe;
import com.mhrsdev.musicplayer.ui.nav.Adaptive;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Settings &gt; Diagnostics (D30): why the app last closed, the last crash's first lines, Share and Clear. Opened
 * from Settings &gt; About and from the Home notice; needs the public no-arg constructor (re-created after process death).
 */
public class DiagnosticsFragment extends Fragment {
    public static final String TAG = "diagnostics";
    private DiagnosticsFragmentBinding b;
    private DiagnosticsViewModel vm;

    public DiagnosticsFragment() { }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = DiagnosticsFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this, DiagnosticsViewModel.factory(Diagnostics.get(requireContext()),
                Screens.container(this).ioExecutor, ExitText.header())).get(DiagnosticsViewModel.class);
        Adaptive.limitWidth(b.content, Adaptive.CONTENT_MAX_DP);
        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.share.setOnClickListener(v -> vm.share(text -> {
            if (isAdded()) ExitText.share(requireContext(), text);
        }));
        b.clear.setOnClickListener(v -> {
            vm.clear();
            Toast.makeText(requireContext(), R.string.diag_cleared, Toast.LENGTH_SHORT).show();
        });
        vm.getSummary().observe(getViewLifecycleOwner(), this::render);
        setupPerformance();
    }

    // ---- Performance card (PT1, D38) ----

    private void setupPerformance() {
        b.perfSwitch.setOnCheckedChangeListener((v, on) -> {
            if (on == PerfProbe.isRecording()) return; // a programmatic update from renderPerf
            if (on) PerfProbe.start(requireActivity(), new PerfAppState(Screens.container(this)));
            else PerfProbe.stop();
        });
        b.perfShare.setOnClickListener(v -> vm.sharePerf(text -> {
            if (!isAdded()) return;
            if (text == null) Toast.makeText(requireContext(), R.string.perf_nothing, Toast.LENGTH_SHORT).show();
            else ExitText.share(requireContext(), text, R.string.perf_share_subject, R.string.perf_share_chooser);
        }));
        b.perfClear.setOnClickListener(v -> {
            PerfProbe.clear();
            Toast.makeText(requireContext(), R.string.perf_cleared, Toast.LENGTH_SHORT).show();
        });
        PerfProbe.changes().observe(getViewLifecycleOwner(), n -> renderPerf());
    }

    private void renderPerf() {
        PerfProbe.Summary s = PerfProbe.summary();
        if (b.perfSwitch.isChecked() != s.recording()) b.perfSwitch.setChecked(s.recording());
        b.perfSummary.setVisibility(s.hasData() ? View.VISIBLE : View.GONE);
        boolean worst = s.hasData() && s.worstScreen() != null && s.worstPercent() >= 1;
        b.perfWorst.setVisibility(worst ? View.VISIBLE : View.GONE);
        if (s.hasData()) {
            b.perfSummary.setText(getString(s.recording() ? R.string.perf_summary_live : R.string.perf_summary_done,
                    DateUtils.formatElapsedTime(s.elapsedMs() / 1000), NumberFormat.getIntegerInstance().format(s.frames()),
                    percent(s.jankyPercent())));
        }
        if (worst) {
            // screen names are class names: keep them left-to-right inside Persian text
            b.perfWorst.setText(getString(R.string.perf_worst, "\u2066" + s.worstScreen() + "\u2069", percent(s.worstPercent())));
        }
        b.perfShare.setEnabled(s.hasData());
        b.perfShare.setAlpha(s.hasData() ? 1f : 0.4f);
        b.perfClear.setEnabled(s.hasData());
        b.perfClear.setAlpha(s.hasData() ? 1f : 0.4f);
    }

    private static String percent(double value) {
        NumberFormat nf = NumberFormat.getPercentInstance();
        nf.setMaximumFractionDigits(1);
        return nf.format(value / 100.0);
    }

    private void render(DiagnosticsViewModel.Summary s) {
        ExitRecord exit = s.lastExit();
        if (exit == null) {
            b.verdict.setText(Build.VERSION.SDK_INT >= 30 ? R.string.diag_none : R.string.diag_none_old_android);
            b.verdictSub.setVisibility(View.GONE);
            tintDot(R.attr.mpPrimary);
        } else {
            b.verdict.setText(ExitText.verdict(exit.kind()));
            b.verdictSub.setVisibility(View.VISIBLE);
            CharSequence when = DateUtils.getRelativeDateTimeString(requireContext(), exit.timestamp(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0);
            b.verdictSub.setText(s.exitCount() > 1
                    ? when + " · " + getString(R.string.diag_exits_count, s.exitCount()) : when);
            tintDot(ExitText.accent(exit.kind()));
        }
        boolean crash = s.crashExcerpt() != null;
        b.crashLabel.setVisibility(crash ? View.VISIBLE : View.GONE);
        b.crashCard.setVisibility(crash ? View.VISIBLE : View.GONE);
        if (crash) {
            b.crashTime.setText(DateUtils.getRelativeDateTimeString(requireContext(), s.crashTime(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
            b.crashExcerpt.setText(s.crashExcerpt());
        }
        b.clear.setEnabled(!s.isEmpty());
        b.clear.setAlpha(s.isEmpty() ? 0.4f : 1f);
    }

    private void tintDot(int attr) {
        b.verdictDot.setBackgroundTintList(ColorStateList.valueOf(Tokens.color(requireContext(), attr)));
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
