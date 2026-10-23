package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import com.mhrsdev.musicplayer.ui.sound.SoundFragment;
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

/** Debug-only visual check: renders the Sound screen (fake effects) to docs/screens/d-sound-*.png. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class)
public class SoundRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";
    private static final String LIGHT = "w412dp-h915dp-notnight-xhdpi";

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void dark() throws Exception { render("dark", false); }

    @Test @Config(sdk = 35, qualifiers = LIGHT)
    public void light() throws Exception { render("light", false); }

    @Test @Config(sdk = 35, qualifiers = "fa-" + DARK)
    public void faDark() throws Exception { render("fa-dark", false); }

    /** Preset picked and a few sliders moved, so the curve and tracks have something to show. */
    @Test @Config(sdk = 35, qualifiers = DARK)
    public void darkTuned() throws Exception { render("dark-tuned", true); }

    private void render(String name, boolean tuned) throws Exception {
        AppCompatActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        SoundFragment fragment = new SoundFragment();
        a.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment).commitNow();
        if (tuned) {
            com.mhrsdev.musicplayer.di.AppContainer c = ((MusicPlayerApp) a.getApplication()).getContainer();
            c.audioEffects().selectPreset(9); // Rock
            c.audioEffects().setEqualizerEnabled(true);
            c.audioEffects().setBassBoost(600);
            c.audioEffects().setLoudnessGain(300);
            c.player.setPlaybackSpeed(1.25f);
            c.player.setSkipSilence(true);
        }
        settle();
        View root = a.getWindow().getDecorView();
        int w = 412 * 2, h = 1900 * 2;
        // Tall bitmap: the page is a scroll view, so measure the content at its full height.
        View content = fragment.requireView();
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        View inner = ((ViewGroup) content).getChildAt(0);
        h = inner.getMeasuredHeight() + 360;
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, w, h);
        settle();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Tokens.color(a, R.attr.mpBackground));
        content.draw(canvas);
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "d-sound-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(40);
        }
    }
}
