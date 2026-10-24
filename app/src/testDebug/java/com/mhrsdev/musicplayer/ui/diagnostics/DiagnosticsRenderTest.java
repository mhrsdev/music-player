package com.mhrsdev.musicplayer.ui.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.diag.Diagnostics;
import com.mhrsdev.musicplayer.diag.DiagnosticsFixtures;
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
 * Settings &gt; Diagnostics and the Home "closed unexpectedly" notice (X1, D30), rendered from the real shell with a
 * seeded store: docs/screens/x1-*.png (dark English, Persian light, Home notice).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class DiagnosticsRenderTest {

    @Test @Config(qualifiers = "w412dp-h915dp-night-xhdpi")
    public void diagnosticsDark() throws Exception {
        MainActivity a = start(true);
        a.openFragment(new DiagnosticsFragment(), DiagnosticsFragment.TAG);
        View root = render(a, "diagnostics-dark");
        assertEquals(a.getString(R.string.diag_exit_crash), ((TextView) root.findViewById(R.id.verdict)).getText().toString());
        assertTrue(((TextView) root.findViewById(R.id.crash_excerpt)).getText().toString()
                .startsWith("java.lang.IllegalStateException: Cannot draw recycled bitmaps"));
    }

    @Test @Config(qualifiers = "fa-w412dp-h915dp-notnight-xhdpi")
    public void diagnosticsFaLight() throws Exception {
        MainActivity a = start(false);
        a.openFragment(new DiagnosticsFragment(), DiagnosticsFragment.TAG);
        View root = render(a, "diagnostics-fa-light");
        assertEquals(a.getString(R.string.diag_exit_low_memory),
                ((TextView) root.findViewById(R.id.verdict)).getText().toString());
        assertEquals(View.GONE, root.findViewById(R.id.crash_card).getVisibility());
    }

    @Test @Config(qualifiers = "fa-w412dp-h915dp-night-xhdpi")
    public void homeNoticeFaDark() throws Exception {
        MainActivity a = start(true);
        View root = render(a, "home-notice-fa-dark");
        DiagnosticsNotice notice = find((ViewGroup) root);
        assertNotNull(notice);
        assertTrue("an unseen crash shows the notice", notice.isShowing());
    }

    private static DiagnosticsNotice find(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof DiagnosticsNotice n) return n;
            if (v instanceof ViewGroup c) {
                DiagnosticsNotice n = find(c);
                if (n != null) return n;
            }
        }
        return null;
    }

    private static MainActivity start(boolean crash) throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        Diagnostics store = Diagnostics.get(app);
        if (crash) DiagnosticsFixtures.seed(store, System.currentTimeMillis());
        else DiagnosticsFixtures.seedLowMemoryOnly(store, System.currentTimeMillis());
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        return a;
    }

    private static View render(MainActivity a, String name) throws Exception {
        settle();
        View root = a.getWindow().getDecorView();
        int w = 412 * 2, h = 915 * 2;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        settle();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Tokens.color(a, R.attr.mpBackground));
        root.draw(c);
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "x1-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
        return root;
    }

    /** The io executor posts back to main: alternate idling the looper with short real waits. */
    private static void settle() throws InterruptedException {
        for (int i = 0; i < 8; i++) {
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(60);
        }
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(java.time.Duration.ofMillis(500));
    }
}
