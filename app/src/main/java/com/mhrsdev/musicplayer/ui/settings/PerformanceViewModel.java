package com.mhrsdev.musicplayer.ui.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.data.settings.BatterySaverMode;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import java.util.function.UnaryOperator;

/**
 * Settings &gt; Performance. Stores only; the store's LiveData reaches {@code MotionPolicy} through the app-wide
 * observer, so every change is live everywhere without the screen doing anything else.
 */
public class PerformanceViewModel extends ViewModel {
    private final SettingsStore settings;

    PerformanceViewModel(SettingsStore settings) {
        this.settings = settings;
    }

    public LiveData<PerformanceProfile> getProfile() { return settings.performance(); }

    /** The preset picked for this phone on first run while the user has not touched Performance since, else null. */
    @androidx.annotation.Nullable
    public PerformanceProfile.Preset chosenForThisPhone() { return settings.performanceChosenForPhone(); }

    /** Only to refresh the "battery saving is on right now" note. */
    public LiveData<BatterySaverMode> getBatteryMode() { return settings.batterySaver(); }

    /** Sets every switch and the frame rate to the preset ({@link PerformanceProfile.Preset#CUSTOM} is not a choice). */
    public void choosePreset(PerformanceProfile.Preset p) {
        if (p == PerformanceProfile.Preset.CUSTOM) return;
        settings.setPerformance(PerformanceProfile.of(p));
    }

    /** One switch / the frame rate changed by hand; the result shows as Custom unless it equals a preset. */
    public void change(UnaryOperator<PerformanceProfile> edit) {
        PerformanceProfile now = settings.performance().getValue();
        PerformanceProfile next = edit.apply(now);
        if (!next.equals(now)) settings.setPerformance(next);
    }

    public static ViewModelProvider.Factory factory(SettingsStore settings) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new PerformanceViewModel(settings));
            }
        };
    }
}
