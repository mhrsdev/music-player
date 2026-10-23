package com.mhrsdev.musicplayer.ui.backup;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.BackupFragmentBinding;
import com.mhrsdev.musicplayer.databinding.BackupStatRowBinding;
import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import com.mhrsdev.musicplayer.domain.model.RestoreMode;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore;
import com.mhrsdev.musicplayer.ui.settings.ThemeApplier;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Backup &amp; restore screen. Public no-arg constructor: Settings opens it by class name. */
public final class BackupFragment extends Fragment {
    private BackupFragmentBinding b;
    private BackupViewModel vm;
    private NumberFormat numbers;

    private final ActivityResultLauncher<String> createDocument =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::onExportTarget);
    private final ActivityResultLauncher<String[]> openDocument =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onRestoreSource);

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = BackupFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this, BackupViewModel.factory(Screens.container(this).backup))
                .get(BackupViewModel.class);
        numbers = NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0));
        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.export.setOnClickListener(v -> createDocument.launch("music-player-backup-"
                + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".json"));
        b.choose.setOnClickListener(v -> openDocument.launch(new String[] {"*/*"}));
        b.modeMerge.setOnClickListener(v -> vm.setMode(RestoreMode.MERGE));
        b.modeReplace.setOnClickListener(v -> vm.setMode(RestoreMode.REPLACE));
        b.restore.setOnClickListener(v -> vm.restore());
        label(b.rowPlaylists, R.string.backup_row_playlists);
        label(b.rowLikes, R.string.backup_row_likes);
        label(b.rowPlays, R.string.backup_row_plays);
        label(b.rowSettings, R.string.backup_row_settings);
        label(b.rowUnmatched, R.string.backup_row_unmatched);
        vm.getState().observe(getViewLifecycleOwner(), this::render);
    }

    private void label(BackupStatRowBinding row, int res) {
        row.label.setText(res);
    }

    private void render(BackupViewModel.State s) {
        boolean working = s.phase() == BackupViewModel.Phase.WORKING;
        boolean preview = s.phase() == BackupViewModel.Phase.PREVIEW;
        BackupSummary sum = s.summary();
        b.export.setEnabled(!working);
        b.choose.setEnabled(!working);
        b.restore.setEnabled(!working);
        b.preview.setVisibility(preview ? View.VISIBLE : View.GONE);
        if (preview) {
            b.rowPlaylists.value.setText(numbers.format(sum.playlists()));
            b.rowLikes.value.setText(numbers.format(sum.likes()));
            b.rowPlays.value.setText(numbers.format(sum.plays()));
            b.rowSettings.value.setText(numbers.format(sum.settings()));
            b.rowUnmatched.value.setText(numbers.format(sum.unmatched()));
            b.rowUnmatched.value.setTextColor(Tokens.color(requireContext(),
                    sum.unmatched() > 0 ? R.attr.mpError : R.attr.mpOnSurface));
            int unmatchedVisibility = sum.unmatched() > 0 ? View.VISIBLE : View.GONE;
            b.unmatchedSub.setVisibility(unmatchedVisibility);
            b.modeMerge.setSelected(s.mode() == RestoreMode.MERGE);
            b.modeReplace.setSelected(s.mode() == RestoreMode.REPLACE);
            b.modeSub.setText(s.mode() == RestoreMode.MERGE ? R.string.backup_mode_merge_sub : R.string.backup_mode_replace_sub);
        }
        if (s.phase() == BackupViewModel.Phase.RESTORED && vm.takeSettingsChange()) applyRestoredSettings();
        String message = message(s);
        b.status.setVisibility(message == null ? View.GONE : View.VISIBLE);
        b.status.setText(message);
        b.status.setTextColor(Tokens.color(requireContext(),
                s.phase() == BackupViewModel.Phase.ERROR ? R.attr.mpError : R.attr.mpOnSurface));
    }

    @Nullable
    private String message(BackupViewModel.State s) {
        BackupSummary sum = s.summary();
        return switch (s.phase()) {
            case WORKING -> getString(R.string.backup_working);
            case EXPORTED -> getString(R.string.backup_done_export, numbers.format(sum.playlists()),
                    numbers.format(sum.likes()), numbers.format(sum.plays()));
            case RESTORED -> getString(R.string.backup_done_restore, numbers.format(sum.playlists()),
                    numbers.format(sum.likes()), numbers.format(sum.plays()));
            case ERROR -> getString(switch (sum.error()) {
                case NEWER_VERSION -> R.string.backup_err_newer;
                case CORRUPT -> R.string.backup_err_corrupt;
                case NO_PREVIEW -> R.string.backup_err_expired;
                default -> R.string.backup_err_io;
            });
            default -> null;
        };
    }

    private void onExportTarget(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "wt");
            if (out == null) throw new IOException("no stream");
            vm.export(out);
        } catch (IOException | RuntimeException e) {
            vm.failed(BackupSummary.Error.IO);
        }
    }

    private void onRestoreSource(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            InputStream in = requireContext().getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("no stream");
            vm.preview(in);
        } catch (IOException | RuntimeException e) {
            vm.failed(BackupSummary.Error.IO);
        }
    }

    /** Restored settings are already in the store; theme and language are applied by the View layer, like Settings does. */
    private void applyRestoredSettings() {
        PrefsSettingsStore prefs = new PrefsSettingsStore(requireContext());
        ThemeApplier.applyNightMode(prefs.getTheme());
        ThemeApplier.applyLanguage(prefs.getLanguage());
        requireActivity().recreate();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
