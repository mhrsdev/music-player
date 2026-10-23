package com.mhrsdev.musicplayer.ui.update;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.BuildConfig;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.UpdateFragmentBinding;
import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;
import com.mhrsdev.musicplayer.domain.model.UpdateState;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.text.NumberFormat;

/**
 * App updates: current version, check / download / install, release notes and the update options. Opened by class name
 * from Settings, so it needs the public no-arg constructor.
 */
public final class UpdateFragment extends Fragment {
    private UpdateFragmentBinding b;
    private UpdateViewModel vm;
    private boolean rendering;
    private boolean installAfterDownload;
    private final ActivityResultLauncher<Intent> unknownSources =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (canInstall()) vm.install();
            });

    public UpdateFragment() { }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = UpdateFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this, UpdateViewModel.factory(Screens.container(this).updates())).get(UpdateViewModel.class);
        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        b.currentVersion.setText(BuildConfig.VERSION_NAME);
        b.checkNow.setOnClickListener(v -> vm.checkNow());
        b.skip.setOnClickListener(v -> vm.skipVersion());
        b.autoCheck.setOnCheckedChangeListener((v, on) -> { if (!rendering) vm.setAutoCheck(on); });
        b.prereleases.setOnCheckedChangeListener((v, on) -> { if (!rendering) vm.setIncludePrereleases(on); });
        b.wifiOnly.setOnCheckedChangeListener((v, on) -> { if (!rendering) vm.setWifiOnly(on); });
        vm.getState().observe(getViewLifecycleOwner(), this::render);
        vm.getSettings().observe(getViewLifecycleOwner(), this::renderSettings);
    }

    private void render(UpdateState s) {
        ReleaseInfo r = s.release();
        boolean busy = s.kind() == UpdateState.Kind.CHECKING || s.kind() == UpdateState.Kind.DOWNLOADING
                || s.kind() == UpdateState.Kind.VERIFYING;
        b.checkNow.setEnabled(!busy);
        b.checkNow.setAlpha(busy ? 0.4f : 1f);
        b.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        b.progress.setIndeterminate(s.kind() != UpdateState.Kind.DOWNLOADING);
        b.progress.setProgress(s.progress());
        b.skip.setVisibility(s.kind() == UpdateState.Kind.AVAILABLE ? View.VISIBLE : View.GONE);

        @StringRes int primary = 0;
        switch (s.kind()) {
            case CHECKING:
                title(getString(R.string.update_status_checking), null);
                break;
            case UP_TO_DATE:
                title(getString(R.string.update_status_up_to_date), null);
                break;
            case AVAILABLE:
                title(getString(R.string.update_status_available, r.versionName()), sizeLine(r));
                primary = R.string.update_btn_download;
                break;
            case DOWNLOADING:
                title(getString(R.string.update_status_downloading,
                        NumberFormat.getPercentInstance().format(s.progress() / 100.0)), null);
                break;
            case VERIFYING:
                title(getString(R.string.update_status_verifying), null);
                break;
            case READY_TO_INSTALL:
                title(getString(R.string.update_status_ready), getString(R.string.update_sub_ready, r.versionName()));
                primary = R.string.update_btn_install;
                if (installAfterDownload) {
                    installAfterDownload = false;
                    install();
                }
                break;
            case ERROR:
                installAfterDownload = false;
                renderError(s.error());
                if (r != null) primary = R.string.update_btn_retry;
                break;
            default:
                title(getString(R.string.update_status_idle), null);
        }
        b.primaryAction.setVisibility(primary == 0 ? View.GONE : View.VISIBLE);
        if (primary != 0) {
            b.primaryAction.setText(primary);
            b.primaryAction.setOnClickListener(primary == R.string.update_btn_install ? v -> install() : v -> {
                installAfterDownload = true;
                vm.download();
            });
        }
        boolean notes = r != null && s.kind() != UpdateState.Kind.ERROR && !r.notes().trim().isEmpty();
        b.notesLabel.setVisibility(notes ? View.VISIBLE : View.GONE);
        b.notes.setVisibility(notes ? View.VISIBLE : View.GONE);
        if (notes) b.notes.setText(NotesText.plain(r.notes()));
    }

    private String sizeLine(ReleaseInfo r) {
        String size = Formatter.formatShortFileSize(requireContext(), r.apkSize());
        return getString(r.prerelease() ? R.string.update_sub_prerelease : R.string.update_sub_available, size);
    }

    private void renderError(UpdateState.ErrorKind kind) {
        switch (kind) {
            case NETWORK: title(getString(R.string.update_err_network), getString(R.string.update_err_network_sub)); break;
            case RATE_LIMITED: title(getString(R.string.update_err_rate), getString(R.string.update_err_rate_sub)); break;
            case BAD_RESPONSE: title(getString(R.string.update_err_bad), getString(R.string.update_err_bad_sub)); break;
            case DOWNLOAD: title(getString(R.string.update_err_download), getString(R.string.update_err_download_sub)); break;
            case CHECKSUM: title(getString(R.string.update_err_checksum), getString(R.string.update_err_checksum_sub)); break;
            case INVALID_APK: title(getString(R.string.update_err_invalid), getString(R.string.update_err_invalid_sub)); break;
            case DIFFERENT_SIGNATURE: title(getString(R.string.update_err_signature), getString(R.string.update_err_signature_sub)); break;
            default: title(getString(R.string.update_err_install), getString(R.string.update_err_install_sub));
        }
    }

    private void title(String title, @Nullable String sub) {
        b.statusTitle.setText(title);
        b.statusSub.setText(sub);
        b.statusSub.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderSettings(UpdateSettings s) {
        rendering = true;
        b.autoCheck.setChecked(s.autoCheck());
        b.prereleases.setChecked(s.includePrereleases());
        b.wifiOnly.setChecked(s.wifiOnly());
        rendering = false;
        b.lastChecked.setText(s.lastCheckedMs() == 0 ? getString(R.string.update_never_checked)
                : getString(R.string.update_last_checked, DateUtils.getRelativeTimeSpanString(s.lastCheckedMs(),
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)));
    }

    /** Installing needs "install unknown apps" for this app; if it is off, send the user to that system page and resume after. */
    private void install() {
        if (canInstall()) {
            vm.install();
            return;
        }
        Toast.makeText(requireContext(), R.string.update_allow_installs, Toast.LENGTH_LONG).show();
        unknownSources.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + requireContext().getPackageName())));
    }

    private boolean canInstall() {
        return requireContext().getPackageManager().canRequestPackageInstalls();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
