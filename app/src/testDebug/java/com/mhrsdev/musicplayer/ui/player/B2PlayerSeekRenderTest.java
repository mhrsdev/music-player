package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentActivity;

import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.settings.SeekStylePickerSheet;
import com.mhrsdev.musicplayer.ui.widget.LiquidSeekView;
import com.mhrsdev.musicplayer.ui.widget.MountainSeekView;
import com.mhrsdev.musicplayer.ui.widget.WavySeekView;
import com.mhrsdev.musicplayer.ui.widget.WidgetGalleryActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * D25 (B2): the full player with new seek styles (RTL Persian 360 dp, light), the live style switch while the player
 * is open, and the style picker sheet (docs/screens/b2-*.png).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class B2PlayerSeekRenderTest {

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-w360dp-h800dp-night-xhdpi")
    public void rtlPlayerKeepsTheBarLtr() throws Exception {
        FragmentActivity a = host(SeekBarStyle.MOUNTAIN);
        PlayerFragment f = player(a);
        ViewGroup box = f.requireView().findViewById(R.id.player_seek_container);
        assertTrue(box.getChildAt(0) instanceof MountainSeekView);
        assertEquals(View.LAYOUT_DIRECTION_LTR, box.getChildAt(0).getLayoutDirection());
        snap(content(a), "b2-player-mountain-fa-dark", 360, 800);
    }

    @Test @Config(sdk = 35, qualifiers = "w360dp-h800dp-notnight-xhdpi")
    public void lightPlayerLadder() throws Exception {
        FragmentActivity a = host(SeekBarStyle.LED_LADDER);
        player(a);
        snap(content(a), "b2-player-ladder-light", 360, 800);
    }

    @Test @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xhdpi")
    public void switchingTheStyleUpdatesTheOpenPlayer() throws Exception {
        FragmentActivity a = host(SeekBarStyle.WAVY);
        PlayerFragment f = player(a);
        ViewGroup box = f.requireView().findViewById(R.id.player_seek_container);
        assertEquals(WavySeekView.class, box.getChildAt(0).getClass());
        AppContainer c = ((MusicPlayerApp) a.getApplication()).getContainer();
        c.settings().setSeekBarStyle(SeekBarStyle.LIQUID);
        idle();
        assertEquals(1, box.getChildCount());
        LiquidSeekView liquid = (LiquidSeekView) box.getChildAt(0);
        assertEquals(52_000L, liquid.getPosition());
        assertTrue(liquid.getDuration() > 0);
        snap(content(a), "b2-player-liquid-live-dark", 360, 800);
    }

    @Test @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xhdpi")
    public void pickerSheetDark() throws Exception { picker("dark"); }

    @Test @Config(sdk = 35, qualifiers = "w360dp-h800dp-notnight-xhdpi")
    public void pickerSheetLight() throws Exception { picker("light"); }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-w360dp-h800dp-night-xhdpi")
    public void pickerSheetFa() throws Exception { picker("fa-dark"); }

    private void picker(String tag) throws Exception {
        // Robolectric runs vsync back to back, so a playing preview would tick forever inside idle(): render the
        // still frame (animator scale 0 = the same path as reduce motion / battery saver).
        animatorScale(0f);
        FragmentActivity a = host(SeekBarStyle.PULSE_DOTS);
        player(a);
        SeekStylePickerSheet sheet = new SeekStylePickerSheet();
        PlayerRenderTest.overlay(a, sheet);
        snap(content(a), "b2-picker-over-player-" + tag, 360, 800);
        // the whole sheet, unclipped, for review
        View root = sheet.requireView();
        int w = 360 * 2;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, w, root.getMeasuredHeight());
        ViewGroup scroll = (ViewGroup) ((ViewGroup) root).getChildAt(3);
        View list = scroll.getChildAt(0);
        int full = root.getMeasuredHeight() - scroll.getHeight() + list.getMeasuredHeight();
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(full, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, full);
        Bitmap bmp = Bitmap.createBitmap(w, full, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        save(bmp, "b2-picker-" + tag);
        // tapping a card applies instantly
        ((ViewGroup) list).getChildAt(SeekBarStyle.BEADS.ordinal()).performClick();
        idle();
        assertEquals(SeekBarStyle.BEADS,
                ((MusicPlayerApp) a.getApplication()).getContainer().settings().seekBarStyle().getValue());
    }

    /** Frames flow only while playing, attached/visible and motion allowed; they stop the moment one is false. */
    @Test @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xhdpi")
    public void framesOnlyWhilePlayingAndMotionAllowed() throws Exception {
        animatorScale(1f);
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        com.mhrsdev.musicplayer.ui.widget.FrameSeekView[] views = {
                new LiquidSeekView(a), new com.mhrsdev.musicplayer.ui.widget.PulseDotsSeekView(a)};
        for (com.mhrsdev.musicplayer.ui.widget.FrameSeekView v : views) {
            ((ViewGroup) content(a)).addView(v, new ViewGroup.LayoutParams(720, 112));
            v.setDuration(200_000);
            assertTrue(!v.isAnimating());
            v.setPlaying(true);
            assertTrue("animates while playing + visible", v.isAnimating());
            v.setVisibility(View.GONE);
            assertTrue("stops when hidden", !v.isAnimating());
            v.setVisibility(View.VISIBLE);
            assertTrue(v.isAnimating());
            v.setPlaying(false);
            assertTrue("static when paused", !v.isAnimating());
            animatorScale(0f);
            v.setPlaying(true);
            assertTrue("animator scale 0: fully static", !v.isAnimating());
            v.setPlaying(false);
            animatorScale(1f);
            v.setPlaying(true);
            ((ViewGroup) content(a)).removeView(v);
            assertTrue("stops when detached", !v.isAnimating());
        }
    }

    private static void animatorScale(float s) {
        android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, s);
    }

    private static FragmentActivity host(SeekBarStyle style) throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        MusicPlayerApp app = (MusicPlayerApp) a.getApplication();
        AppContainer fakes = new AppContainer(app, true);
        java.lang.reflect.Field f = MusicPlayerApp.class.getDeclaredField("container");
        f.setAccessible(true);
        f.set(app, fakes);
        fakes.settings().setSeekBarStyle(style);
        fakes.player.play(SampleData.TRACKS, 1, "NEFFEX");
        fakes.player.pause();
        fakes.player.seekTo(52_000L);
        idle();
        return a;
    }

    private static PlayerFragment player(FragmentActivity a) {
        PlayerFragment f = PlayerFragment.newInstance();
        a.getSupportFragmentManager().beginTransaction().replace(android.R.id.content, f).commitNow();
        idle();
        return f;
    }

    private static View content(FragmentActivity a) { return a.findViewById(android.R.id.content); }

    private static void idle() { ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS); }

    private static void snap(View v, String name, int wDp, int hDp) throws Exception {
        int w = wDp * 2, h = hDp * 2;
        for (int i = 0; i < 2; i++) {
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            idle();
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        save(bmp, name);
    }

    private static void save(Bitmap bmp, String name) throws Exception {
        File dir = new File("../docs/screens");
        dir.mkdirs();
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }
}
