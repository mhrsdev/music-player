package com.mhrsdev.musicplayer.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TreePathsTest {
    private static final String EXT = TreePaths.EXTERNAL_STORAGE_AUTHORITY;

    @Test public void primaryVolume() {
        assertEquals("/storage/emulated/0/Music/Rock", TreePaths.toPath(EXT, "primary:Music/Rock"));
        assertEquals("/storage/emulated/0", TreePaths.toPath(EXT, "primary:"));
        assertEquals("/storage/emulated/0/Music", TreePaths.toPath(EXT, "primary:Music/"));
    }

    @Test public void sdCardVolume() {
        assertEquals("/storage/1A2B-3C4D/Music", TreePaths.toPath(EXT, "1A2B-3C4D:Music"));
    }

    @Test public void unsupportedProvidersAreRejected() {
        assertNull(TreePaths.toPath("com.google.android.apps.docs.storage", "abc:def"));
        assertNull(TreePaths.toPath(EXT, "weird:Music"));
        assertNull(TreePaths.toPath(EXT, null));
        assertEquals("/storage/emulated/0/Download/x", TreePaths.toPath("com.android.providers.downloads.documents",
                "raw:/storage/emulated/0/Download/x"));
    }

    @Test public void labels() {
        assertEquals("Rock", TreePaths.label("/storage/emulated/0/Music/Rock"));
    }
}
