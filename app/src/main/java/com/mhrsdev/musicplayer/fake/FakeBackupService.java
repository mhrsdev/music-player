package com.mhrsdev.musicplayer.fake;

import android.os.Handler;
import android.os.Looper;
import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import com.mhrsdev.musicplayer.domain.model.RestoreMode;
import com.mhrsdev.musicplayer.domain.repository.BackupService;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import java.io.InputStream;
import java.io.OutputStream;

/** Canned summaries for the UI development gallery and tests; writes nothing. */
public final class FakeBackupService implements BackupService {
    private static final BackupSummary SAMPLE = new BackupSummary(12, 340, 1280, 8, 5, BackupSummary.Error.NONE);
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void export(OutputStream out, Callback<BackupSummary> done) {
        main.post(() -> done.onResult(SAMPLE));
    }

    @Override public void preview(InputStream in, Callback<BackupSummary> done) {
        main.post(() -> done.onResult(SAMPLE));
    }

    @Override public void restore(RestoreMode mode, Callback<BackupSummary> done) {
        main.post(() -> done.onResult(SAMPLE));
    }
}
