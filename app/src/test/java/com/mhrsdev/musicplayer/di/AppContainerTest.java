package com.mhrsdev.musicplayer.di;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import com.mhrsdev.musicplayer.data.lyrics.LyricsRepositoryImpl;
import com.mhrsdev.musicplayer.data.repository.SqliteLibraryRepository;
import com.mhrsdev.musicplayer.data.waveform.WaveformRepositoryImpl;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Smoke tests of the wiring: which implementations each mode binds, and that startup does no work. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AppContainerTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void fakesModeBindsFakesAndTheLibraryAdminIsTheSameObject() {
        AppContainer c = new AppContainer(context, true);
        assertTrue(c.library instanceof FakeLibraryRepository);
        assertSame(c.library, c.libraryAdmin());
        assertTrue(c.player instanceof FakePlayerController);
        assertNotNull(c.settings());
        c.onAudioPermissionGranted(); // no-ops on the fake
        c.onAppForeground();
    }

    @Test public void realModeBindsSqliteAndWaveformAndOpensNoDatabaseAtStartup() {
        AppContainer c = new AppContainer(context, false);
        assertTrue(c.library instanceof SqliteLibraryRepository);
        assertSame(c.library, c.libraryAdmin());
        assertTrue(c.lyrics instanceof LyricsRepositoryImpl);
        assertTrue(c.waveform instanceof WaveformRepositoryImpl);
        assertFalse(c.player instanceof FakePlayerController);
        assertFalse(context.getDatabasePath("musicplayer.db").exists());
    }

    @Test public void appFlagDefaultsToRealImplementations() {
        assertFalse(AppContainer.USE_FAKES);
    }
}
