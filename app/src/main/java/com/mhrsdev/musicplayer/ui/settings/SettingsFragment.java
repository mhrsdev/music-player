package com.mhrsdev.musicplayer.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.SettingsFragmentBinding;
import com.mhrsdev.musicplayer.databinding.SettingsRowBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import com.mhrsdev.musicplayer.ui.nav.Adaptive;
import com.mhrsdev.musicplayer.ui.nav.FeatureScreens;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.settings.SettingsStore.SeekStyle;
import com.mhrsdev.musicplayer.ui.settings.SettingsStore.Theme;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;

/**
 * Settings (DESIGN.md 09). Applying theme/language is done here (View layer); the ViewModels only store.
 * Library folders are picked with the system folder picker (Storage Access Framework) and stored as paths.
 */
public final class SettingsFragment extends Fragment {
    public static final String REPO_URL = "https://github.com/mhrsdev/music-player";
    private static final String STATE_PICK_EXCLUDE = "pick_exclude";

    private SettingsFragmentBinding b;
    private SettingsViewModel vm;
    private LibrarySettingsViewModel library;
    private TextView[] sleepChips;
    private SettingsRowBinding rescanRow;
    private NumberFormat nf;
    private boolean pickExclude;
    private boolean announceScan;

    private final ActivityResultLauncher<Uri> pickFolder =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::onFolderPicked);

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = SettingsFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (state != null) pickExclude = state.getBoolean(STATE_PICK_EXCLUDE);
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, SettingsViewModel.factory(new PrefsSettingsStore(requireContext())))
                .get(SettingsViewModel.class);
        library = new ViewModelProvider(this, LibrarySettingsViewModel.factory(c.settings(), c.libraryAdmin(), c.library))
                .get(LibrarySettingsViewModel.class);
        nf = NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0));
        Adaptive.limitWidth(b.settingsContent, Adaptive.CONTENT_MAX_DP);

        bindPreferences();
        bindFolders();
        bindRescan();
        bindFeatures();
        bindAbout();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_PICK_EXCLUDE, pickExclude);
    }

    // ---- appearance, language, player ----

    private void bindPreferences() {
        TextView[] themes = {b.themeSystem, b.themeLight, b.themeDark, b.themeBlack};
        for (int i = 0; i < themes.length; i++) {
            Theme t = Theme.values()[i];
            themes[i].setOnClickListener(v -> {
                Theme before = Objects.requireNonNull(vm.getState().getValue()).theme();
                if (before == t) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                vm.setTheme(t);
                ThemeApplier.applyNightMode(t);
                // Black is a separate theme resource (not just night mode), so switching to/from it recreates.
                if (before == Theme.BLACK || t == Theme.BLACK) requireActivity().recreate();
            });
        }
        TextView[] seeks = {b.seekWavy, b.seekWaveform, b.seekBars};
        for (int i = 0; i < seeks.length; i++) {
            SeekStyle s = SeekStyle.values()[i];
            seeks[i].setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                vm.setSeekStyle(s);
            });
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

        int[] presets = SettingsViewModel.SLEEP_PRESETS;
        sleepChips = new TextView[presets.length];
        for (int i = 0; i < presets.length; i++) {
            int m = presets[i];
            TextView chip = new TextView(new ContextThemeWrapper(requireContext(), R.style.Search_Chip), null, 0);
            chip.setText(m == 0 ? getString(R.string.settings_sleep_end) : getString(R.string.settings_sleep_minutes_f, nf.format(m)));
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48));
            lp.setMarginStart(dp(4));
            lp.setMarginEnd(dp(4));
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

    // ---- music folders ----

    private void bindFolders() {
        SettingsRowBinding add = row(b.foldersActions, R.drawable.settings_ic_add, getString(R.string.settings_folders_add),
                null, v -> launchPicker(false));
        tintPrimary(add);
        row(b.foldersActions, R.drawable.settings_ic_block, getString(R.string.settings_folders_exclude),
                getString(R.string.settings_folders_exclude_sub), v -> launchPicker(true));

        library.getIncluded().observe(getViewLifecycleOwner(), in -> renderFolders());
        library.getExcluded().observe(getViewLifecycleOwner(), ex -> renderFolders());
    }

    private void renderFolders() {
        List<String> in = library.getIncluded().getValue();
        List<String> ex = library.getExcluded().getValue();
        if (in == null || ex == null) return;
        b.foldersHint.setText(in.isEmpty() ? R.string.settings_folders_all : R.string.settings_folders_only);
        b.foldersList.removeAllViews();
        for (String path : in) folderRow(path, false);
        for (String path : ex) folderRow(path, true);
        b.foldersList.setVisibility(in.isEmpty() && ex.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void folderRow(String path, boolean excluded) {
        String label = folderLabel(path);
        SettingsRowBinding r = row(b.foldersList, excluded ? R.drawable.settings_ic_block : R.drawable.ic_mp_folder,
                label, excluded ? getString(R.string.settings_folder_skipped, path) : path, null);
        r.getRoot().setBackgroundResource(R.drawable.settings_folder_bg);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) r.getRoot().getLayoutParams();
        lp.bottomMargin = dp(8);
        r.rowEnd.setVisibility(View.VISIBLE);
        r.rowEnd.setImageResource(R.drawable.ic_mp_close);
        r.rowEnd.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        r.rowEnd.setContentDescription(getString(R.string.settings_folder_remove, label));
        r.rowEnd.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            library.removeFolder(path, excluded);
            releasePermission(path);
        });
    }

    private String folderLabel(String path) {
        return "/storage/emulated/0".equals(path) ? getString(R.string.settings_folder_internal) : TreePaths.label(path);
    }

    private void launchPicker(boolean exclude) {
        pickExclude = exclude;
        try {
            pickFolder.launch(null);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.settings_folders_no_picker, Toast.LENGTH_LONG).show();
        }
    }

    private void onFolderPicked(@Nullable Uri tree) {
        if (tree == null || b == null) return;
        String path = null;
        try {
            path = TreePaths.toPath(tree.getAuthority(), DocumentsContract.getTreeDocumentId(tree));
        } catch (IllegalArgumentException notATree) {
            // falls through to the message below
        }
        if (path == null) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_folders_unsupported_title)
                    .setMessage(R.string.settings_folders_unsupported)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        try {
            requireContext().getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The scanner reads MediaStore by path; the grant only keeps the folder reachable for later features.
        }
        boolean added = library.addFolder(path, pickExclude);
        Toast.makeText(requireContext(), added
                ? getString(pickExclude ? R.string.settings_folder_excluded_f : R.string.settings_folder_added_f, folderLabel(path))
                : getString(R.string.settings_folder_exists), Toast.LENGTH_SHORT).show();
    }

    /** Drops the persisted SAF grant(s) for a removed folder so the app does not keep unused access. */
    private void releasePermission(String path) {
        android.content.ContentResolver cr = requireContext().getContentResolver();
        for (android.content.UriPermission p : cr.getPersistedUriPermissions()) {
            Uri u = p.getUri();
            try {
                if (path.equals(TreePaths.toPath(u.getAuthority(), DocumentsContract.getTreeDocumentId(u)))) {
                    cr.releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (IllegalArgumentException | SecurityException ignored) {
                // not a tree grant / already gone
            }
        }
    }

    // ---- rescan ----

    private void bindRescan() {
        rescanRow = row(b.rescanCard, R.drawable.settings_ic_refresh, getString(R.string.settings_rescan), null, v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            announceScan = true;
            library.rescan();
        });
        library.getTrackCount().observe(getViewLifecycleOwner(), n -> renderRescan());
        library.isScanning().observe(getViewLifecycleOwner(), s -> renderRescan());
        library.getLastResult().observe(getViewLifecycleOwner(), r -> {
            renderRescan();
            if (announceScan && r != null && rescanRow != null) {
                announceScan = false;
                rescanRow.getRoot().announceForAccessibility(rescanRow.rowSub.getText());
            }
        });
    }

    private void renderRescan() {
        if (rescanRow == null) return;
        boolean scanning = Boolean.TRUE.equals(library.isScanning().getValue());
        Integer count = library.getTrackCount().getValue();
        int n = count == null ? 0 : count;
        ScanResult r = library.getLastResult().getValue();
        String songs = getResources().getQuantityString(R.plurals.settings_songs, n, nf.format(n));
        String sub;
        if (scanning) {
            sub = getString(R.string.settings_rescan_running);
        } else if (r == null) {
            sub = getString(R.string.settings_rescan_sub, songs);
        } else if (!r.ok()) {
            sub = getString(R.string.settings_rescan_failed);
        } else if (!r.changed()) {
            sub = getString(R.string.settings_rescan_nochange, songs);
        } else {
            sub = getString(R.string.settings_rescan_done, songs, nf.format(r.added()), nf.format(r.updated()),
                    nf.format(r.removed()));
        }
        rescanRow.rowSub.setVisibility(View.VISIBLE);
        rescanRow.rowSub.setText(sub);
        rescanRow.rowProgress.setVisibility(scanning ? View.VISIBLE : View.GONE);
        rescanRow.getRoot().setEnabled(!scanning);
    }

    // ---- feature screens (registry) + about ----

    private void bindFeatures() {
        List<FeatureScreens.Entry> entries = FeatureScreens.available(requireContext());
        b.featuresLabel.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        b.featuresCard.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        for (FeatureScreens.Entry e : entries) {
            SettingsRowBinding r = row(b.featuresCard, e.icon(), getString(e.title()), getString(e.subtitle()), v -> {
                if (!FeatureScreens.open(this, e)) {
                    Toast.makeText(requireContext(), R.string.settings_feature_unavailable, Toast.LENGTH_SHORT).show();
                }
            });
            chevron(r);
        }
    }

    private void bindAbout() {
        row(b.aboutCard, R.drawable.settings_ic_info, getString(R.string.app_name),
                getString(R.string.settings_version, versionName()), null);
        row(b.aboutCard, R.drawable.settings_ic_license, getString(R.string.settings_license),
                getString(R.string.settings_license_sub), null);
        SettingsRowBinding gh = row(b.aboutCard, R.drawable.settings_ic_open, getString(R.string.settings_source),
                REPO_URL.replace("https://", ""), v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(requireContext(), REPO_URL, Toast.LENGTH_LONG).show();
                    }
                });
        chevron(gh);
    }

    // ---- row helpers ----

    private SettingsRowBinding row(ViewGroup parent, @DrawableRes int icon, CharSequence title,
            @Nullable CharSequence sub, @Nullable View.OnClickListener click) {
        SettingsRowBinding r = SettingsRowBinding.inflate(getLayoutInflater(), parent, true);
        r.rowIcon.setImageResource(icon);
        r.rowTitle.setText(title);
        r.rowSub.setVisibility(sub == null ? View.GONE : View.VISIBLE);
        r.rowSub.setText(sub);
        if (click != null) {
            r.getRoot().setOnClickListener(click);
        } else {
            r.getRoot().setBackground(null);
            r.getRoot().setFocusable(true); // TalkBack reads title + subtitle as one item
        }
        return r;
    }

    private static void chevron(SettingsRowBinding r) {
        r.rowEnd.setVisibility(View.VISIBLE);
        r.rowEnd.setImageResource(R.drawable.ic_mp_chevron_right);
        r.rowEnd.setClickable(false);
        r.rowEnd.setFocusable(false);
        r.rowEnd.setBackground(null);
    }

    private void tintPrimary(SettingsRowBinding r) {
        ColorStateList primary = ColorStateList.valueOf(Tokens.color(requireContext(), R.attr.mpPrimary));
        r.rowIcon.setImageTintList(primary);
        r.rowTitle.setTextColor(primary);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
        rescanRow = null;
    }
}
