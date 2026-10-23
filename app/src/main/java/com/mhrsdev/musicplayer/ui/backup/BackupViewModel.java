package com.mhrsdev.musicplayer.ui.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import com.mhrsdev.musicplayer.domain.model.RestoreMode;
import com.mhrsdev.musicplayer.domain.repository.BackupService;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/** State of the backup screen: idle -> (export | choose file -> dry-run preview -> restore). */
public final class BackupViewModel extends ViewModel {
    public enum Phase { IDLE, WORKING, PREVIEW, EXPORTED, RESTORED, ERROR }

    /** {@code summary} is null while IDLE / WORKING; in ERROR its {@code error} says why. */
    public record State(Phase phase, @Nullable BackupSummary summary, RestoreMode mode) { }

    private final BackupService service;
    private boolean settingsChanged;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(Phase.IDLE, null, RestoreMode.MERGE));

    BackupViewModel(BackupService service) {
        this.service = service;
    }

    public LiveData<State> getState() {
        return state;
    }

    public void export(OutputStream out) {
        if (!begin()) return;
        service.export(out, summary -> finish(summary, Phase.EXPORTED));
    }

    /** Dry run of the chosen file: nothing changes until {@link #restore()}. */
    public void preview(InputStream in) {
        if (!begin()) return;
        service.preview(in, summary -> finish(summary, Phase.PREVIEW));
    }

    public void setMode(RestoreMode mode) {
        State s = current();
        if (s.phase() == Phase.PREVIEW) state.setValue(new State(Phase.PREVIEW, s.summary(), mode));
    }

    public void restore() {
        State s = current();
        if (s.phase() != Phase.PREVIEW) return;
        RestoreMode mode = s.mode();
        begin();
        service.restore(mode, summary -> {
            settingsChanged = summary.ok() && summary.settings() > 0;
            finish(summary, Phase.RESTORED);
        });
    }

    /** True once after a restore that wrote settings, so the screen applies them exactly one time. */
    public boolean takeSettingsChange() {
        boolean changed = settingsChanged;
        settingsChanged = false;
        return changed;
    }

    /** The file could not even be opened (SAF returned nothing). */
    public void failed(BackupSummary.Error error) {
        state.setValue(new State(Phase.ERROR, BackupSummary.failed(error), current().mode()));
    }

    private boolean begin() {
        State s = current();
        if (s.phase() == Phase.WORKING) return false;
        state.setValue(new State(Phase.WORKING, null, s.mode()));
        return true;
    }

    private void finish(BackupSummary summary, Phase okPhase) {
        state.setValue(new State(summary.ok() ? okPhase : Phase.ERROR, summary, current().mode()));
    }

    private State current() {
        return Objects.requireNonNull(state.getValue());
    }

    public static ViewModelProvider.Factory factory(BackupService service) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new BackupViewModel(service));
            }
        };
    }
}
