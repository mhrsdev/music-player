package com.mhrsdev.musicplayer.ui.backup;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

/** Debug-only visual check: renders the Backup screen (fake service, dry-run preview shown) to docs/screens/s1-backup*.png. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class BackupRenderTest {
    private static final String DARK = "w412dp-h1250dp-night-xhdpi";
    private static final String LIGHT = "w412dp-h1250dp-notnight-xhdpi";

    @Test @Config(qualifiers = DARK)
    public void dark() throws Exception { render("s1-backup", true); }

    @Test @Config(qualifiers = LIGHT)
    public void light() throws Exception { render("s1-backup-light", true); }

    @Test @Config(qualifiers = "fa-" + DARK)
    public void persian() throws Exception { render("s1-backup-fa", true); }

    @Test @Config(qualifiers = DARK)
    public void idle() throws Exception { render("s1-backup-idle", false); }

    private void render(String name, boolean preview) throws Exception {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Fragment f = new BackupFragment();
        a.getSupportFragmentManager().beginTransaction().replace(R.id.screen_container, f).addToBackStack(null).commit();
        settle();
        if (preview) {
            new ViewModelProvider(f).get(BackupViewModel.class).preview(new ByteArrayInputStream(new byte[0]));
            settle();
        }
        View root = a.getWindow().getDecorView();
        int w = 412 * 2, h = 1250 * 2;
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
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(40);
        }
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
