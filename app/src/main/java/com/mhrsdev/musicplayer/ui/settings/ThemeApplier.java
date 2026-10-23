package com.mhrsdev.musicplayer.ui.settings;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Applies theme mode and app language. View-layer helper; ViewModels only store the choice. */
public final class ThemeApplier {
    private ThemeApplier() { }

    public static void applyNightMode(SettingsStore.Theme theme) {
        int mode = switch (theme) {
            case LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case DARK, BLACK -> AppCompatDelegate.MODE_NIGHT_YES;
            case SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static void applyLanguage(String tag) {
        AppCompatDelegate.setApplicationLocales(tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(tag));
    }
}
