package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.LyricWord;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;

import java.util.List;

/**
 * One lyric line, drawn by hand from a {@link StaticLayout} that is built once per text/width/size. Everything that
 * moves is done in {@link #onDraw} with reused objects (no allocation per frame):
 * <ul>
 *   <li>active line: unsung words dim, sung words full colour, the word being sung filled by a moving gradient edge
 *       (one {@link LinearGradient} per colour, only its local matrix changes) and lifted a little while sung;</li>
 *   <li>other lines: alpha and (API 31+, when allowed) a cached blur {@link RenderEffect} by distance from the
 *       active line; scale 0.94 → 1 on the spring curve when a line becomes active.</li>
 * </ul>
 * Each line takes its own paragraph direction from its text (first strong character), so Persian lines run RTL and
 * align right even inside an English song, and mixed lines keep the bidi order of the layout.
 */
public class LyricLineView extends View {
    /** Shared playback clock: the interpolated position for this frame. */
    public interface Clock { long positionMs(); }

    private static Typeface latin, persian;

    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Path sungPath = new Path();
    private final RectF word = new RectF();
    private final Matrix gradMatrix = new Matrix();
    private final float padH, padV, rise, maxContent;
    private LinearGradient sweep;

    @Nullable private LyricLine line;
    @Nullable private StaticLayout layout;
    private int layoutWidth = -1;
    private float textPx;
    private int color = 0xFFFFFFFF;
    @Nullable private Clock clock;
    private boolean rtl;
    private boolean decorative = true;

    // emphasis animation: emphasis 0..1 and distance from the active line (signed, past < 0)
    private float emphasis, distance = 1f, fromEmphasis, toEmphasis, fromDistance, toDistance = 1f;
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
    private float blurMaxPx;
    private boolean blurAllowed;
    private int appliedBlurBucket = -1;

    public LyricLineView(Context c) {
        super(c);
        float d = getResources().getDisplayMetrics().density;
        padH = 28 * d;
        padV = 9 * d;
        rise = 2.5f * d;
        maxContent = 560 * d;
        blurMaxPx = 7 * d;
        setFocusable(true);
        anim.setInterpolator(LyricsMotion.SPRING);
        anim.setDuration(460);
        anim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            emphasis = fromEmphasis + (toEmphasis - fromEmphasis) * t;
            distance = fromDistance + (toDistance - fromDistance) * t;
            applyBlur();
            invalidate();
        });
        if (latin == null) {
            try {
                latin = ResourcesCompat.getFont(c, R.font.google_sans_flex);
                persian = ResourcesCompat.getFont(c, R.font.vazirmatn);
            } catch (RuntimeException e) {
                latin = Typeface.DEFAULT_BOLD;
            }
        }
    }

    public void bind(LyricLine l, float textSp, int textColor, Clock c) {
        boolean changed = line == null || !line.text().equals(l.text());
        line = l;
        clock = c;
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp, getResources().getDisplayMetrics());
        if (px != textPx) changed = true;
        textPx = px;
        setTextColor(textColor);
        setContentDescription(l.text());
        if (changed) {
            layout = null;
            boolean fa = ScriptFont.hasArabicScript(l.text());
            Typeface tf = fa && persian != null ? persian : latin != null ? latin : Typeface.DEFAULT_BOLD;
            paint.setTypeface(tf);
            if (Build.VERSION.SDK_INT >= 26) paint.setFontVariationSettings(fa ? "'wght' 720" : "'wght' 700");
            requestLayout();
        }
    }

    public void setTextColor(int c) {
        if (c == color && sweep != null) return;
        color = c;
        // x 0..1: full colour, then transparent; CLAMP keeps colour before 0 and nothing after 1.
        sweep = new LinearGradient(0, 0, 1, 0, new int[]{c, c & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        invalidate();
    }

    /** Low-power mode: no blur, no lift (battery saver, reduce motion, low-RAM device, API < 31). */
    public void setEffects(boolean blur, boolean decorativeMotion) {
        blurAllowed = blur && Build.VERSION.SDK_INT >= 31;
        decorative = decorativeMotion;
        appliedBlurBucket = -1;
        applyBlur();
    }

    /**
     * @param newDistance signed distance from the active line (0 = active, -1 = the line before, ...)
     * @param animate     spring to the new state (else jump)
     */
    public void setDistance(float newDistance, boolean animate) {
        float e = newDistance == 0f ? 1f : 0f;
        if (!animate) {
            anim.cancel();
            emphasis = toEmphasis = e;
            distance = toDistance = newDistance;
            applyBlur();
            invalidate();
            return;
        }
        if (toDistance == newDistance && toEmphasis == e) return;
        anim.cancel();
        fromEmphasis = emphasis;
        fromDistance = distance;
        toEmphasis = e;
        toDistance = newDistance;
        anim.start();
    }

    public boolean isActiveLine() { return toEmphasis > 0.5f; }

    public boolean hasWordTiming() { return line != null && line.hasWords(); }

    private void applyBlur() {
        if (Build.VERSION.SDK_INT < 31) return;
        int bucket = blurAllowed ? Math.round(LyricsMotion.blurForDistance(distance, blurMaxPx) * 2f) : 0;
        if (bucket == appliedBlurBucket) return;
        appliedBlurBucket = bucket;
        setRenderEffect(LyricsBlur.effect(bucket));
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int content = (int) Math.max(1, Math.min(w - 2 * padH, maxContent));
        if (layout == null || layoutWidth != content) buildLayout(content);
        int h = (int) (layout.getHeight() + 2 * padV);
        setMeasuredDimension(w, h);
    }

    private void buildLayout(int width) {
        String text = line == null ? "" : line.text();
        paint.setTextSize(textPx);
        paint.setColor(color);
        StaticLayout.Builder b = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1.08f)
                .setIncludePad(false);
        if (Build.VERSION.SDK_INT >= 26) b.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
        b.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        layout = b.build();
        layoutWidth = width;
        rtl = layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT;
    }

    @Override protected void onDraw(Canvas c) {
        StaticLayout l = layout;
        if (l == null || line == null) return;
        float left = (getWidth() - layoutWidth) / 2f;
        float pivotX = rtl ? left + layoutWidth : left;
        float scale = LyricsMotion.scaleFor(emphasis);
        int save = c.save();
        c.translate(left, padV);
        c.scale(scale, scale, pivotX - left, l.getHeight() / 2f);

        boolean past = distance < 0;
        float lineAlpha = LyricsMotion.alphaForDistance(distance, past, blurAllowed);
        List<LyricWord> words = line.words();
        long pos = clock == null ? 0 : clock.positionMs();
        if (emphasis < 0.01f || words == null) {
            drawPlain(c, l, Math.round(255 * lineAlpha));
        } else {
            drawSung(c, l, words, pos, lineAlpha);
        }
        c.restoreToCount(save);
    }

    private void drawPlain(Canvas c, StaticLayout l, int alpha) {
        paint.setShader(null);
        paint.setColor(color);
        paint.setAlpha(alpha * (color >>> 24) / 255);
        l.draw(c);
    }

    /** Active line: dim base, sung words solid, the current word swept by the gradient edge. */
    private void drawSung(Canvas c, StaticLayout l, List<LyricWord> words, long pos, float lineAlpha) {
        // As the line fades out (emphasis 1 -> 0) the dim/bright contrast fades with it.
        float dim = 0.42f + (lineAlpha - 0.42f) * (1f - emphasis);
        int k = line.activeWordIndex(pos);
        float p = k >= 0 ? words.get(k).progress(pos) : 0f;
        boolean lifting = decorative && k >= 0 && p > 0f && p < 1f;
        float lift = lifting ? rise * LyricsMotion.bell(p) * (line.estimated() ? 0.6f : 1f) * emphasis : 0f;

        // 1. dim base everywhere except the lifted word
        int save = c.save();
        if (lift > 0f && wordRect(l, words.get(k), word)) c.clipOutRect(word);
        drawPlain(c, l, Math.round(255 * dim));
        c.restoreToCount(save);

        // 2. fully sung words in full colour (one clip path, one draw)
        sungPath.rewind();
        for (int i = 0; i < k; i++) if (wordRect(l, words.get(i), word)) sungPath.addRect(word, Path.Direction.CW);
        if (k >= 0 && p >= 1f && wordRect(l, words.get(k), word)) sungPath.addRect(word, Path.Direction.CW);
        if (!sungPath.isEmpty()) {
            save = c.save();
            c.clipPath(sungPath);
            drawPlain(c, l, Math.round(255 * emphasis + 255 * dim * (1 - emphasis)));
            c.restoreToCount(save);
        }

        // 3. the word being sung: lifted, dim copy + bright copy masked by the moving gradient edge
        if (k < 0 || p >= 1f || !wordRect(l, words.get(k), word)) return;
        save = c.save();
        c.translate(0, -lift);
        if (lift > 0f) {
            c.save();
            c.clipRect(word);
            drawPlain(c, l, Math.round(255 * dim));
            c.restore();
        }
        LyricWord w = words.get(k);
        boolean wordRtl = isRtlWord(l, w);
        float x0 = wordRtl ? word.right : word.left, x1 = wordRtl ? word.left : word.right;
        float feather = Math.max(word.width() * (line.estimated() ? 0.7f : 0.35f), textPx * 0.5f);
        // edge travels from x0 - feather (nothing lit) to x1 + feather (all lit)
        float edge = x0 + (x1 - x0) * p + (wordRtl ? -1 : 1) * feather * (2 * p - 1) * 0.5f;
        gradMatrix.setScale(wordRtl ? -feather : feather, 1f);
        gradMatrix.postTranslate(wordRtl ? edge + feather / 2f : edge - feather / 2f, 0);
        sweep.setLocalMatrix(gradMatrix);
        c.clipRect(word);
        paint.setShader(sweep);
        paint.setAlpha(Math.round(255 * emphasis));
        l.draw(c);
        paint.setShader(null);
        c.restoreToCount(save);
    }

    /** Bounds of a word on its first layout line (taller than the line so the lift is not cut). */
    private boolean wordRect(StaticLayout l, LyricWord w, RectF out) {
        int len = l.getText().length();
        int s = Math.min(w.start(), len), e = Math.min(w.end(), len);
        if (e <= s) return false;
        int ls = l.getLineForOffset(s);
        int le = l.getLineForOffset(Math.max(s, e - 1));
        float xs = l.getPrimaryHorizontal(s);
        float xe = le == ls && l.getLineForOffset(e) == ls ? l.getPrimaryHorizontal(e)
                : (l.getParagraphDirection(ls) == Layout.DIR_RIGHT_TO_LEFT ? l.getLineLeft(ls) : l.getLineRight(ls));
        if (le == ls && e == l.getLineEnd(ls)) xe = l.getParagraphDirection(ls) == Layout.DIR_RIGHT_TO_LEFT
                ? l.getLineLeft(ls) : l.getLineRight(ls);
        float pad = rise * 2f;
        out.set(Math.min(xs, xe) - 1, l.getLineTop(ls) - pad, Math.max(xs, xe) + 1, l.getLineBottom(ls) + pad);
        return out.width() > 1;
    }

    private static boolean isRtlWord(StaticLayout l, LyricWord w) {
        int s = Math.min(w.start(), l.getText().length() - 1);
        return s >= 0 && l.isRtlCharAt(s);
    }

    @Override protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }
}
