package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

/**
 * v0.3.1 (D29): decoded covers are capped to their size bucket (a 3000 px picture asked for a row no longer costs ~4x
 * the cache), and "no artwork" answers survive an app switch and a restart (no re-probing every cover-less row).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class ArtworkMemoryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void decodesAreCappedToTheirBucket() {
        Bitmap big = Bitmap.createBitmap(375, 375, Bitmap.Config.RGB_565); // 3000 px / 8, asked for 192
        Bitmap fitted = ArtworkLoader.fit(big, 192);
        assertEquals(192, fitted.getWidth());
        assertEquals(Bitmap.Config.RGB_565, fitted.getConfig());
        System.out.println("PERF row cover 3000 px source at bucket 192: " + big.getAllocationByteCount() + " -> "
                + fitted.getAllocationByteCount() + " bytes in the cache");
        Bitmap ok = Bitmap.createBitmap(220, 220, Bitmap.Config.RGB_565); // within 1.25 x: kept, no extra work
        assertSame(ok, ArtworkLoader.fit(ok, 192));
    }

    @Test public void missesSurviveTrimAndRestart() throws Exception {
        File f = new File(tmp.getRoot(), "misses.txt");
        ArtworkResolverTest.FakeBackend fb = new ArtworkResolverTest.FakeBackend();
        ArtworkLoader a = new ArtworkLoader(new ArtworkResolver(fb), null, f);
        String uri = "content://media/external/audio/media/77";
        a.load(uri, 96, (k, b) -> { });
        for (int i = 0; i < 200 && !a.isKnownMiss(uri); i++) {
            Thread.sleep(10);
            ShadowLooper.shadowMainLooper().idle();
        }
        assertTrue(a.isKnownMiss(uri));
        a.trim(); // what UI_HIDDEN does to bitmaps: the miss memory stays
        assertTrue(a.isKnownMiss(uri));
        a.saveMisses();
        for (int i = 0; i < 200 && !f.isFile(); i++) Thread.sleep(10);
        Thread.sleep(50);

        int probes = fb.embeddedCalls.get();
        ArtworkLoader restarted = new ArtworkLoader(new ArtworkResolver(fb), null, f);
        boolean[] answered = {false};
        restarted.load(uri, 96, (k, b) -> answered[0] = b == null);
        for (int i = 0; i < 200 && !answered[0]; i++) {
            Thread.sleep(10);
            ShadowLooper.shadowMainLooper().idle();
        }
        assertTrue(answered[0]);
        assertEquals("answered from the remembered miss, not re-probed", probes, fb.embeddedCalls.get());
    }
}
