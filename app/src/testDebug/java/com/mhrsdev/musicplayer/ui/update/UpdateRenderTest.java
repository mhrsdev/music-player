package com.mhrsdev.musicplayer.ui.update;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;
import com.mhrsdev.musicplayer.domain.model.UpdateSettings;
import com.mhrsdev.musicplayer.domain.model.UpdateState;
import com.mhrsdev.musicplayer.domain.repository.UpdateRepository;
import com.mhrsdev.musicplayer.ui.widget.WidgetGalleryActivity;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

/** Renders the update screen against a fake repository to docs/screens/b-update-*.png for a visual check. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UpdateRenderTest {
    private static final ReleaseInfo RELEASE = new ReleaseInfo("v0.2.0", true,
            "## What's new\n- **Update** screen: check, download, verify, install\n- Persian UI polish\n- [Changelog](https://example.com)",
            "2026-10-20T10:00:00Z", "https://example.com/a.apk", 14_600_000L, "https://example.com/a.apk.sha256", null);

    private static class Fake implements UpdateRepository {
        final MutableLiveData<UpdateState> state = new MutableLiveData<>();
        final MutableLiveData<UpdateSettings> settings = new MutableLiveData<>(
                new UpdateSettings(true, true, false, System.currentTimeMillis() - 3 * 3_600_000L));
        @Override public LiveData<UpdateState> state() { return state; }
        @Override public LiveData<ReleaseInfo> availableUpdate() { return new MutableLiveData<>(); }
        @Override public LiveData<UpdateSettings> settings() { return settings; }
        @Override public void setAutoCheck(boolean on) { }
        @Override public void setIncludePrereleases(boolean on) { }
        @Override public void setWifiOnly(boolean on) { }
        @Override public void checkNow() { }
        @Override public void download() { }
        @Override public void install() { }
        @Override public void skipVersion() { }
        @Override public void checkIfDue() { }
    }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-notnight-xhdpi") public void light() throws Exception { renderAll("light"); }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi") public void dark() throws Exception { renderAll("dark"); }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-w412dp-h915dp-night-xhdpi") public void darkFa() throws Exception { renderAll("dark-fa"); }

    private void renderAll(String tag) throws Exception {
        UpdateState[] states = {UpdateState.available(RELEASE), UpdateState.downloading(RELEASE, 42),
                UpdateState.error(UpdateState.ErrorKind.DIFFERENT_SIGNATURE, null), UpdateState.UP_TO_DATE};
        String[] names = {"available", "downloading", "signature", "uptodate"};
        for (int i = 0; i < states.length; i++) {
            if (tag.equals("dark-fa") && i > 2) break;
            render(tag, names[i], states[i]);
        }
    }

    private void render(String tag, String name, UpdateState state) throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        UpdateFragment f = new UpdateFragment();
        Fake fake = new Fake();
        fake.state.setValue(state);
        com.mhrsdev.musicplayer.di.AppContainer container = ((com.mhrsdev.musicplayer.MusicPlayerApp) a.getApplication()).getContainer();
        java.lang.reflect.Field field = com.mhrsdev.musicplayer.di.AppContainer.class.getDeclaredField("updates");
        field.setAccessible(true);
        field.set(container, fake); // the container creates the real repository lazily; the screen only sees the interface
        a.getSupportFragmentManager().beginTransaction().replace(android.R.id.content, f).commitNow();
        ShadowLooper.idleMainLooper();
        View v = f.requireView();
        int w = 412 * 2, h = 915 * 2;
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(com.mhrsdev.musicplayer.ui.theme.Tokens.color(a, com.mhrsdev.musicplayer.R.attr.mpBackground));
        v.draw(c);
        File out = new File("../docs/screens/b-update-" + name + "-" + tag + ".png");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }
}
