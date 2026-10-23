package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

/**
 * U1 visual checks (settings, search, shell) at phone / large-phone / tablet widths and at font scale 1.3, written
 * to docs/screens/u1-*.png. Same approach as {@link ScreenRenderTest}: real shell, fake data, native graphics.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class U1RenderTest {

    @Test @Config(qualifiers = "w411dp-h3400dp-night-xhdpi")
    public void settings411() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-411-dark", 411, 3400); }

    @Test @Config(qualifiers = "w360dp-h3700dp-notnight-xhdpi")
    public void settings360Light() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-360-light", 360, 3700); }

    @Test @Config(qualifiers = "w411dp-h4300dp-night-xhdpi", fontScale = 1.3f)
    public void settingsFont13() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-411-font1.3", 411, 4300); }

    @Test @Config(qualifiers = "fa-w411dp-h3500dp-notnight-xhdpi")
    public void settingsFa() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-411-fa-light", 411, 3500); }

    @Test @Config(qualifiers = "w800dp-h1280dp-night-mdpi")
    public void settings800() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-800-dark", 800, 1280); }

    @Test @Config(qualifiers = "w411dp-h915dp-night-xhdpi")
    public void searchPrompt411() throws Exception { render(ScreenNavigator.Tab.SEARCH, null, "search-prompt-411", 411, 915); }

    @Test @Config(qualifiers = "w411dp-h915dp-night-xhdpi")
    public void searchResults411() throws Exception { render(ScreenNavigator.Tab.SEARCH, "ra", "search-results-411", 411, 915); }

    @Test @Config(qualifiers = "w360dp-h800dp-night-xhdpi")
    public void searchNoResults360() throws Exception { render(ScreenNavigator.Tab.SEARCH, "neffexx grateful", "search-none-360", 360, 800); }

    @Test @Config(qualifiers = "w800dp-h1280dp-night-mdpi")
    public void search800() throws Exception { render(ScreenNavigator.Tab.SEARCH, "ra", "search-results-800", 800, 1280); }

    /** Owner's phone: 1080x2400 at 420dpi = 411dp wide, Persian (RTL). */
    @Test @Config(qualifiers = "fa-w411dp-h914dp-night-xxhdpi")
    public void homeFa411() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-fa-411", 411, 914); }

    @Test @Config(qualifiers = "fa-w411dp-h914dp-night-xxhdpi")
    public void homeFa411Scrolled() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-fa-411-bottom", 411, 914, true); }

    // Top of Home (owner: seam under the status bar, hard glow edge, squeezed neighbours): 1080x2400 + 360dp.
    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void homeTop1080() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-top-1080x2400-dark", 411, 914); }

    @Test @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    public void homeTop360Light() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-top-360-light", 360, 800); }

    @Test @Config(qualifiers = "fa-w360dp-h800dp-night-xxhdpi")
    public void homeTop360Fa() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-top-360-fa-dark", 360, 800); }

    // Dock spacing (owner: 'margin is not enough'): light+dark, en+fa, 360 and 411 dp.
    @Test @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    public void dock360Light() throws Exception { render(ScreenNavigator.Tab.HOME, null, "dock-360-light", 360, 800, true); }

    @Test @Config(qualifiers = "fa-w360dp-h800dp-night-xxhdpi")
    public void dock360FaDark() throws Exception { render(ScreenNavigator.Tab.HOME, null, "dock-360-fa-dark", 360, 800, true); }

    @Test @Config(qualifiers = "fa-w411dp-h914dp-notnight-xxhdpi")
    public void dock411FaLight() throws Exception { render(ScreenNavigator.Tab.HOME, null, "dock-411-fa-light", 411, 914, true); }

    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void dock411Dark() throws Exception { render(ScreenNavigator.Tab.LIBRARY, null, "dock-411-dark", 411, 914); }

    @Test @Config(qualifiers = "w800dp-h1280dp-night-mdpi")
    public void home800() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-800", 800, 1280); }

    @Test @Config(qualifiers = "w891dp-h411dp-night-xhdpi")
    public void homeLandscape() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-land", 891, 411); }

    @Test @Config(qualifiers = "w360dp-h800dp-night-xhdpi", fontScale = 1.3f)
    public void home360Font13() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-360-font1.3", 360, 800); }

    private void render(ScreenNavigator.Tab tab, String query, String name, int wDp, int hDp) throws Exception {
        render(tab, query, name, wDp, hDp, false);
    }

    private void render(ScreenNavigator.Tab tab, String query, String name, int wDp, int hDp, boolean scrollToEnd)
            throws Exception {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        if (tab != ScreenNavigator.Tab.HOME) {
            a.openTab(tab);
            settle();
        }
        if (query != null) {
            EditText q = a.findViewById(R.id.query);
            q.setText(query);
            settle();
            q.clearFocus();
            settle();
        }
        View root = a.getWindow().getDecorView();
        float d = a.getResources().getDisplayMetrics().density;
        int w = Math.round(wDp * d), h = Math.round(hDp * d);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        settle();
        if (scrollToEnd) {
            androidx.recyclerview.widget.RecyclerView rv = findList(root);
            if (rv != null && rv.getAdapter() != null) {
                rv.scrollToPosition(rv.getAdapter().getItemCount() - 1);
                settle();
                rv.scrollBy(0, 100000);
                settle();
            }
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Tokens.color(a, R.attr.mpBackground));
        root.draw(c);
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "u1-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static androidx.recyclerview.widget.RecyclerView findList(View v) {
        if (v instanceof androidx.recyclerview.widget.RecyclerView rv && rv.getLayoutManager() != null
                && rv.getLayoutManager().canScrollVertically()) return rv;
        if (v instanceof android.view.ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) {
                androidx.recyclerview.widget.RecyclerView r = findList(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(60);
        }
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(java.time.Duration.ofMillis(500));
    }
}
