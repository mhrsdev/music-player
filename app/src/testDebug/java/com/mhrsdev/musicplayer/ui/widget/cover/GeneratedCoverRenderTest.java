package com.mhrsdev.musicplayer.ui.widget.cover;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Renders the generated-cover sheet (docs/screens/g1-covers-sheet.png: every scene family, Latin and Persian titles,
 * many palettes, plus a legibility strip at 48/96/160/512 px) and checks determinism of real pixels.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi")
public class GeneratedCoverRenderTest {

    /** title, artist; the scene/palette are forced below so the sheet shows every family. */
    private static final String[][] COVERS = {
            {"Without You", "NEFFEX"}, {"Grateful", "NEFFEX"}, {"شب‌های تهران", "آوا نیک‌زاد"},
            {"Electric Dusk", "Halcyon Drift"}, {"کوچه‌باغ", "گروه دریاچه"}, {"Salt & Static", "The Low Tides"},
            {"Cold Light", "Mira Vale"}, {"باران که می‌آید", "نیلوفر"}, {"Northern Letters", "Aster & Vale"},
            {"Concrete Gardens", "Kasra"}, {"انار", "سامان آرین"}, {"Midnight Radio Sessions Vol. 2", "Juniper Lane"},
            {"Paper Planes", "Juniper Lane"}, {"دلتنگی", "محسن"}, {"Fight Back", "NEFFEX"},
            {"Desert Bloom", "Kaveh Rostami"}, {"A Very Long Title That Will Not Fit On Two Lines At All", "Someone With A Long Name"},
            {"ماه و ماهی", "حجت اشرف‌زاده"}, {"Blue Hour", "Lumen"}, {"Ghost Town", null},
            {"Tidal", "Ocean Theory"}, {"سفر", "رضا"}, {"Static Bloom", "Neon Coast"}, {"Hollow", "Ash & Ember"},
            {"Lanterns", "Sora"}, {"گل‌های رز", "مهسا"}, {"Afterglow", "Wild Rivers"}, {"Saffron", "Darya"},
            {"Echoes", "Polaris"}, {"Moonrise", "NEFFEX"}};

    @Test
    public void coversSheet() throws Exception {
        GeneratedCover gc = GeneratedCover.get(RuntimeEnvironment.getApplication());
        int cols = 6, cell = 300, gap = 28, rows = (COVERS.length + cols - 1) / cols;
        int stripH = 560;
        Bitmap sheet = Bitmap.createBitmap(cols * (cell + gap) + gap, rows * (cell + gap) + gap + stripH,
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sheet);
        c.drawColor(0xFF0F1216);
        for (int i = 0; i < COVERS.length; i++) {
            CoverSpec spec = CoverSpec.single(COVERS[i][0], COVERS[i][1]);
            int scene = i % CoverScenes.COUNT, palette = (i * 7) % CoverPalettes.ALL.size();
            Bitmap b = Bitmap.createBitmap(cell, cell, Bitmap.Config.RGB_565);
            gc.draw(new Canvas(b), new RectF(0, 0, cell, cell), spec, scene, palette);
            rounded(c, b, gap + (i % cols) * (cell + gap), gap + (i / cols) * (cell + gap), cell * 0.08f);
        }
        // legibility strip: one cover per scene at its natural spec, at 48 / 96 / 160 px, plus one 512 px cover
        int y0 = rows * (cell + gap) + gap;
        int x = gap;
        int[] sizes = {48, 96, 160};
        for (int s = 0; s < CoverScenes.COUNT; s++) {
            CoverSpec spec = CoverSpec.single(COVERS[s + 8][0], COVERS[s + 8][1]);
            int yy = y0;
            for (int px : sizes) {
                Bitmap b = gc.render(spec, px);
                // the real ArtworkView draws a cached bitmap 1:1; here too
                rounded(c, b, x, yy, px * 0.14f);
                yy += px + 12;
            }
            x += 160 + 16;
        }
        Bitmap big = gc.render(CoverSpec.forArtist("Kasra"), 160);
        rounded(c, big, x, y0, 80);
        Bitmap fa = gc.render(CoverSpec.forArtist("سهراب نوری"), 160);
        rounded(c, fa, x, y0 + 172, 80);
        write(sheet, "g1-covers-sheet.png");

        Bitmap ref = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
        gc.draw(new Canvas(ref), new RectF(0, 0, 1024, 1024), CoverSpec.single("Without You", "NEFFEX"),
                CoverScenes.MOONLIT_HILLS, 0);
        Bitmap r2 = Bitmap.createBitmap(1124, 1124, Bitmap.Config.ARGB_8888);
        Canvas rc = new Canvas(r2);
        rc.drawColor(0xFF0F1216);
        rounded(rc, ref, 50, 50, 80);
        write(r2, "g1-cover-reference-512.png");
    }

    @Test
    public void samePixelsEveryRenderAndSpecsDiffer() {
        GeneratedCover gc = GeneratedCover.get(RuntimeEnvironment.getApplication());
        CoverSpec a = CoverSpec.forAlbum("12", "Concrete Gardens", "Kasra");
        int[] p1 = pixels(gc.render(a, 256));
        int[] p2 = pixels(gc.render(CoverSpec.forAlbum("12", "Concrete Gardens", "Kasra"), 256));
        assertArrayEquals(p1, p2);
        assertNotEquals(java.util.Arrays.hashCode(p1),
                java.util.Arrays.hashCode(pixels(gc.render(CoverSpec.forAlbum("13", "Cold Light", "Mira"), 256))));
        Bitmap b = gc.render(a, 512);
        assertEquals(Bitmap.Config.RGB_565, b.getConfig());
        assertEquals(512, b.getWidth());
    }

    private static int[] pixels(Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return px;
    }

    private static void rounded(Canvas c, Bitmap b, float x, float y, float r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setTranslate(x, y);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        c.drawRoundRect(new RectF(x, y, x + b.getWidth(), y + b.getHeight()), r, r, p);
    }

    private static void write(Bitmap b, String name) throws Exception {
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }
}
