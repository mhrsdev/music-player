package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArtworkLoaderTest {
    @Test public void sampleSizeKeepsBothSidesAtLeastMax() {
        assertEquals(1, ArtworkLoader.sampleSize(300, 300, 512));
        assertEquals(1, ArtworkLoader.sampleSize(900, 900, 512));
        assertEquals(2, ArtworkLoader.sampleSize(1024, 1024, 512));
        assertEquals(4, ArtworkLoader.sampleSize(3000, 2200, 512));
        assertEquals(1, ArtworkLoader.sampleSize(4000, 400, 512)); // thin image: do not shrink below max
    }
}
