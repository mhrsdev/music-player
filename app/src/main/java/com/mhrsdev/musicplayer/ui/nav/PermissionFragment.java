package com.mhrsdev.musicplayer.ui.nav;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.NavPermissionBinding;

/** First run: explains why audio access is needed (DESIGN.md 13-states). "Denied for good" offers system settings. */
public final class PermissionFragment extends Fragment {

    /** Implemented by the host; called when the user granted access or chose to continue without it. */
    public interface Host {
        void onPermissionFlowDone();
    }

    private NavPermissionBinding b;
    private boolean askedOnce;

    private final ActivityResultLauncher<String> request =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    done();
                } else {
                    askedOnce = true;
                    render();
                }
            });

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = NavPermissionBinding.inflate(inflater, container, false);
        b.permFolders.setOnClickListener(v -> done());
        render();
        return b.getRoot();
    }

    @Override public void onResume() {
        super.onResume();
        if (AudioPermission.granted(requireContext())) done();
    }

    private void render() {
        boolean blocked = askedOnce && !shouldShowRequestPermissionRationale(AudioPermission.name());
        b.permDenied.setVisibility(blocked ? View.VISIBLE : View.GONE);
        b.permAllow.setText(blocked ? R.string.nav_perm_open_settings : R.string.nav_perm_allow);
        b.permAllow.setOnClickListener(v -> {
            if (blocked) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().getPackageName(), null)));
            } else {
                request.launch(AudioPermission.name());
            }
        });
    }

    private void done() {
        if (getActivity() instanceof Host) ((Host) getActivity()).onPermissionFlowDone();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
