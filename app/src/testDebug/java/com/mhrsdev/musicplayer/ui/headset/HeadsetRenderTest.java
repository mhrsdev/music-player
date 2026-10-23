package com.mhrsdev.musicplayer.ui.headset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import com.mhrsdev.musicplayer.domain.model.OutputDevice.Kind;
import com.mhrsdev.musicplayer.domain.model.SoundProfile;
import com.mhrsdev.musicplayer.fake.FakeOutputDevices;
import com.mhrsdev.musicplayer.ui.nav.FeatureScreens;
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
import org.robolectric.shadows.ShadowLooper;

/** Debug-only visual check: renders the Headset & outputs screen to docs/screens/o1-*.png. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class HeadsetRenderTest {
    private static final String DARK = "w412dp-h2000dp-night-xhdpi";
    private static final String LIGHT = "w412dp-h2000dp-notnight-xhdpi";

    private static final OutputDevice EARBUDS = new OutputDevice("BLUETOOTH:AA:BB", Kind.BLUETOOTH, "CMF Buds 2a", false, true);
    private static final OutputDevice SPEAKER = new OutputDevice("SPEAKER", Kind.SPEAKER, null, false, false);
    private static final OutputDevice WIRED = new OutputDevice("WIRED", Kind.WIRED, null, false, false);

    @Test @Config(qualifiers = DARK)
    public void twoOutputsDark() throws Exception { render("o1-headset-two-dark", List.of(EARBUDS, SPEAKER, WIRED), true); }

    @Test @Config(qualifiers = LIGHT)
    public void twoOutputsLight() throws Exception { render("o1-headset-two-light", List.of(EARBUDS, SPEAKER), false); }

    @Test @Config(qualifiers = "fa-" + DARK)
    public void persian() throws Exception { render("o1-headset-fa", List.of(EARBUDS, SPEAKER), true); }

    @Test @Config(qualifiers = "w360dp-h2000dp-night-xhdpi")
    public void singleOutputNarrow() throws Exception {
        render("o1-headset-single-360", List.of(new OutputDevice("SPEAKER", Kind.SPEAKER, null, false, true)), false);
    }

    @Test @Config(qualifiers = LIGHT)
    public void registryListsTheScreen() {
        assertTrue(FeatureScreens.available(RuntimeEnvironment.getApplication()).stream()
                .anyMatch(e -> e.className().equals(HeadsetFragment.class.getName())));
    }

    private void render(String name, List<OutputDevice> devices, boolean savedProfile) throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        AppContainer container = ((MusicPlayerApp) app).getContainer();
        ((FakeOutputDevices) container.outputDevices()).set(devices);
        if (savedProfile) {
            container.deviceProfiles().save(EARBUDS.key(), "CMF Buds 2a",
                    new SoundProfile(true, -1, List.of(300, 100, 0, 100, 300), 400, 0, 0, 1f));
        }
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Fragment f = new HeadsetFragment();
        a.getSupportFragmentManager().beginTransaction().replace(R.id.screen_container, f).addToBackStack(null).commit();
        settle();
        // The picker appears only with more than one output.
        assertEquals(devices.size() > 1 ? View.VISIBLE : View.GONE, f.requireView().findViewById(R.id.switch_output).getVisibility());

        View root = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
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
