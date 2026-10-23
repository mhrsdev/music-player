package com.mhrsdev.musicplayer.ui.headset;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.HeadsetDeviceRowBinding;
import com.mhrsdev.musicplayer.databinding.HeadsetFragmentBinding;
import com.mhrsdev.musicplayer.databinding.HeadsetGestureRowBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.HeadsetAction;
import com.mhrsdev.musicplayer.domain.model.HeadsetConfig;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.text.NumberFormat;
import java.util.List;

/**
 * Headset and output devices: what the earbud / headset button gestures do, connect and disconnect behaviour,
 * the connected outputs (picker, volume, sound saved per device) and the honest note about two Bluetooth devices.
 * Opened by class name from Settings, so it needs the public no-arg constructor.
 */
public class HeadsetFragment extends Fragment {
    private static final HeadsetAction[] ACTIONS = HeadsetAction.values();
    private static final int[] GESTURE_TITLES = {R.string.headset_gesture_single, R.string.headset_gesture_double,
            R.string.headset_gesture_triple, R.string.headset_gesture_long};

    private HeadsetFragmentBinding b;
    private HeadsetViewModel vm;
    private HeadsetGestureRowBinding[] gestureRows;
    private boolean rendering;
    private List<OutputDevice> devices = List.of();
    private final ActivityResultLauncher<String> btPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (vm != null) vm.refreshDevices();
            });

    public HeadsetFragment() { }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = HeadsetFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, HeadsetViewModel.factory(c.settings(), c.outputDevices(), c.deviceProfiles(),
                c.audioEffects(), c.player, c::startDeviceProfiles)).get(HeadsetViewModel.class);
        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        gestureRows = new HeadsetGestureRowBinding[GESTURE_TITLES.length];
        for (int i = 0; i < gestureRows.length; i++) {
            HeadsetGestureRowBinding row = HeadsetGestureRowBinding.inflate(getLayoutInflater(), b.gestures, false);
            row.gesture.setText(GESTURE_TITLES[i]);
            int index = i;
            row.getRoot().setOnClickListener(v -> pickAction(index));
            b.gestures.addView(row.getRoot());
            gestureRows[i] = row;
        }
        b.resumeConnect.setOnCheckedChangeListener((v, on) -> { if (!rendering) vm.setResumeOnConnect(on); });
        b.pauseDisconnect.setOnCheckedChangeListener((v, on) -> { if (!rendering) vm.setPauseOnDisconnect(on); });
        b.resumeCall.setOnCheckedChangeListener((v, on) -> { if (!rendering) vm.setResumeAfterCall(on); });
        b.switchOutput.setOnClickListener(v -> {
            if (!OutputSwitcher.openOutputPicker(requireContext())) {
                Toast.makeText(requireContext(), R.string.headset_switch_failed, Toast.LENGTH_LONG).show();
            }
        });
        b.dualOpen.setOnClickListener(v -> OutputSwitcher.openAudioSharing(requireContext()));
        b.btNames.setOnClickListener(v -> btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT));

        vm.getConfig().observe(getViewLifecycleOwner(), this::renderConfig);
        vm.getDevices().observe(getViewLifecycleOwner(), list -> { devices = list; renderDevices(); });
        vm.getProfileRevision().observe(getViewLifecycleOwner(), r -> renderDevices());
        vm.getSound().observe(getViewLifecycleOwner(), s -> { }); // keeps the effects probed for "save current sound"
    }

    @Override public void onResume() {
        super.onResume();
        if (vm != null) renderDevices(); // the media volume may have been changed with the hardware keys
    }

    private void renderConfig(HeadsetConfig cfg) {
        rendering = true;
        for (int i = 0; i < gestureRows.length; i++) {
            HeadsetAction a = i == 0 ? cfg.single() : i == 1 ? cfg.doublePress() : i == 2 ? cfg.triple() : cfg.longPress();
            gestureRows[i].action.setText(label(a));
        }
        b.resumeConnect.setChecked(cfg.resumeOnConnect());
        b.pauseDisconnect.setChecked(cfg.pauseOnDisconnect());
        b.resumeCall.setChecked(cfg.resumeAfterCall());
        rendering = false;
    }

    private void pickAction(int gestureIndex) {
        HeadsetConfig cfg = vm.getConfig().getValue();
        HeadsetAction now = cfg == null ? HeadsetAction.NONE : gestureIndex == 0 ? cfg.single()
                : gestureIndex == 1 ? cfg.doublePress() : gestureIndex == 2 ? cfg.triple() : cfg.longPress();
        String[] labels = new String[ACTIONS.length];
        int checked = 0;
        for (int i = 0; i < ACTIONS.length; i++) {
            labels[i] = getString(label(ACTIONS[i]));
            if (ACTIONS[i] == now) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(GESTURE_TITLES[gestureIndex])
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    vm.setGesture(gestureIndex, ACTIONS[which]);
                    d.dismiss();
                })
                .show();
    }

    @StringRes static int label(HeadsetAction a) {
        return switch (a) {
            case PLAY_PAUSE -> R.string.headset_action_play_pause;
            case NEXT -> R.string.headset_action_next;
            case PREVIOUS -> R.string.headset_action_previous;
            case SEEK_FORWARD -> R.string.headset_action_seek_forward;
            case SEEK_BACK -> R.string.headset_action_seek_back;
            case SHUFFLE -> R.string.headset_action_shuffle;
            case LIKE -> R.string.headset_action_like;
            case NONE -> R.string.headset_action_none;
        };
    }

    // --- output devices -------------------------------------------------------------------------

    private void renderDevices() {
        b.outputs.removeAllViews();
        b.outputsEmpty.setVisibility(devices.isEmpty() ? View.VISIBLE : View.GONE);
        b.switchOutput.setVisibility(devices.size() > 1 ? View.VISIBLE : View.GONE); // the picker only makes sense with a choice
        boolean namesHidden = false;
        for (OutputDevice d : devices) {
            namesHidden |= d.nameNeedsPermission();
            b.outputs.addView(deviceRow(d).getRoot());
        }
        b.btNames.setVisibility(namesHidden && Build.VERSION.SDK_INT >= 31 ? View.VISIBLE : View.GONE);
    }

    private HeadsetDeviceRowBinding deviceRow(OutputDevice d) {
        HeadsetDeviceRowBinding row = HeadsetDeviceRowBinding.inflate(getLayoutInflater(), b.outputs, false);
        String name = displayName(d);
        boolean saved = vm.hasProfile(d);
        row.icon.setImageResource(icon(d.kind()));
        row.name.setText(name);
        String status = getString(d.active() ? R.string.headset_playing_now : R.string.headset_connected);
        row.status.setText(saved ? status + " · " + getString(R.string.headset_sound_saved_tag) : status);
        row.getRoot().setContentDescription(null);
        if (d.active()) {
            bindVolume(row);
            row.save.setVisibility(View.VISIBLE);
            row.save.setText(saved ? R.string.headset_update_sound : R.string.headset_save_sound);
            row.save.setOnClickListener(v -> {
                vm.saveSound(d, d.name());
                Toast.makeText(requireContext(), getString(R.string.headset_sound_saved_toast, name), Toast.LENGTH_SHORT).show();
            });
        }
        if (saved) {
            row.remove.setVisibility(View.VISIBLE);
            row.remove.setOnClickListener(v -> vm.removeSound(d));
        }
        return row;
    }

    private void bindVolume(HeadsetDeviceRowBinding row) {
        int max = Math.max(1, vm.getMaxVolume());
        row.volumeBox.setVisibility(View.VISIBLE);
        row.volumeNote.setVisibility(View.VISIBLE);
        row.volume.setMax(max);
        row.volume.setProgress(vm.getVolume());
        NumberFormat percent = NumberFormat.getPercentInstance();
        row.volumeValue.setText(percent.format(vm.getVolume() / (double) max));
        row.volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                row.volumeValue.setText(percent.format(progress / (double) max));
                if (fromUser) vm.setVolume(progress);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private String displayName(OutputDevice d) {
        if (d.name() != null) return d.name();
        return getString(switch (d.kind()) {
            case SPEAKER, EARPIECE -> R.string.player_output_device;
            case WIRED -> R.string.player_output_headphones;
            case USB -> R.string.player_output_usb;
            case BLUETOOTH -> R.string.headset_kind_bluetooth;
            case HDMI -> R.string.headset_kind_hdmi;
            case OTHER -> R.string.headset_kind_other;
        });
    }

    @DrawableRes private static int icon(OutputDevice.Kind kind) {
        return switch (kind) {
            case BLUETOOTH -> R.drawable.player_ic_bluetooth;
            case WIRED, USB -> R.drawable.player_ic_headphones;
            default -> R.drawable.player_ic_speaker;
        };
    }
}
