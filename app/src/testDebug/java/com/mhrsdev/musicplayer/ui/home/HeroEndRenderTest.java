package com.mhrsdev.musicplayer.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.HomeCarouselBinding;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

/**
 * Owner bug (v0.2.0): the hero row ended in an empty page. For 1/2/3/10 cards, LTR and RTL, 360/411/800 dp: the first
 * card starts at the start margin, after scrolling to the end the last card is full size and ends at the end margin;
 * a plain card row (Throwbacks / mixes) ends with its normal margin too. Renders docs/screens/h1-hero-end-*.png
 * (columns: start, end; rows: 1, 2, 3, 10 hero cards, then a 10-card plain row).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class HeroEndRenderTest {
    private static final int[] COUNTS = {1, 2, 3, 10};

    @Test @Config(qualifiers = "w360dp-h800dp-night-xhdpi") public void w360() throws Exception { run(360, false); }
    @Test @Config(qualifiers = "w360dp-h800dp-night-xhdpi") public void w360Rtl() throws Exception { run(360, true); }
    @Test @Config(qualifiers = "w411dp-h900dp-night-xhdpi") public void w411() throws Exception { run(411, false); }
    @Test @Config(qualifiers = "w411dp-h900dp-night-xhdpi") public void w411Rtl() throws Exception { run(411, true); }
    @Test @Config(qualifiers = "w800dp-h1280dp-night-xhdpi") public void w800() throws Exception { run(800, false); }
    @Test @Config(qualifiers = "w800dp-h1280dp-night-xhdpi") public void w800Rtl() throws Exception { run(800, true); }

    private void run(int dp, boolean rtl) throws Exception {
        Context ctx = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_MusicPlayer);
        float d = ctx.getResources().getDisplayMetrics().density;
        int w = Math.round(dp * d);
        int rowH = Math.round(dp >= 800 ? 720 * d : 420 * d);
        int rows = COUNTS.length + 1;
        Bitmap sheet = Bitmap.createBitmap(w, rowH / 2 * rows, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        sc.drawColor(Tokens.color(ctx, R.attr.mpBackground));
        Bitmap half = Bitmap.createBitmap(w, rowH, Bitmap.Config.ARGB_8888);
        for (int r = 0; r < rows; r++) {
            boolean hero = r < COUNTS.length;
            int n = hero ? COUNTS[r] : 10;
            FrameLayout host = new FrameLayout(ctx);
            host.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            HomeCarouselBinding b = HomeCarouselBinding.inflate(LayoutInflater.from(ctx), host, true);
            RecyclerView rv = b.carousel;
            if (hero) HeroCarousel.attach(rv);
            else rv.setLayoutManager(new LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false));
            HomeCardAdapter adapter = new HomeCardAdapter(hero, item -> { });
            rv.setAdapter(adapter);
            adapter.submitList(albums(n));
            layout(host, w, rowH, rv, hero);
            View first = rv.findViewHolderForAdapterPosition(0).itemView;
            int margin = Math.round(4 * d);
            int startEdge = rtl ? w - first.getRight() : first.getLeft();
            assertEquals(dp + "dp " + n + " first card start", rv.getPaddingStart() + margin, startEdge, 2);
            draw(host, half, sc, 0, r * rowH / 2, w / 2, rowH / 2);

            rv.scrollToPosition(n - 1);
            layout(host, w, rowH, rv, hero);
            rv.scrollBy(rtl ? -w * 3 : w * 3, 0); // fling-to-end equivalent: as far as it goes
            layout(host, w, rowH, rv, hero);
            View last = rv.findViewHolderForAdapterPosition(n - 1).itemView;
            int endEdge = rtl ? last.getLeft() : w - last.getRight();
            String where = dp + "dp " + (rtl ? "rtl " : "ltr ") + (hero ? "hero " : "row ") + n;
            assertEquals(where + " last card end margin", rv.getPaddingEnd() + margin, endEdge, 2);
            if (hero) assertEquals(where + " last card focused", 1f, last.getScaleX(), 0.02f);
            draw(host, half, sc, w / 2, r * rowH / 2, w / 2, rowH / 2);
        }
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "h1-hero-end-" + dp + (rtl ? "-rtl" : "") + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static List<HomeItem> albums(int n) {
        List<HomeItem> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(HomeItem.of(new Album("a" + i, "Album " + (i + 1), "ar", "Richard Clayderman", 1990, 12, null, i * 97)));
        }
        return out;
    }

    private static void layout(View host, int w, int h, RecyclerView rv, boolean hero) throws InterruptedException {
        for (int i = 0; i < 2; i++) {
            host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
            host.layout(0, 0, w, host.getMeasuredHeight());
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idle();
            if (hero) HeroCarousel.apply(rv);
        }
    }

    /** Draws the row at full size, then into the sheet cell at half width (two columns). */
    private static void draw(View host, Bitmap half, Canvas sheet, int x, int y, int cellW, int cellH) {
        half.eraseColor(0);
        Canvas c = new Canvas(half);
        host.draw(c);
        sheet.drawBitmap(half, new android.graphics.Rect(0, 0, half.getWidth(), half.getHeight()),
                new android.graphics.Rect(x, y, x + cellW, y + cellH), new Paint(Paint.FILTER_BITMAP_FLAG));
    }
}
