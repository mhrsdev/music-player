package com.mhrsdev.musicplayer.ui.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.data.settings.BatterySaverMode;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import java.io.File;
import java.util.concurrent.Executor;

/**
 * Settings → Battery, motion and Storage. Folder sizes are walked on the io executor (never on the main thread)
 * and only when the screen asks (no background work while Settings is closed).
 */
public class DeviceSettingsViewModel extends ViewModel {

    /** Bytes; -1 while computing. */
    public record Sizes(long cacheBytes, long databaseBytes) {
        static final Sizes COMPUTING = new Sizes(-1, -1);
    }

    private final SettingsStore settings;
    private final Executor io;
    private final File cacheDir;
    private final File databaseDir;
    private final MutableLiveData<Sizes> sizes = new MutableLiveData<>(Sizes.COMPUTING);

    DeviceSettingsViewModel(SettingsStore settings, Executor io, File cacheDir, File databaseDir) {
        this.settings = settings;
        this.io = io;
        this.cacheDir = cacheDir;
        this.databaseDir = databaseDir;
    }

    public LiveData<BatterySaverMode> getBatteryMode() { return settings.batterySaver(); }

    public void setBatteryMode(BatterySaverMode m) { settings.setBatterySaver(m); }

    public LiveData<Boolean> getReduceMotion() { return settings.reduceMotion(); }

    public void setReduceMotion(boolean on) { settings.setReduceMotion(on); }

    public LiveData<Sizes> getSizes() { return sizes; }

    public void refreshSizes() {
        io.execute(() -> sizes.postValue(new Sizes(sizeOf(cacheDir), sizeOf(databaseDir))));
    }

    /** Deletes everything under the app cache folder (rebuildable files only), then re-measures. */
    public void clearCache() {
        sizes.setValue(Sizes.COMPUTING);
        io.execute(() -> {
            File[] children = cacheDir.listFiles();
            if (children != null) for (File f : children) delete(f);
            sizes.postValue(new Sizes(sizeOf(cacheDir), sizeOf(databaseDir)));
        });
    }

    static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) total += sizeOf(c);
        return total;
    }

    private static void delete(File f) {
        File[] children = f.isDirectory() ? f.listFiles() : null;
        if (children != null) for (File c : children) delete(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    public static ViewModelProvider.Factory factory(SettingsStore settings, Executor io, File cacheDir, File databaseDir) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new DeviceSettingsViewModel(settings, io, cacheDir, databaseDir));
            }
        };
    }
}
