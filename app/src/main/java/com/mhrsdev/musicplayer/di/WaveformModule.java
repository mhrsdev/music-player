package com.mhrsdev.musicplayer.di;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import com.mhrsdev.musicplayer.data.waveform.FileWaveformStore;
import com.mhrsdev.musicplayer.data.waveform.MediaFingerprint;
import com.mhrsdev.musicplayer.data.waveform.WaveformExtractor;
import com.mhrsdev.musicplayer.data.waveform.WaveformRepositoryImpl;
import com.mhrsdev.musicplayer.data.waveform.WaveformStore;
import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Factory for the real waveform repository (wired into {@link AppContainer} by the integration task). Owns the
 * dedicated extraction thread: one thread, {@code THREAD_PRIORITY_BACKGROUND}, exits after 20 s idle.
 */
public final class WaveformModule {
    private WaveformModule() { }

    /** With the simple file-backed store under {@code filesDir/waveforms}. */
    public static WaveformRepositoryImpl create(Context context) {
        return create(context, new FileWaveformStore(new File(context.getFilesDir(), "waveforms")));
    }

    /** With the system's battery saver deciding whether background prefetch is skipped. */
    public static WaveformRepositoryImpl create(Context context, WaveformStore store) {
        PowerManager power = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return create(context, store, power::isPowerSaveMode);
    }

    /** With a caller-provided store (e.g. the SQLite-backed one) and battery-saver state (called on the worker too). */
    public static WaveformRepositoryImpl create(Context context, WaveformStore store, BooleanSupplier powerSave) {
        Context app = context.getApplicationContext();
        ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                runnable -> new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    runnable.run();
                }, "waveform-extractor"));
        worker.allowCoreThreadTimeOut(true);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        return new WaveformRepositoryImpl(store, new WaveformExtractor(app), new MediaFingerprint(app),
                powerSave, worker, mainHandler::post);
    }
}
