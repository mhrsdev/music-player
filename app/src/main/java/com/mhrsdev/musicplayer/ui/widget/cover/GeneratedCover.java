package com.mhrsdev.musicplayer.ui.widget.cover;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;

import androidx.annotation.Nullable;
import androidx.annotation.AnyThread;
import androidx.core.content.res.ResourcesCompat;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.ColorMath;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverPalettes.Palette;

import java.util.List;
import java.util.Locale;

/**
 * Mowj's generated covers for tracks without artwork: eight night-scene families (moonlit hills after the owner's
 * reference, sunset over waves, vinyl, aurora, Persian star tiles, soft orbs, typographic poster, contour map) drawn
 * with Canvas primitives in a 100-unit square, then cover typography (Google Sans Flex / Vazirmatn, RTL-aware,
 * auto-fit, never cropped). Fully determined by the {@link CoverSpec}: all randomness comes from {@link CoverRandom}.
 *
 * <p>Cost: one draw per (spec, size bucket), on a worker thread, cached by the artwork loader. Paints and paths are
 * pooled; shaders are created per render (a render is a one-off, never per frame). {@link #draw} is synchronized
 * because the pool is shared by the loader's worker threads.
 */
public class GeneratedCover {
    /** Below this size the text would be unreadable: scene only. */
    public static final int TEXT_MIN_PX = 96;
    /** Artist initials are drawn from this size (avatars are small). */
    public static final int INITIAL_MIN_PX = 24;

    private static final float MARGIN = 8f;
    private static GeneratedCover instance;

    @Nullable private final Typeface latin;
    @Nullable private final Typeface arabic;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    private final Path path2 = new Path();
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();

    public GeneratedCover(@Nullable Typeface latin, @Nullable Typeface arabic) {
        this.latin = latin;
        this.arabic = arabic;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Process-wide instance with the bundled fonts (any thread). */
    public static synchronized GeneratedCover get(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            instance = new GeneratedCover(font(app, R.font.google_sans_flex), font(app, R.font.vazirmatn));
        }
        return instance;
    }

    @Nullable private static Typeface font(Context c, int id) {
        try {
            return ResourcesCompat.getFont(c, id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * A square cover bitmap. Opaque, so RGB_565 (half the memory; gradients are dithered). Pure canvas drawing with
     * no shared state, so it is safe on any thread; callers keep main-thread renders tiny
     * ({@code ArtworkLoader.generatedNow} only up to {@code SYNC_MAX_PX} = 96 px, to avoid a placeholder flash) and
     * render everything larger on the loader's worker.
     */
    @AnyThread
    public Bitmap render(CoverSpec spec, int sizePx) {
        return render(spec, sizePx, Bitmap.Config.RGB_565);
    }

    @AnyThread
    public Bitmap render(CoverSpec spec, int sizePx, Bitmap.Config config) {
        int s = Math.max(1, sizePx);
        Bitmap b = Bitmap.createBitmap(s, s, config);
        draw(new Canvas(b), new RectF(0, 0, s, s), spec);
        return b;
    }

    /** Draws the cover for {@code spec} into the square {@code bounds} (the shorter side if not square). */
    public void draw(Canvas canvas, RectF bounds, CoverSpec spec) {
        draw(canvas, bounds, spec, spec.scene(), spec.palette());
    }

    /** As {@link #draw(Canvas, RectF, CoverSpec)} with the scene and palette forced (sheets, tests). */
    public synchronized void draw(Canvas canvas, RectF bounds, CoverSpec spec, int scene, int palette) {
        float size = Math.min(bounds.width(), bounds.height());
        Palette p = CoverPalettes.ALL.get(palette);
        CoverRandom r = new CoverRandom(spec.seed() ^ 0xC0FFEEL * (scene + 1));
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.clipRect(0, 0, size, size);
        int unitSave = canvas.save();
        canvas.scale(size / 100f, size / 100f);
        reset();
        switch (scene) {
            case CoverScenes.MOONLIT_HILLS -> moonlitHills(canvas, p, r);
            case CoverScenes.SUNSET_WAVES -> sunsetWaves(canvas, p, r);
            case CoverScenes.VINYL -> {
                // tone arm goes opposite the artist line: mirror for a right-aligned (Persian) subtitle
                if (spec.subtitle() != null && ScriptFont.hasArabicScript(spec.subtitle())) canvas.scale(-1, 1, 50, 50);
                vinyl(canvas, p, r);
            }
            case CoverScenes.AURORA -> aurora(canvas, p, r);
            case CoverScenes.TILES -> tiles(canvas, p, r);
            case CoverScenes.ORBS -> orbs(canvas, p, r);
            case CoverScenes.POSTER -> poster(canvas, p, r);
            default -> contours(canvas, p, r);
        }
        if (spec.kind() == CoverSpec.Kind.ARTIST) radial(canvas, 50, 50, 46, p.deep(), 0.62f, 0f);
        canvas.restoreToCount(unitSave);
        reset();
        drawText(canvas, size, spec, p, scene);
        canvas.restoreToCount(save);
    }

    private void reset() {
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setAlpha(255);
        stroke.setShader(null);
        stroke.setPathEffect(null);
        path.reset();
        path2.reset();
    }

    // ---------------------------------------------------------------- scenes (100 x 100 units)

    /** The owner's reference (design 05): navy-to-teal sky, haloed moon with craters, stars, two dark hills. */
    private void moonlitHills(Canvas c, Palette p, CoverRandom r) {
        sky(c, p, 0.62f);
        float mx = r.range(52, 74), my = r.range(28, 38), mr = r.range(9.5f, 12.5f);
        if (r.chance(0.35f)) mx = 100 - mx; // moon left now and then
        radial(c, mx, my, 42, p.halo(), 0.55f, 0f);
        stars(c, r, p.glow(), r.nextInt(18) + 36, 58);
        boolean crescent = r.chance(0.3f);
        if (crescent) {
            path.addCircle(mx, my, mr, Path.Direction.CW);
            path2.addCircle(mx + mr * 0.48f, my - mr * 0.28f, mr * 0.9f, Path.Direction.CW);
            path.op(path2, Path.Op.DIFFERENCE);
            fill.setColor(p.glow());
            c.drawPath(path, fill);
            path.reset();
            path2.reset();
        } else {
            fill.setColor(p.glow());
            c.drawCircle(mx, my, mr, fill);
            fill.setColor(ColorMath.lerp(p.glow(), p.sky2(), 0.13f));
            float k = mr / 11.5f, f = r.chance(0.5f) ? 1 : -1;
            c.drawCircle(mx - 3.5f * k * f, my - 3f * k, 2.2f * k, fill);
            c.drawCircle(mx + 3f * k * f, my + 4.5f * k, 1.6f * k, fill);
            c.drawCircle(mx + 4f * k * f, my - 5f * k, 1f * k, fill);
        }
        // back hill: M0 y0 Q c1 p1 T 100 y2 (as in the reference), then the darker front hill
        float y0 = r.range(72, 79);
        hill(c, y0, r.range(14, 30), y0 - r.range(8, 14), r.range(40, 54), y0 - r.range(0, 6), r.range(62, 72), p.hill());
        float f0 = r.range(83, 88);
        hill(c, f0, r.range(22, 38), f0 - r.range(7, 12), r.range(54, 68), f0 + r.range(-2, 2), r.range(78, 85), p.deep());
    }

    /** Sun on the horizon with a faint sunburst, its reflection on the water and three wave silhouettes. */
    private void sunsetWaves(Canvas c, Palette p, CoverRandom r) {
        float horizon = r.range(62, 68);
        int glowSky = ColorMath.lerp(p.sky2(), p.accent(), 0.32f);
        fill.setShader(new LinearGradient(0, 0, 0, horizon, new int[]{p.sky0(), p.sky1(), glowSky},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 100, 100, fill);
        fill.setShader(null);
        float sx = r.range(34, 66), sr = r.range(11, 15);
        int save = c.save();
        c.clipRect(0, 0, 100, horizon);
        radial(c, sx, horizon, 46, p.accent(), 0.38f, 0f);
        fill.setColor(ColorMath.withAlpha(p.glow(), 0.055f));
        int rays = 32;
        for (int i = 0; i < rays; i += 2) {
            double a0 = Math.PI * 2 * i / rays, a1 = Math.PI * 2 * (i + 1) / rays;
            path.reset();
            path.moveTo(sx, horizon);
            path.lineTo(sx + 120 * (float) Math.cos(a0), horizon + 120 * (float) Math.sin(a0));
            path.lineTo(sx + 120 * (float) Math.cos(a1), horizon + 120 * (float) Math.sin(a1));
            path.close();
            c.drawPath(path, fill);
        }
        path.reset();
        stars(c, r, p.glow(), 14, horizon - 30);
        path.addCircle(sx, horizon + 1, sr, Path.Direction.CW);
        if (r.chance(0.55f)) { // retro stripes cut through the lower half of the sun
            for (int i = 0; i < 4; i++) {
                float y = horizon + 1 - sr * 0.52f + i * sr * 0.17f;
                path2.addRect(sx - sr, y, sx + sr, y + 0.5f + i * 0.45f, Path.Direction.CW);
            }
            path.op(path2, Path.Op.DIFFERENCE);
            path2.reset();
        }
        fill.setShader(new LinearGradient(0, horizon - sr, 0, horizon,
                ColorMath.lerp(p.glow(), p.accent2(), 0.25f), p.accent(), Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        path.reset();
        c.restoreToCount(save);
        // water
        fill.setShader(new LinearGradient(0, horizon, 0, 100, p.hill(), p.deep(), Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, 100, 100, fill);
        fill.setShader(null);
        for (int i = 0; i < 6; i++) { // reflection
            float w = sr * (1.5f - i * 0.2f), y = horizon + 2.2f + i * 2.1f;
            fill.setColor(ColorMath.withAlpha(ColorMath.lerp(p.accent(), p.glow(), 0.4f), 0.42f - i * 0.06f));
            rect.set(sx - w, y, sx + w, y + 0.55f);
            c.drawRoundRect(rect, 0.3f, 0.3f, fill);
        }
        float[] bases = {r.range(73, 77), r.range(81, 85), r.range(89, 92)};
        for (int i = 0; i < 3; i++) {
            float a = bases[i], j = r.range(-4, 4);
            path.reset();
            path.moveTo(0, a);
            path.cubicTo(18 + j, a - 6, 38 - j, a + 5, 60, a - 1.5f);
            path.cubicTo(76 + j, a - 6, 90, a - 4, 100, a - 1.5f);
            path.lineTo(100, 100);
            path.lineTo(0, 100);
            path.close();
            fill.setColor(ColorMath.lerp(p.hill(), p.deep(), 0.35f + i * 0.33f));
            c.drawPath(path, fill);
        }
        path.reset();
    }

    /** A record in a soft spotlight: grooves, a sweep sheen, the label and a tone arm. */
    private void vinyl(Canvas c, Palette p, CoverRandom r) {
        fill.setShader(new LinearGradient(0, 0, 100, 100, new int[]{p.sky0(), p.sky1(), p.sky2()},
                new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 100, 100, fill);
        fill.setShader(null);
        radial(c, r.range(20, 40), r.range(10, 25), 70, p.halo(), 0.14f, 0f);
        float cx = r.range(50, 62), cy = r.range(48, 56), R = r.range(35, 40);
        radial(c, cx + 2, cy + 3.5f, R + 9, p.deep(), 0.75f, 0f);
        fill.setColor(ColorMath.lerp(p.deep(), 0xFF000000, 0.35f));
        c.drawCircle(cx, cy, R, fill);
        stroke.setStrokeWidth(0.22f);
        int grooves = 22;
        for (int i = 0; i < grooves; i++) {
            stroke.setColor(ColorMath.withAlpha(p.glow(), 0.035f + 0.06f * r.nextFloat()));
            c.drawCircle(cx, cy, R * (0.38f + 0.6f * i / (grooves - 1)), stroke);
        }
        SweepGradient sheen = new SweepGradient(cx, cy,
                new int[]{0, ColorMath.withAlpha(p.glow(), 0.16f), 0, 0, ColorMath.withAlpha(p.glow(), 0.11f), 0, 0},
                new float[]{0f, 0.07f, 0.16f, 0.5f, 0.57f, 0.66f, 1f});
        matrix.setRotate(r.range(0, 360), cx, cy);
        sheen.setLocalMatrix(matrix);
        fill.setShader(sheen);
        c.drawCircle(cx, cy, R, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(0.35f);
        stroke.setColor(ColorMath.withAlpha(p.glow(), 0.16f));
        c.drawCircle(cx, cy, R, stroke);
        float lr = R * 0.34f;
        fill.setShader(new RadialGradient(cx - lr * 0.3f, cy - lr * 0.3f, lr * 1.3f,
                ColorMath.lerp(p.accent(), p.glow(), 0.18f), ColorMath.lerp(p.accent(), p.deep(), 0.4f), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, lr, fill);
        fill.setShader(null);
        stroke.setColor(ColorMath.withAlpha(p.accent2(), 0.7f));
        stroke.setStrokeWidth(0.4f);
        c.drawCircle(cx, cy, lr * 0.78f, stroke);
        fill.setColor(p.sky0());
        c.drawCircle(cx, cy, 1.3f, fill);
        // tone arm
        float px = 91, py = 13, ex = cx + R * 0.52f, ey = cy - R * 0.42f;
        stroke.setColor(ColorMath.lerp(p.glow(), p.hill(), 0.45f));
        stroke.setStrokeWidth(1.3f);
        c.drawLine(px, py, px - 2, ey - 9, stroke);
        c.drawLine(px - 2, ey - 9, ex, ey, stroke);
        stroke.setStrokeWidth(2.8f);
        c.drawLine(ex + 1.6f, ey - 1.3f, ex - 0.8f, ey + 0.8f, stroke);
        fill.setColor(p.hill());
        c.drawCircle(px, py, 3.4f, fill);
        stroke.setStrokeWidth(0.4f);
        stroke.setColor(ColorMath.withAlpha(p.glow(), 0.3f));
        c.drawCircle(px, py, 3.4f, stroke);
        bottomScrim(c, p, 68);
    }

    /** Night sky with glowing curtains of light above two mountain ridges. */
    private void aurora(Canvas c, Palette p, CoverRandom r) {
        fill.setShader(new LinearGradient(0, 0, 0, 100, new int[]{p.sky0(), ColorMath.lerp(p.sky0(), p.sky1(), 0.7f), p.sky1()},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 100, 100, fill);
        fill.setShader(null);
        stars(c, r, p.glow(), 44, 64);
        for (int k = 0; k < 3; k++) {
            int col = k == 1 ? p.accent2() : p.accent();
            float base = r.range(40, 58) + k * 3, amp = r.range(4, 9), h = r.range(16, 28);
            float ph = r.range(0, 6.28f), fr = r.range(0.035f, 0.07f), ph2 = r.range(0, 6.28f);
            path.reset();
            path2.reset();
            float minTop = 100;
            for (int i = 0; i <= 44; i++) {
                float x = -5 + i * 2.5f;
                float yb = base + amp * (float) Math.sin(x * fr + ph);
                float yt = yb - h * (0.65f + 0.35f * (float) Math.sin(x * fr * 2.3f + ph2));
                minTop = Math.min(minTop, yt);
                if (i == 0) path2.moveTo(x, yb); else path2.lineTo(x, yb);
            }
            for (int i = 44; i >= 0; i--) {
                float x = -5 + i * 2.5f;
                float yb = base + amp * (float) Math.sin(x * fr + ph);
                float yt = yb - h * (0.65f + 0.35f * (float) Math.sin(x * fr * 2.3f + ph2));
                if (i == 44) path.moveTo(x, yt); else path.lineTo(x, yt);
            }
            for (int i = 0; i <= 44; i++) {
                float x = -5 + i * 2.5f;
                path.lineTo(x, base + amp * (float) Math.sin(x * fr + ph));
            }
            path.close();
            float alpha = k == 2 ? 0.4f : 0.62f;
            fill.setShader(new LinearGradient(0, minTop, 0, base + amp,
                    new int[]{0, ColorMath.withAlpha(col, alpha * 0.45f), ColorMath.withAlpha(col, alpha)},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(path, fill);
            fill.setShader(null);
            stroke.setColor(ColorMath.withAlpha(ColorMath.lerp(col, p.glow(), 0.4f), 0.16f));
            stroke.setStrokeWidth(2.2f);
            c.drawPath(path2, stroke);
            stroke.setColor(ColorMath.withAlpha(ColorMath.lerp(col, p.glow(), 0.55f), 0.45f));
            stroke.setStrokeWidth(0.45f);
            c.drawPath(path2, stroke);
        }
        path.reset();
        path2.reset();
        ridge(c, r, r.range(62, 70), 9, p.hill());
        ridge(c, r, r.range(77, 84), 6, p.deep());
    }

    /** Persian eight-point star tiles (kashi) with a gold grout line, under a plaque for the title. */
    private void tiles(Canvas c, Palette p, CoverRandom r) {
        fill.setColor(ColorMath.lerp(p.sky1(), p.deep(), 0.25f));
        c.drawRect(0, 0, 100, 100, fill);
        float w = r.range(18, 24), h = w * 0.866f, ox = r.range(0, w), oy = r.range(0, h);
        float outer = w * 0.47f;
        int outerFill = ColorMath.lerp(p.sky2(), p.sky1(), 0.35f);
        for (int gy = -1; gy * h + oy < 100 + h; gy++) {
            for (int gx = -1; gx * w + ox < 100 + w; gx++) {
                float cx = gx * w + ox + ((gy & 1) == 1 ? w / 2 : 0), cy = gy * h + oy;
                star(c, cx, cy, outer, 0.62f, outerFill, p.accent2(), 0.6f);
                star(c, cx, cy, outer * 0.49f, 0.62f, p.accent(), p.deep(), 0.45f);
                fill.setColor(p.glow());
                c.drawCircle(cx, cy, w * 0.055f, fill);
            }
        }
        radial(c, 50, 38, 78, p.deep(), 0f, 0.6f);
        fill.setColor(p.deep());
        c.drawRect(0, 72, 100, 100, fill);
        fill.setColor(p.accent2());
        c.drawRect(0, 72, 100, 73.1f, fill);
        fill.setColor(ColorMath.withAlpha(p.accent2(), 0.45f));
        c.drawRect(0, 74.2f, 100, 74.6f, fill);
    }

    /** Glassy light spheres on a deep gradient: each lit from the top left, with a soft halo, plus fine dust. */
    private void orbs(Canvas c, Palette p, CoverRandom r) {
        sky(c, p, 0.6f);
        int[] cols = {p.accent(), p.accent2(), ColorMath.lerp(p.sky2(), p.accent(), 0.5f), p.halo()};
        int n = 3 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            int col = cols[(i + r.nextInt(4)) % cols.length];
            float rad = i == 0 ? r.range(20, 27) : r.range(7, 17);
            float x = r.range(12 + rad * 0.4f, 88 - rad * 0.4f), y = r.range(18 + rad * 0.3f, 66 - rad * 0.2f);
            radial(c, x, y, rad * 1.9f, col, 0.22f, 0f);
            fill.setShader(new RadialGradient(x - rad * 0.35f, y - rad * 0.4f, rad * 1.45f,
                    new int[]{ColorMath.withAlpha(ColorMath.lerp(col, p.glow(), 0.45f), 0.92f),
                            ColorMath.withAlpha(col, 0.62f), ColorMath.withAlpha(ColorMath.lerp(col, p.deep(), 0.5f), 0.4f)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, fill);
            fill.setShader(null);
            stroke.setColor(ColorMath.withAlpha(p.glow(), 0.22f));
            stroke.setStrokeWidth(0.3f);
            c.drawCircle(x, y, rad, stroke);
        }
        for (int i = 0; i < 30; i++) {
            fill.setColor(ColorMath.withAlpha(p.glow(), r.range(0.15f, 0.6f)));
            c.drawCircle(r.range(0, 100), r.range(4, 78), r.range(0.12f, 0.4f), fill);
        }
        bottomScrim(c, p, 64);
    }

    /** Typographic poster: the title is the image (set by {@link #drawText}); a striped half-sun rises below. */
    private void poster(Canvas c, Palette p, CoverRandom r) {
        fill.setShader(new LinearGradient(0, 0, 0, 100, p.sky0(), ColorMath.lerp(p.sky0(), p.sky1(), 0.75f),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 100, 100, fill);
        fill.setShader(null);
        float R = r.range(30, 40), cx = r.range(36, 72), cy = 100 + R * 0.18f;
        radial(c, cx, cy, R * 1.9f, p.accent(), 0.3f, 0f);
        path.addCircle(cx, cy, R, Path.Direction.CW);
        for (int i = 0; i < 5; i++) {
            float y = cy - R * 0.62f + i * R * 0.16f;
            path2.addRect(cx - R, y, cx + R, y + 0.6f + i * 0.5f, Path.Direction.CW);
        }
        path.op(path2, Path.Op.DIFFERENCE);
        fill.setShader(new LinearGradient(0, cy - R, 0, 100, ColorMath.lerp(p.accent2(), p.glow(), 0.15f), p.accent(),
                Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        path.reset();
        path2.reset();
        stroke.setColor(ColorMath.withAlpha(p.glow(), 0.3f));
        stroke.setStrokeWidth(0.3f);
        c.drawLine(MARGIN, 6.5f, 100 - MARGIN, 6.5f, stroke);
        stroke.setColor(ColorMath.withAlpha(p.glow(), 0.18f));
        stroke.setStrokeWidth(0.3f);
        rect.set(cx - R * 1.25f, cy - R * 1.25f, cx + R * 1.25f, cy + R * 1.25f);
        c.drawArc(rect, 180, 180, false, stroke);
    }

    /** Topographic map: two summits of contour lines over a faint survey grid. */
    private void contours(Canvas c, Palette p, CoverRandom r) {
        fill.setShader(new LinearGradient(0, 0, 100, 100, p.sky0(), ColorMath.lerp(p.sky0(), p.sky1(), 0.65f),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 100, 100, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(0.2f);
        stroke.setColor(ColorMath.withAlpha(p.glow(), 0.05f));
        for (int i = 1; i < 8; i++) {
            c.drawLine(i * 12.5f, 0, i * 12.5f, 100, stroke);
            c.drawLine(0, i * 12.5f, 100, i * 12.5f, stroke);
        }
        float ax = r.range(26, 46), ay = r.range(30, 50), bx = r.range(62, 84), by = r.range(58, 78);
        blob(c, r, ax, ay, r.range(3.8f, 4.6f), 12, p.accent());
        blob(c, r, bx, by, r.range(3.2f, 4f), 8, p.accent2());
        fill.setColor(p.accent());
        c.drawCircle(ax, ay, 1.1f, fill);
        stroke.setColor(ColorMath.withAlpha(p.accent(), 0.6f));
        stroke.setStrokeWidth(0.3f);
        c.drawCircle(ax, ay, 2.2f, stroke);
        topScrim(c, p, 24);
        bottomScrim(c, p, 70);
    }

    // ---------------------------------------------------------------- scene helpers

    private void sky(Canvas c, Palette p, float mid) {
        fill.setShader(new LinearGradient(0, 0, 0, 100, new int[]{p.sky0(), p.sky1(), p.sky2()},
                new float[]{0f, mid, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 100, 100, fill);
        fill.setShader(null);
    }

    /** Radial wash from {@code a0} alpha at the center to {@code a1} at radius {@code rad}. */
    private void radial(Canvas c, float x, float y, float rad, int color, float a0, float a1) {
        fill.setShader(new RadialGradient(x, y, rad, ColorMath.withAlpha(color, a0), ColorMath.withAlpha(color, a1),
                Shader.TileMode.CLAMP));
        if (a1 > 0f) c.drawRect(0, 0, 100, 100, fill); else c.drawCircle(x, y, rad, fill);
        fill.setShader(null);
    }

    /** Stars as in the reference: random size and opacity, a few with a faint four-point sparkle. */
    private void stars(Canvas c, CoverRandom r, int color, int n, float yMax) {
        stroke.setStrokeWidth(0.12f);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * 100, y = r.nextFloat() * yMax, rad = r.nextFloat() * 0.35f + 0.1f;
            float op = r.nextFloat() * 0.7f + 0.2f;
            fill.setColor(ColorMath.withAlpha(color, op));
            c.drawCircle(x, y, rad, fill);
            if (rad > 0.4f && op > 0.6f) {
                stroke.setColor(ColorMath.withAlpha(color, op * 0.45f));
                c.drawLine(x - rad * 3.2f, y, x + rad * 3.2f, y, stroke);
                c.drawLine(x, y - rad * 3.2f, x, y + rad * 3.2f, stroke);
            }
        }
    }

    /** {@code M0 y0 Q c p T 100 end V100 H0 Z}: the smooth quadratic hill of the reference SVG. */
    private void hill(Canvas c, float y0, float cx, float cy, float px, float py, float end, int color) {
        path.reset();
        path.moveTo(0, y0);
        path.quadTo(cx, cy, px, py);
        path.quadTo(2 * px - cx, 2 * py - cy, 100, end);
        path.lineTo(100, 100);
        path.lineTo(0, 100);
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
        path.reset();
    }

    /** Jagged mountain ridge around {@code base} with peaks up to {@code peak} units high. */
    private void ridge(Canvas c, CoverRandom r, float base, float peak, int color) {
        path.reset();
        path.moveTo(0, base - r.range(0, peak));
        float x = 0;
        while (x < 100) {
            x += r.range(6, 14);
            path.lineTo(x, base - r.range(0, peak));
        }
        path.lineTo(100, 100);
        path.lineTo(0, 100);
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
        path.reset();
    }

    /** Sixteen-point star polygon (an eight-point girih star) with a grout stroke. */
    private void star(Canvas c, float cx, float cy, float rad, float inner, int fillColor, int strokeColor, float sw) {
        path.reset();
        for (int i = 0; i < 16; i++) {
            float rr = (i & 1) == 1 ? rad * inner : rad;
            double t = Math.PI * 2 * i / 16 - Math.PI / 2;
            float x = cx + rr * (float) Math.cos(t), y = cy + rr * (float) Math.sin(t);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        fill.setColor(fillColor);
        c.drawPath(path, fill);
        stroke.setColor(strokeColor);
        stroke.setStrokeWidth(sw);
        c.drawPath(path, stroke);
        path.reset();
    }

    /** {@code n} contour rings around a summit, wobbling with two seeded harmonics, fading outwards. */
    private void blob(Canvas c, CoverRandom r, float cx, float cy, float base, int n, int color) {
        float ph = r.range(0, 6.28f), squash = r.range(0.72f, 0.88f);
        for (int k = 1; k <= n; k++) {
            path.reset();
            for (int i = 0; i <= 90; i++) {
                double t = i / 90.0 * Math.PI * 2;
                double rr = base * k * (1 + 0.16 * Math.sin(3 * t + ph + k * 0.3) + 0.08 * Math.sin(5 * t - ph * 2 + k * 0.5));
                float x = cx + (float) (rr * Math.cos(t)), y = cy + (float) (rr * Math.sin(t)) * squash;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            stroke.setStrokeWidth(k == 1 ? 0.7f : k % 4 == 0 ? 0.55f : 0.38f);
            stroke.setColor(ColorMath.withAlpha(color, Math.max(0.16f, 0.92f - k * 0.075f)));
            c.drawPath(path, stroke);
        }
        path.reset();
    }

    private void bottomScrim(Canvas c, Palette p, float from) {
        fill.setShader(new LinearGradient(0, from, 0, 100, new int[]{0, ColorMath.withAlpha(p.deep(), 0.62f),
                ColorMath.withAlpha(p.deep(), 0.9f)}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, from, 100, 100, fill);
        fill.setShader(null);
    }

    private void topScrim(Canvas c, Palette p, float to) {
        fill.setShader(new LinearGradient(0, 0, 0, to, ColorMath.withAlpha(p.deep(), 0.8f), 0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 100, to, fill);
        fill.setShader(null);
    }

    // ---------------------------------------------------------------- typography

    /** Text style per scene: tracked caps (night scenes), rounded mixed case, poster, centred plaque (tiles). */
    private enum Style { CAPS, ROUND, POSTER, PLAQUE }

    private static Style style(int scene) {
        return switch (scene) {
            case CoverScenes.TILES -> Style.PLAQUE;
            case CoverScenes.POSTER -> Style.POSTER;
            case CoverScenes.ORBS, CoverScenes.CONTOURS -> Style.ROUND;
            default -> Style.CAPS;
        };
    }

    private void drawText(Canvas c, float size, CoverSpec spec, Palette p, int scene) {
        if (spec.kind() == CoverSpec.Kind.ARTIST) {
            drawInitial(c, size, spec.title(), p);
            return;
        }
        if (size < TEXT_MIN_PX || spec.title().isEmpty()) return;
        float k = size / 100f;
        // small covers: bigger type in units so it stays legible (DESIGN: ~10 px minimum at 96 px)
        float boost = size < 220 ? 1f + (220 - size) / 220f * 0.7f : 1f;
        Style st = style(scene);
        String title = spec.title(), sub = spec.subtitle();
        boolean titleRtl = ScriptFont.hasArabicScript(title);
        boolean subRtl = sub != null && ScriptFont.hasArabicScript(sub);
        float width = (100 - 2 * MARGIN) * k;

        // subtitle (artist): small tracked caps for Latin, plain Vazirmatn for Persian
        float subSize = (subRtl ? 4.8f : 4.2f) * k * Math.min(boost, 1.35f);
        boolean showSub = sub != null && subSize >= 8.5f;
        String subLine = null;
        if (showSub) {
            setFont(subRtl, subRtl ? 600 : 640, subRtl || st == Style.ROUND ? 0f : 0.3f);
            String s = subRtl || st == Style.ROUND ? sub : sub.toUpperCase(Locale.ROOT);
            text.setTextSize(subSize);
            float subWidth = width - (size >= 256 && st != Style.PLAQUE ? 11 * k : 0); // room for the mark
            subLine = text.measureText(s) <= subWidth ? s : CoverText.ellipsize(s, subSize, subWidth, this::measure);
        }

        int weight = st == Style.ROUND ? 600 : 800;
        float spacing = titleRtl ? 0f : st == Style.CAPS || st == Style.PLAQUE ? 0.06f : st == Style.POSTER ? 0.01f : 0f;
        String t = titleRtl || st == Style.ROUND ? title : title.toUpperCase(Locale.ROOT);
        float maxSize, minSize;
        int maxLines;
        switch (st) {
            case POSTER -> { maxSize = 16f; minSize = 8f; maxLines = 3; }
            case PLAQUE -> { maxSize = 8.6f; minSize = 5.2f; maxLines = 2; }
            default -> { maxSize = 8.4f; minSize = 5.4f; maxLines = 2; }
        }
        if (titleRtl) { maxSize *= 1.18f; minSize *= 1.15f; }
        setFont(titleRtl, weight, spacing);
        CoverText.Fit fit = CoverText.fit(t, width, maxLines, maxSize * k * boost, Math.min(minSize * k * boost, maxSize * k),
                this::measure);
        float ts = fit.size();
        text.setTextSize(ts);
        Paint.FontMetrics fm = text.getFontMetrics();
        float lineH = (fm.descent - fm.ascent) * (titleRtl ? 0.92f : 0.98f);
        List<String> lines = fit.lines();
        float m = MARGIN * k;

        if (st == Style.PLAQUE) {
            float subH = subLine == null ? 0 : subSize * 1.5f;
            float block = lines.size() * lineH + subH;
            float top = 74.6f * k + (25.4f * k - block) / 2f;
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(p.ink());
            float y = top - fm.ascent * 0.96f;
            for (String line : lines) {
                c.drawText(line, size / 2f, y, text);
                y += lineH;
            }
            if (subLine != null) {
                setFont(subRtl, subRtl ? 600 : 640, subRtl ? 0f : 0.3f);
                text.setTextSize(subSize);
                text.setTextAlign(Paint.Align.CENTER);
                text.setColor(ColorMath.withAlpha(p.ink(), CoverPalettes.SUBTITLE_ALPHA));
                c.drawText(subLine, size / 2f, y - lineH + fm.descent + subSize * 1.3f, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
            return;
        }

        if (st == Style.POSTER) {
            float y = 10f * k - fm.ascent * 0.9f;
            align(titleRtl);
            text.setColor(p.ink());
            for (String line : lines) {
                c.drawText(line, titleRtl ? size - m : m, y, text);
                y += lineH * 0.9f;
            }
            if (subLine != null) {
                setFont(subRtl, subRtl ? 600 : 640, subRtl ? 0f : 0.3f);
                text.setTextSize(subSize);
                align(subRtl);
                text.setColor(ColorMath.withAlpha(p.ink(), CoverPalettes.SUBTITLE_ALPHA));
                c.drawText(subLine, subRtl ? size - m : m, y - lineH * 0.9f + fm.descent + subSize * 1.9f, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
            return;
        }

        // CAPS / ROUND: artist top, title bottom (start-aligned per script, as in the reference)
        text.setColor(p.ink());
        align(titleRtl);
        float base = size - m - fm.descent * 0.6f;
        for (int i = lines.size() - 1; i >= 0; i--) {
            c.drawText(lines.get(i), titleRtl ? size - m : m, base, text);
            base -= lineH;
        }
        if (subLine != null) {
            setFont(subRtl, subRtl ? 600 : 640, subRtl || st == Style.ROUND ? 0f : 0.3f);
            text.setTextSize(subSize);
            align(subRtl);
            text.setColor(ColorMath.withAlpha(p.ink(), CoverPalettes.SUBTITLE_ALPHA));
            Paint.FontMetrics sm = text.getFontMetrics();
            c.drawText(subLine, subRtl ? size - m : m, m - sm.ascent * 0.78f, text);
        }
        text.setTextAlign(Paint.Align.LEFT);
        if (size >= 256 && scene != CoverScenes.VINYL) mark(c, k, subRtl, p);
    }

    /** Artist avatar: one large initial, centred (the scene behind it is darkened at the centre). */
    private void drawInitial(Canvas c, float size, String initial, Palette p) {
        if (size < INITIAL_MIN_PX || initial.isEmpty()) return;
        boolean rtl = ScriptFont.hasArabicScript(initial);
        setFont(rtl, 700, 0f);
        text.setTextSize(size * (rtl ? 0.4f : 0.44f));
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(ColorMath.withAlpha(p.ink(), 0.94f));
        text.getTextBounds(initial, 0, initial.length(), tmpBounds);
        c.drawText(initial, size / 2f, size / 2f - tmpBounds.exactCenterY(), text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private final android.graphics.Rect tmpBounds = new android.graphics.Rect();

    /** The tiny Mowj wave, opposite the artist line; barely there. */
    private void mark(Canvas c, float k, boolean left, Palette p) {
        float x0 = left ? MARGIN * k : (100 - MARGIN - 7) * k, y = (MARGIN + 1.6f) * k, w = 7 * k;
        path.reset();
        path.moveTo(x0, y);
        path.cubicTo(x0 + w * 0.16f, y - 1.6f * k, x0 + w * 0.34f, y - 1.6f * k, x0 + w * 0.5f, y);
        path.cubicTo(x0 + w * 0.66f, y + 1.6f * k, x0 + w * 0.84f, y + 1.6f * k, x0 + w, y);
        stroke.setStrokeWidth(0.55f * k);
        stroke.setColor(ColorMath.withAlpha(p.glow(), 0.42f));
        c.drawPath(path, stroke);
        path.reset();
    }

    private void align(boolean rtl) {
        text.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
    }

    private void setFont(boolean arabicScript, int weight, float letterSpacingEm) {
        Typeface tf = arabicScript ? arabic : latin;
        text.setTypeface(tf != null ? tf : Typeface.DEFAULT_BOLD);
        if (tf != null) {
            int w = arabicScript ? weight : Math.min(800, Math.max(400, weight));
            text.setFontVariationSettings(null); // same string twice would be skipped after a typeface switch
            text.setFontVariationSettings("'wght' " + w);
        }
        text.setLetterSpacing(letterSpacingEm);
    }

    private float measure(String s, float sizePx) {
        text.setTextSize(sizePx);
        return text.measureText(s);
    }
}
