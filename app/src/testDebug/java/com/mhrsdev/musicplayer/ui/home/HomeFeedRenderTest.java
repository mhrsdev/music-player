package com.mhrsdev.musicplayer.ui.home;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.data.repository.FeedFixtures;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
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
 * Home feed v2 on synthetic libraries of 10 / 200 / 5000 tracks (real {@code HomeFeedBuilder}, fixed seeds), rendered to
 * docs/screens/h1-home-*.png as three consecutive screens side by side, and the total scroll length printed in screens.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class HomeFeedRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";
    private static final int W = 412 * 2, H = 915 * 2;

    @Test @Config(qualifiers = DARK) public void home10() throws Exception { render(10, "h1-home-10-dark"); }

    @Test @Config(qualifiers = DARK) public void home200() throws Exception { render(200, "h1-home-200-dark"); }

    @Test @Config(qualifiers = DARK) public void home5000() throws Exception { render(5000, "h1-home-5000-dark"); }

    @Test @Config(qualifiers = "fa-" + DARK) public void home200Fa() throws Exception { render(200, "h1-home-200-fa-dark"); }

    @Test @Config(qualifiers = "w412dp-h915dp-notnight-xhdpi")
    public void home200Light() throws Exception { render(200, "h1-home-200-light"); }

    private void render(int size, String name) throws Exception {
        List<HomeSection> sections = FeedFixtures.library(size, 1).build();
        MusicPlayerApp app = (MusicPlayerApp) RuntimeEnvironment.getApplication();
        AppContainer c = app.getContainer();
        MutableLiveData<List<HomeSection>> live = new MutableLiveData<>(sections);
        var field = AppContainer.class.getField("homeFeed");
        field.setAccessible(true);
        field.set(c, (HomeFeedSource) () -> (LiveData<List<HomeSection>>) live);
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        View root = a.getWindow().getDecorView();
        layout(root, H);
        RecyclerView list = a.findViewById(R.id.list);

        // Scroll length: scroll screen by screen to the end, summing the real offsets (rows are laid out lazily).
        long scrolled = 0;
        int screen = list.getHeight();
        for (int i = 0; i < 200 && list.canScrollVertically(1); i++) {
            int before = list.computeVerticalScrollOffset();
            list.scrollBy(0, screen);
            layout(root, H);
            scrolled += list.computeVerticalScrollOffset() - before;
        }
        double screens = (scrolled + screen) / (double) screen;
        System.out.println("H1 " + name + ": " + sections.size() + " sections, total height " + String.format("%.1f", screens)
                + " screens");
        list.scrollToPosition(0);
        layout(root, H);

        int panels = 3;
        Bitmap sheet = Bitmap.createBitmap(W / 2 * panels, H / 2, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        Bitmap frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        for (int p = 0; p < panels; p++) {
            Canvas fc = new Canvas(frame);
            fc.drawColor(Tokens.color(a, R.attr.mpBackground));
            root.draw(fc);
            sc.drawBitmap(frame, new Rect(0, 0, W, H), new Rect(p * W / 2, 0, (p + 1) * W / 2, H / 2), null);
            list.scrollBy(0, (int) (screen * 0.9));
            layout(root, H);
        }
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static void layout(View root, int h) throws InterruptedException {
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, h);
        settle();
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(40);
        }
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(java.time.Duration.ofMillis(300));
    }
}
