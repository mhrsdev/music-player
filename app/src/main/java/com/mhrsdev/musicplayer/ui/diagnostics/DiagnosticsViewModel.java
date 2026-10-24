package com.mhrsdev.musicplayer.ui.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.diag.Diagnostics;
import com.mhrsdev.musicplayer.diag.ExitRecord;
import com.mhrsdev.musicplayer.diag.PerfProbe;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Settings &gt; Diagnostics: the last exit, the last crash's first lines, Share and Clear. File reads run on the io
 * executor; the screen reloads when the store changes (e.g. the exit history arrives a moment after start).
 */
public class DiagnosticsViewModel extends ViewModel {
    /** What the screen shows; {@code lastExit} null = nothing recorded (or Android 10 and older). */
    public record Summary(@Nullable ExitRecord lastExit, long crashTime, @Nullable String crashExcerpt, int exitCount) {
        public boolean isEmpty() {
            return lastExit == null && crashExcerpt == null;
        }
    }

    static final int EXCERPT_LINES = 5;
    private static final int SHARE_MAX = 200 * 1024; // well under the binder limit of an Intent

    private final Diagnostics store;
    private final Executor io;
    private final String header;
    private final MutableLiveData<Summary> summary = new MutableLiveData<>();
    private final Observer<Integer> onChange = v -> reload();

    DiagnosticsViewModel(Diagnostics store, Executor io, String header) {
        this.store = store;
        this.io = io;
        this.header = header;
        Diagnostics.changes().observeForever(onChange); // delivers the current value at once: first load
    }

    public LiveData<Summary> getSummary() {
        return summary;
    }

    void reload() {
        io.execute(() -> {
            List<ExitRecord> exits = store.exits();
            String crash = store.lastCrash();
            summary.postValue(new Summary(exits.isEmpty() ? null : exits.get(0), store.lastCrashTime(),
                    crash == null ? null : excerpt(crash), exits.size()));
        });
    }

    /** Builds the share text (io thread) and marks everything as seen; {@code done} runs on the main thread. */
    public void share(Callback<String> done) {
        io.execute(() -> {
            String text = store.report(header);
            if (text.length() > SHARE_MAX) text = text.substring(0, SHARE_MAX) + "\n...";
            store.markSeen(System.currentTimeMillis());
            String result = text;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> done.onResult(result));
        });
    }

    /** Builds the performance report text (io thread); {@code done} runs on the main thread, with null when nothing was recorded. */
    public void sharePerf(Callback<String> done) {
        io.execute(() -> {
            String text = PerfProbe.report(header);
            if (text != null && text.length() > SHARE_MAX) text = text.substring(0, SHARE_MAX) + "\n...";
            String result = text;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> done.onResult(result));
        });
    }

    public void clear() {
        io.execute(store::clear); // clear() notifies: the screen reloads
    }

    /** The exception line(s) of a crash report: the first lines after the blank line that ends the header. */
    static String excerpt(String crash) {
        int start = crash.indexOf("\n\n");
        String body = start < 0 ? crash : crash.substring(start + 2);
        String[] lines = body.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(EXCERPT_LINES, lines.length); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i].trim());
        }
        return sb.toString();
    }

    @Override protected void onCleared() {
        Diagnostics.changes().removeObserver(onChange);
    }

    public static ViewModelProvider.Factory factory(Diagnostics store, Executor io, String header) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new DiagnosticsViewModel(store, io, header));
            }
        };
    }
}
