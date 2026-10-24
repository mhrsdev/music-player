package com.mhrsdev.musicplayer.ui.widget;

import com.mhrsdev.musicplayer.ui.motion.PeriodicTick;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.ui.theme.AccentScheme;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;

/**
 * Reusable "background that follows the content": a base gradient plus the {@link AmbientField} colour blobs, both
 * from the content's artwork ({@link AmbientPalette}, cached per artwork key).
 *
 * <ul>
 *   <li>{@link Mode#SOFT} (headers, Home top glow): a STATIC field. It never animates, except the single cross-fade
 *       when {@link #setSource} changes the content.</li>
 *   <li>{@link Mode#FULL} (player/lyrics): the field drifts at <= 15 fps, only while {@link #setAnimating} is on
 *       (caller: visible + playing), the view is shown and decorative motion is allowed.</li>
 * </ul>
 * Cross-fade: one {@link ObjectAnimator} (<= 600 ms) between two prepared layers, alpha only; none under reduce
 * motion / battery saver. "Colors from artwork" off -> the app's neutral accent (lapis base, or whatever the theme
 * controller provides, e.g. dynamic colour). Memory: one 96 px artwork request through {@link ArtworkLoader} (shared
 * cache), no bitmap kept by this view.
 *
 * <p>Usage: {@code view.setMode(Mode.SOFT); view.setSource(album.artworkUri(), placeholderSeedColor, true);}
 */
public class AmbientBackdropView extends View {
    public enum Mode { SOFT, FULL }

    static final long FRAME_MS = 66;
    private static final int ART_PX = 96;
    private static final Property<AmbientBackdropView, Float> MIX =
            new Property<>(Float.class, "mix") {
                @Override public Float get(AmbientBackdropView v) { return v.mix; }
                @Override public void set(AmbientBackdropView v, Float value) {
                    v.mix = value;
                    v.invalidate();
                }
            };

    private static class Layer {
        final Paint base = new Paint();
        final AmbientField field = new AmbientField();
        @Nullable AmbientPalette palette;
    }

    private Layer back = new Layer(), front = new Layer();
    private float mix = 1f;
    private Mode mode = Mode.SOFT;
    @Nullable private ObjectAnimator fade;
    private boolean animating;
    private final long start = SystemClock.uptimeMillis();
    @Nullable private String wantedKey;
    private final PeriodicTick ticker = new PeriodicTick(FRAME_MS, this::frame);
    private final ArtworkLoader.Callback onArt = this::onArtwork;
    private boolean pendingAnimate;
    private int pendingSeed;

    public AmbientBackdropView(Context c) { this(c, null); }

    public AmbientBackdropView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        front.palette = neutral();
        com.mhrsdev.musicplayer.ui.motion.FrameRateVote.decorative(this, true); // slow drift / one fade
    }

    public void setMode(Mode m) {
        if (mode == m) return;
        mode = m;
        if (m == Mode.SOFT) setAnimating(false);
        invalidate();
    }

    public Mode getMode() { return mode; }

    /**
     * Shows the colours of {@code artworkUri} (or of {@code seedColor} while loading / when there is no artwork).
     * Repeated calls with the same source do nothing.
     */
    public void setSource(@Nullable String artworkUri, int seedColor, boolean animate) {
        boolean dark = isDark();
        String key = (artworkUri == null ? "seed:" + seedColor : artworkUri);
        if (key.equals(wantedKey)) return;
        wantedKey = key;
        if (!artworkColorsOn()) { show(neutral(), animate); return; }
        AmbientPalette hit = AmbientPalette.cached(key, dark);
        if (hit != null) { show(hit, animate); return; }
        if (artworkUri == null) {
            AmbientPalette p = AmbientPalette.fromSeed(seedColor, dark);
            AmbientPalette.put(key, dark, p);
            show(p, animate);
            return;
        }
        pendingAnimate = animate;
        pendingSeed = seedColor;
        ArtworkLoader.get(getContext()).load(artworkUri, ART_PX, onArt);
    }

    /** Direct palette (e.g. the player already has the cover bitmap). */
    public void setPalette(AmbientPalette p, boolean animate) {
        wantedKey = null;
        show(p, animate);
    }

    /** Convenience: palette from a bitmap the caller already decoded. */
    public void setArtwork(@Nullable Bitmap art, @Nullable String key, int seedColor, boolean animate) {
        boolean dark = isDark();
        if (!artworkColorsOn()) { show(neutral(), animate); return; }
        AmbientPalette p = art == null ? AmbientPalette.fromSeed(seedColor, dark) : AmbientPalette.fromBitmap(art, dark);
        if (key != null) AmbientPalette.put(key, dark, p);
        wantedKey = key;
        show(p, animate);
    }

    private void onArtwork(String loaderKey, @Nullable Bitmap bitmap) {
        String key = wantedKey;
        if (key == null || !loaderKey.equals(ArtworkLoader.key(key, ART_PX))) return; // superseded
        boolean dark = isDark();
        AmbientPalette p = bitmap == null ? AmbientPalette.fromSeed(pendingSeed, dark) : AmbientPalette.fromBitmap(bitmap, dark);
        AmbientPalette.put(key, dark, p);
        show(p, pendingAnimate);
    }

    /** FULL mode only: drift while {@code on} (caller: visible + playing) and motion is allowed. */
    public void setAnimating(boolean on) {
        boolean run = on && mode == Mode.FULL && MotionPolicy.backdropMotionEnabled(getContext());
        if (run == animating) return;
        animating = run;
        if (run) ticker.start(); else ticker.stop();
    }

    public boolean isAnimating() { return animating; }

    @Nullable public AmbientPalette palette() { return front.palette; }

    private void show(AmbientPalette p, boolean animate) {
        Layer l = back;
        back = front;
        front = l;
        front.palette = p;
        build(front);
        if (fade != null) fade.cancel();
        if (!animate || !MotionPolicy.decorativeMotionEnabled(getContext()) || !isLaidOut()) {
            mix = 1f;
            invalidate();
            return;
        }
        mix = 0f;
        fade = ObjectAnimator.ofFloat(this, MIX, 0f, 1f).setDuration(600);
        fade.start();
    }

    private void build(Layer l) {
        int h = getHeight();
        AmbientPalette p = l.palette;
        if (p == null || h == 0) return;
        l.base.setShader(new LinearGradient(0, 0, 0, h, p.top(), p.bottom(), Shader.TileMode.CLAMP));
        l.field.setColors(p.blobs());
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        build(back);
        build(front);
    }

    /** One drift frame; false (stop) the moment the view is not on screen. */
    private boolean frame() {
        if (!animating) return false;
        if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE || !isShown()) { animating = false; return false; }
        invalidate();
        return true;
    }

    private void drawLayer(Canvas c, Layer l, int alpha, float sec) {
        if (l.palette == null) return;
        l.base.setAlpha(alpha);
        c.drawRect(0, 0, getWidth(), getHeight(), l.base);
        l.field.draw(c, getWidth(), getHeight(), alpha * 7 / 10, sec, mode == Mode.FULL ? 1f : 0f);
    }

    @Override protected void onDraw(Canvas c) {
        float sec = mode == Mode.FULL ? (SystemClock.uptimeMillis() - start) / 1000f : 0f;
        if (mix < 1f) drawLayer(c, back, 255, sec);
        drawLayer(c, front, Math.round(255 * mix), sec);
    }

    @Override protected void onDetachedFromWindow() {
        if (fade != null) fade.cancel();
        ticker.stop();
        animating = false;
        super.onDetachedFromWindow();
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean artworkColorsOn() {
        try {
            return new com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore(getContext()).isArtworkColors();
        } catch (RuntimeException e) {
            return true; // no app container (previews): colours on
        }
    }

    /** "Colors from artwork" off / no source: the theme's neutral accent (lapis base, or dynamic colour). */
    private AmbientPalette neutral() {
        AccentScheme a;
        try {
            a = ThemeController.get(getContext()).currentAccent();
        } catch (RuntimeException e) {
            a = AccentScheme.base(isDark());
        }
        boolean dark = isDark();
        if (artworkColorsOn()) a = AccentScheme.base(dark); // the live accent may be artwork-tinted
        return AmbientPalette.fromSeed(dark ? a.container() : a.primary(), dark);
    }
}
