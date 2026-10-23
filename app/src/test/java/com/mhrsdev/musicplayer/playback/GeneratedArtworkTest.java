package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.concurrent.TimeUnit;

/** Notification / lock-screen artwork for a track without a cover is the app's generated cover (512 px RGB_565). */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class GeneratedArtworkTest {
    private static final Track NO_ART = new Track("7", "Grateful", "a1", "NEFFEX", "12", "Best of Me", 180_000L, null,
            3, "https://example.invalid/grateful.mp3", false, false, 0, 1, 0L);

    @Test public void queueItemMapsToTheAppsSpec() {
        MediaItem item = TrackMapper.toMediaItem(NO_ART);
        assertEquals(CoverSpec.forTrack(NO_ART), ArtworkLoader.coverSpec(item.mediaMetadata));
    }

    @Test public void foreignMetadataFallsBackToTitleAndArtist() {
        MediaMetadata m = new MediaMetadata.Builder().setTitle("Song").setArtist("Someone").build();
        assertEquals(CoverSpec.single("Song", "Someone"), ArtworkLoader.coverSpec(m));
    }

    @Test public void noArtworkGivesGeneratedBitmap() throws Exception {
        ArtworkLoader loader = new ArtworkLoader(RuntimeEnvironment.getApplication());
        Bitmap b = loader.loadBitmapFromMetadata(TrackMapper.toMediaItem(NO_ART).mediaMetadata).get(20, TimeUnit.SECONDS);
        assertEquals(ArtworkLoader.MAX_PX, b.getWidth());
        assertEquals(Bitmap.Config.RGB_565, b.getConfig());
        // cached: the second request is the same bitmap
        assertEquals(b, loader.loadBitmapFromMetadata(TrackMapper.toMediaItem(NO_ART).mediaMetadata).get(5, TimeUnit.SECONDS));
    }
}
