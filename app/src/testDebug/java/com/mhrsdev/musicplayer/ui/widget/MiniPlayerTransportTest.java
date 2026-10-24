package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * D35: the mini-player has previous | play/pause | next. The transport keeps that physical order in RTL too (like the
 * full player's always-LTR transport row), the buttons are wired to the listener, next greys out like the full
 * player's, the targets stay >= 44 dp and the title still gets room (and ellipsizes) at 360 dp with font scale 2.
 * Renders docs/screens/u4-miniplayer-*.png.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MiniPlayerTransportTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";
    private static final int PERSIAN = 24; // "دختران قوچان"
    private static final int LONG_TITLE = 6; // "We Start the Cure in Paris"

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void dark() throws Exception {
        MiniPlayerView m = render("u4-miniplayer-dark", 412, LONG_TITLE);
        assertTransportOrder(m, false);
        assertEquals("Previous", m.previousButton().getContentDescription().toString());
        assertEquals("Next", m.nextButton().getContentDescription().toString());
    }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-notnight-xhdpi")
    public void light() throws Exception {
        assertTransportOrder(render("u4-miniplayer-light", 412, 0), false);
    }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK)
    public void persianRtl() throws Exception {
        MiniPlayerView m = render("u4-miniplayer-fa-dark", 412, PERSIAN);
        assertTransportOrder(m, true);
        assertEquals("قبلی", m.previousButton().getContentDescription().toString());
        assertEquals("بعدی", m.nextButton().getContentDescription().toString());
    }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-w360dp-h800dp-night-xhdpi", fontScale = 2f)
    public void persian360FontScale2() throws Exception {
        MiniPlayerView m = render("u4-miniplayer-fa-360-fontscale2", 360, PERSIAN);
        assertTransportOrder(m, true);
        TextView title = titleOf(m);
        float d = m.getResources().getDisplayMetrics().density;
        assertTrue("title keeps room: " + title.getWidth() / d + " dp", title.getWidth() >= 64 * d);
        Layout l = title.getLayout();
        assertTrue("title is one ellipsized line", l != null && l.getLineCount() == 1 && l.getEllipsisCount(0) > 0);
        for (View v : transport(m)) {
            int[] at = loc(v);
            assertTrue("inside the pill", at[0] >= loc(m)[0] && at[0] + v.getWidth() <= loc(m)[0] + m.getWidth());
        }
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void buttonsAreWiredAndNextGreysOutLikeTheFullPlayer() throws Exception {
        MiniPlayerView m = render(null, 412, 0);
        List<String> calls = new ArrayList<>();
        m.setListener(new MiniPlayerView.Listener() {
            @Override public void onOpen() { calls.add("open"); }

            @Override public void onPlayPause() { calls.add("play"); }

            @Override public void onNext() { calls.add("next"); }

            @Override public void onPrevious() { calls.add("previous"); }
        });
        m.previousButton().performClick();
        m.getPlayPauseView().performClick();
        m.nextButton().performClick();
        assertEquals(List.of("previous", "play", "next"), calls);

        assertTrue(m.previousButton().isEnabled() && m.nextButton().isEnabled());
        m.setSkipEnabled(true, false); // last track, repeat off
        assertTrue(m.previousButton().isEnabled());
        assertEquals(1f, m.previousButton().getAlpha(), 0f);
        assertFalse(m.nextButton().isEnabled());
        assertEquals(0.38f, m.nextButton().getAlpha(), 0f);
        calls.clear();
        tap(m.nextButton());
        assertTrue("a tap on a disabled next does nothing", calls.isEmpty());
        m.setSkipEnabled(true, true);
        assertTrue(m.nextButton().isEnabled());
        assertEquals(1f, m.nextButton().getAlpha(), 0f);
        tap(m.nextButton());
        assertEquals(List.of("next"), calls);
    }

    private static void tap(View v) {
        float x = v.getWidth() / 2f, y = v.getHeight() / 2f;
        long t = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0);
        v.dispatchTouchEvent(down);
        v.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(); // the click is posted
    }

    /** previous | play | next from left to right in both directions; in RTL the cluster sits left of the title. */
    private static void assertTransportOrder(MiniPlayerView m, boolean rtl) {
        float d = m.getResources().getDisplayMetrics().density;
        View prev = m.previousButton(), play = m.getPlayPauseView(), next = m.nextButton();
        assertEquals(View.VISIBLE, prev.getVisibility());
        assertTrue("prev left of play", loc(prev)[0] + prev.getWidth() <= loc(play)[0]);
        assertTrue("play left of next", loc(play)[0] + play.getWidth() <= loc(next)[0]);
        for (View v : transport(m)) {
            assertTrue("touch target >= 44 dp", v.getWidth() >= 44 * d - 1 && v.getHeight() >= 44 * d - 1);
        }
        int titleX = loc(titleOf(m))[0];
        if (rtl) assertTrue("RTL: transport at the left", loc(next)[0] + next.getWidth() <= titleX);
        else assertTrue("LTR: transport at the right", loc(prev)[0] >= titleX + titleOf(m).getWidth());
    }

    private static View[] transport(MiniPlayerView m) {
        return new View[]{m.previousButton(), m.getPlayPauseView(), m.nextButton()};
    }

    private static TextView titleOf(MiniPlayerView m) {
        View texts = m.getChildAt(1);
        return (TextView) ((android.view.ViewGroup) texts).getChildAt(0);
    }

    private static int[] loc(View v) {
        int[] at = new int[2];
        v.getLocationInWindow(at);
        return at;
    }

    private static MiniPlayerView render(String name, int widthDp, int track) throws Exception {
        Activity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        float d = a.getResources().getDisplayMetrics().density;
        int w = Math.round(widthDp * d), h = Math.round(140 * d);
        FrameLayout box = new FrameLayout(a);
        int margin = a.getResources().getDimensionPixelSize(R.dimen.mp_dock_margin_h);
        box.setPadding(margin, Math.round(20 * d), margin, Math.round(20 * d));
        MiniPlayerView mini = new MiniPlayerView(a);
        box.addView(mini, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        a.setContentView(box);
        Track t = SampleData.TRACKS.get(track);
        mini.bind(t);
        mini.setPlaying(true);
        mini.setProgress(70_000L, t.durationMs());
        box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        box.layout(0, 0, w, h);
        if (name == null) return mini;
        for (int i = 0; i < 4; i++) { // let the cover decode on the artwork thread and land on the main looper
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(40);
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Tokens.color(a, R.attr.mpBackground));
        box.draw(c);
        File out = new File(new File("../docs/screens"), name + ".png");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
        return mini;
    }
}
