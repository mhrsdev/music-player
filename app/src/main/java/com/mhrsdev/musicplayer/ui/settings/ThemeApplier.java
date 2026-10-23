package com.mhrsdev.musicplayer.ui.settings;

import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import com.mhrsdev.musicplayer.ui.theme.ThemeMode;

/** Applies theme mode and app language (View layer; ViewModels only store the choice). Delegates to T2's ThemeController. */
public final class ThemeApplier {
    private ThemeApplier() { }

    static ThemeMode toMode(SettingsStore.Theme theme) {
        return ThemeMode.valueOf(theme.name());
    }

    public static void applyNightMode(SettingsStore.Theme theme) {
        ThemeMode mode = toMode(theme);
        int before = AppCompatDelegate.getDefaultNightMode();
        if (theme == SettingsStore.Theme.SYSTEM && before == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) return;
        ThemeController.applyMode(mode);
    }

    @StyleRes
    public static int themeRes(SettingsStore.Theme theme) {
        return ThemeController.themeResFor(toMode(theme));
    }

    public static void applyLanguage(String tag) {
        AppCompatDelegate.setApplicationLocales(tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(tag));
    }
}
