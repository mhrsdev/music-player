package com.mhrsdev.musicplayer.ui.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import com.mhrsdev.musicplayer.domain.repository.LibraryAdmin;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings → Library: the scanner's include/exclude folders and the manual rescan. Changing a folder list starts a
 * rescan right away so the library matches what the person sees (the store says a change needs one).
 */
public class LibrarySettingsViewModel extends ViewModel {

    private final SettingsStore settings;
    private final LibraryAdmin admin;
    private final LiveData<Integer> trackCount;
    private final LiveData<List<String>> included;
    private final LiveData<List<String>> excluded;
    private final MutableLiveData<ScanResult> lastResult = new MutableLiveData<>();

    LibrarySettingsViewModel(SettingsStore settings, LibraryAdmin admin, LibraryRepository library) {
        this.settings = settings;
        this.admin = admin;
        trackCount = Transformations.map(library.getAllTracks(), l -> l == null ? 0 : l.size());
        included = Transformations.map(settings.includedFolders(), LibrarySettingsViewModel::sorted);
        excluded = Transformations.map(settings.excludedFolders(), LibrarySettingsViewModel::sorted);
    }

    public LiveData<Integer> getTrackCount() { return trackCount; }

    public LiveData<List<String>> getIncluded() { return included; }

    public LiveData<List<String>> getExcluded() { return excluded; }

    public LiveData<Boolean> isScanning() { return admin.isScanning(); }

    /** Result of the last scan started here; null until one finished. */
    public LiveData<ScanResult> getLastResult() { return lastResult; }

    /** Chip choices for "Skip short audio", seconds (0 = keep everything). */
    public static final int[] MIN_LENGTH_CHOICES = {0, 10, 30, 60};

    public LiveData<Integer> getMinTrackSeconds() { return settings.minTrackSeconds(); }

    public void setMinTrackSeconds(int seconds) {
        Integer now = settings.minTrackSeconds().getValue();
        if (now != null && now == seconds) return;
        settings.setMinTrackSeconds(seconds);
        rescan();
    }

    public void rescan() {
        if (Boolean.TRUE.equals(admin.isScanning().getValue())) return;
        admin.rescan(lastResult::setValue);
    }

    /** Adds {@code path} to the include (or exclude) list; also drops it from the other list. False if already there. */
    public boolean addFolder(String path, boolean exclude) {
        Set<String> target = new HashSet<>(exclude ? value(settings.excludedFolders()) : value(settings.includedFolders()));
        Set<String> other = new HashSet<>(exclude ? value(settings.includedFolders()) : value(settings.excludedFolders()));
        if (!target.add(path)) return false;
        boolean otherChanged = other.remove(path);
        if (exclude) {
            settings.setExcludedFolders(target);
            if (otherChanged) settings.setIncludedFolders(other);
        } else {
            settings.setIncludedFolders(target);
            if (otherChanged) settings.setExcludedFolders(other);
        }
        rescan();
        return true;
    }

    public void removeFolder(String path, boolean exclude) {
        Set<String> set = new HashSet<>(exclude ? value(settings.excludedFolders()) : value(settings.includedFolders()));
        if (!set.remove(path)) return;
        if (exclude) settings.setExcludedFolders(set); else settings.setIncludedFolders(set);
        rescan();
    }

    private static Set<String> value(LiveData<Set<String>> live) {
        Set<String> v = live.getValue();
        return v == null ? Collections.emptySet() : v;
    }

    private static List<String> sorted(@Nullable Set<String> set) {
        List<String> l = new ArrayList<>(set == null ? Collections.emptySet() : set);
        Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public static ViewModelProvider.Factory factory(SettingsStore settings, LibraryAdmin admin, LibraryRepository library) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new LibrarySettingsViewModel(settings, admin, library));
            }
        };
    }
}
