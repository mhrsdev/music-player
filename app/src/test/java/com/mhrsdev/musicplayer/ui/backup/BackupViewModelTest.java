package com.mhrsdev.musicplayer.ui.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import com.mhrsdev.musicplayer.domain.model.RestoreMode;
import com.mhrsdev.musicplayer.domain.repository.BackupService;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Rule;
import org.junit.Test;

public class BackupViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    /** Answers immediately with {@link #answer}; remembers the last restore mode. */
    static final class StubService implements BackupService {
        BackupSummary answer = new BackupSummary(2, 3, 4, 1, 5, BackupSummary.Error.NONE);
        RestoreMode restoredWith;

        @Override public void export(OutputStream out, Callback<BackupSummary> done) { done.onResult(answer); }
        @Override public void preview(InputStream in, Callback<BackupSummary> done) { done.onResult(answer); }
        @Override public void restore(RestoreMode mode, Callback<BackupSummary> done) {
            restoredWith = mode;
            done.onResult(answer);
        }
    }

    @Test public void previewThenRestoreWithChosenMode() {
        StubService service = new StubService();
        BackupViewModel vm = new BackupViewModel(service);
        vm.preview(new ByteArrayInputStream(new byte[0]));
        assertEquals(BackupViewModel.Phase.PREVIEW, vm.getState().getValue().phase());
        assertEquals(5, vm.getState().getValue().summary().unmatched());
        vm.setMode(RestoreMode.REPLACE);
        vm.restore();
        assertEquals(RestoreMode.REPLACE, service.restoredWith);
        assertEquals(BackupViewModel.Phase.RESTORED, vm.getState().getValue().phase());
        assertTrue(vm.takeSettingsChange());
        assertFalse(vm.takeSettingsChange());
    }

    @Test public void restoreWithoutPreviewDoesNothing() {
        StubService service = new StubService();
        BackupViewModel vm = new BackupViewModel(service);
        vm.restore();
        assertEquals(BackupViewModel.Phase.IDLE, vm.getState().getValue().phase());
        assertEquals(null, service.restoredWith);
    }

    @Test public void failureBecomesErrorState() {
        StubService service = new StubService();
        service.answer = BackupSummary.failed(BackupSummary.Error.CORRUPT);
        BackupViewModel vm = new BackupViewModel(service);
        vm.preview(new ByteArrayInputStream(new byte[0]));
        assertEquals(BackupViewModel.Phase.ERROR, vm.getState().getValue().phase());
        assertEquals(BackupSummary.Error.CORRUPT, vm.getState().getValue().summary().error());
    }
}
