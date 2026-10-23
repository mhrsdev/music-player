package com.mhrsdev.musicplayer.ui.settings;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.SettingsFragmentBinding;
import com.mhrsdev.musicplayer.ui.settings.SettingsStore.SeekStyle;
import com.mhrsdev.musicplayer.ui.settings.SettingsStore.Theme;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import java.text.NumberFormat;
import java.util.Objects;

/** Settings (DESIGN.md 09). Applying theme/language is done here (View layer); the ViewModel only stores. */
public final class SettingsFragment extends Fragment {
    private SettingsFragmentBinding b;
    private SettingsViewModel vm;
    private TextView[] sleepChips;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = SettingsFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this, SettingsViewModel.factory(new PrefsSettingsStore(requireContext())))
                .get(SettingsViewModel.class);

        TextView[] themes = {b.themeSystem, b.themeLight, b.themeDark, b.themeBlack};
        for (int i = 0; i < themes.length; i++) {
            Theme t = Theme.values()[i];
            themes[i].setOnClickListener(v -> {
                Theme before = Objects.requireNonNull(vm.getState().getValue()).theme();
                if (before == t) return;
                vm.setTheme(t);
                ThemeApplier.applyNightMode(t);
                // Black is a separate theme resource (not just night mode), so switching to/from it recreates.
                if (before == Theme.BLACK || t == Theme.BLACK) requireActivity().recreate();
            });
        }
        TextView[] seeks = {b.seekWavy, b.seekWaveform, b.seekBars};
        for (int i = 0; i < seeks.length; i++) {
            SeekStyle s = SeekStyle.values()[i];
            seeks[i].setOnClickListener(v -> vm.setSeekStyle(s));
        }
        TextView[] langs = {b.langSystem, b.langEn, b.langFa};
        String[] tags = {SettingsStore.LANG_SYSTEM, "en", "fa"};
        for (int i = 0; i < langs.length; i++) {
            String tag = tags[i];
            langs[i].setOnClickListener(v -> {
                if (tag.equals(Objects.requireNonNull(vm.getState().getValue()).language())) return;
                vm.setLanguage(tag);
                ThemeApplier.applyLanguage(tag);
            });
        }

        NumberFormat nf = NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0));
        int[] presets = SettingsViewModel.SLEEP_PRESETS;
        sleepChips = new TextView[presets.length];
        for (int i = 0; i < presets.length; i++) {
            int m = presets[i];
            TextView chip = new TextView(new ContextThemeWrapper(requireContext(), R.style.Search_Chip), null, 0);
            chip.setText(m == 0 ? getString(R.string.settings_sleep_end) : getString(R.string.settings_sleep_minutes_f, nf.format(m)));
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Math.round(48 * getResources().getDisplayMetrics().density));
            lp.setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> vm.setSleepDefault(m));
            b.sleepRow.addView(chip);
            sleepChips[i] = chip;
        }

        b.artworkColors.setOnCheckedChangeListener((v, on) -> {
            vm.setArtworkColors(on);
            ThemeController.get(requireContext()).setArtworkColorsEnabled(on);
        });
        b.onlineLyrics.setOnCheckedChangeListener((v, on) -> vm.setOnlineLyrics(on));
        b.batterySaver.setOnCheckedChangeListener((v, on) -> vm.setBatterySaver(on));
        b.version.setText(getString(R.string.settings_version, versionName()));

        vm.getState().observe(getViewLifecycleOwner(), s -> {
            for (int i = 0; i < themes.length; i++) themes[i].setSelected(Theme.values()[i] == s.theme());
            for (int i = 0; i < seeks.length; i++) seeks[i].setSelected(SeekStyle.values()[i] == s.seekStyle());
            for (int i = 0; i < langs.length; i++) langs[i].setSelected(tags[i].equals(s.language()));
            for (int i = 0; i < presets.length; i++) sleepChips[i].setSelected(presets[i] == s.sleepDefaultMinutes());
            if (b.artworkColors.isChecked() != s.artworkColors()) b.artworkColors.setChecked(s.artworkColors());
            if (b.onlineLyrics.isChecked() != s.onlineLyrics()) b.onlineLyrics.setChecked(s.onlineLyrics());
            if (b.batterySaver.isChecked() != s.batterySaver()) b.batterySaver.setChecked(s.batterySaver());
        });
    }

    private String versionName() {
        try {
            PackageInfo info = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
