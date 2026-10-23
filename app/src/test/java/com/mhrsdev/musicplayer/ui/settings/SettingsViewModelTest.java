package com.mhrsdev.musicplayer.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import org.junit.Rule;
import org.junit.Test;

public class SettingsViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    /** In-memory store. */
    static final class MemoryStore implements SettingsStore {
        Theme theme = Theme.SYSTEM;
        boolean artwork = true;
        SeekStyle seek = SeekStyle.WAVY;
        String lang = LANG_SYSTEM;
        boolean lyrics = true;
        int sleep = 30;
        boolean saver;

        @Override public Theme getTheme() { return theme; }
        @Override public void setTheme(Theme t) { theme = t; }
        @Override public boolean isArtworkColors() { return artwork; }
        @Override public void setArtworkColors(boolean on) { artwork = on; }
        @Override public SeekStyle getSeekStyle() { return seek; }
        @Override public void setSeekStyle(SeekStyle s) { seek = s; }
        @Override public String getLanguage() { return lang; }
        @Override public void setLanguage(String tag) { lang = tag; }
        @Override public boolean isOnlineLyrics() { return lyrics; }
        @Override public void setOnlineLyrics(boolean on) { lyrics = on; }
        @Override public int getSleepDefaultMinutes() { return sleep; }
        @Override public void setSleepDefaultMinutes(int m) { sleep = m; }
        @Override public boolean isBatterySaver() { return saver; }
        @Override public void setBatterySaver(boolean on) { saver = on; }
    }

    @Test public void settersPersistAndPublish() {
        MemoryStore store = new MemoryStore();
        SettingsViewModel vm = new SettingsViewModel(store);
        vm.setTheme(SettingsStore.Theme.BLACK);
        vm.setSeekStyle(SettingsStore.SeekStyle.BARS);
        vm.setLanguage("fa");
        vm.setBatterySaver(true);
        vm.setArtworkColors(false);
        vm.setSleepDefault(0);
        SettingsViewModel.State s = vm.getState().getValue();
        assertEquals(SettingsStore.Theme.BLACK, s.theme());
        assertEquals(SettingsStore.SeekStyle.BARS, s.seekStyle());
        assertEquals("fa", s.language());
        assertTrue(s.batterySaver());
        assertFalse(s.artworkColors());
        assertEquals(0, s.sleepDefaultMinutes());
        assertEquals(SettingsStore.Theme.BLACK, store.theme);
    }

    @Test public void themeEnumMapsToThemeMode() {
        for (SettingsStore.Theme t : SettingsStore.Theme.values()) {
            assertEquals(t.name(), ThemeApplier.toMode(t).name());
        }
    }
}
