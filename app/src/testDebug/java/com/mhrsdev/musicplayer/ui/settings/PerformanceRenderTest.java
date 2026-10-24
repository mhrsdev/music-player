package com.mhrsdev.musicplayer.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

/**
 * Settings &gt; Performance (Q1, owner D26) rendered from the real Settings screen in the shell (fake library), cropped
 * to the section: docs/screens/q1-*.png. Dark English default (Full), Persian light, and a Custom mix with Reduce
 * motion on (effects paused) to show the disabled state.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class PerformanceRenderTest {

    @Test @Config(qualifiers = "w411dp-h5200dp-night-xhdpi")
    public void fullDark() throws Exception {
        View s = render(PerformanceProfile.FULL, "performance-dark");
        assertTrue(s.findViewById(R.id.perf_full).isSelected());
        assertEquals(View.GONE, s.findViewById(R.id.perf_custom).getVisibility());
    }

    @Test @Config(qualifiers = "fa-w411dp-h5600dp-notnight-xhdpi")
    public void balancedFaLight() throws Exception {
        View s = render(PerformanceProfile.BALANCED, "performance-fa-light");
        assertTrue(s.findViewById(R.id.perf_balanced).isSelected());
        assertEquals(View.LAYOUT_DIRECTION_RTL, s.getLayoutDirection());
    }

    @Test @Config(qualifiers = "w360dp-h5600dp-night-xhdpi")
    public void customReduceMotionDark() throws Exception {
        View s = render(PerformanceProfile.LIGHT.withLyricsWords(true).withReduceMotion(true), "performance-custom-360-dark");
        assertTrue(s.findViewById(R.id.perf_custom).isSelected());
        assertEquals(View.VISIBLE, s.findViewById(R.id.perf_effects_paused).getVisibility());
        assertTrue("word sweep is not paused by reduce motion", s.findViewById(R.id.perf_lyrics_words).isEnabled());
        assertTrue(!s.findViewById(R.id.perf_backdrop).isEnabled());
    }

    private View render(PerformanceProfile profile, String name) throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        ((MusicPlayerApp) app).getContainer().settings().setPerformance(profile);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        a.openTab(ScreenNavigator.Tab.SETTINGS);
        settle();
        View root = a.getWindow().getDecorView();
        float d = a.getResources().getDisplayMetrics().density;
        int w = Math.round(a.getResources().getConfiguration().screenWidthDp * d);
        int h = Math.round(a.getResources().getConfiguration().screenHeightDp * d);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        settle();
        View section = root.findViewById(R.id.performance);
        assertNotNull(section);
        int pad = Math.round(8 * d);
        Bitmap bmp = Bitmap.createBitmap(section.getWidth(), section.getHeight() + 2 * pad, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Tokens.color(a, R.attr.mpBackground));
        c.translate(0, pad);
        section.draw(c);
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "q1-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
        return section;
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(60);
        }
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(java.time.Duration.ofMillis(500));
    }
}
