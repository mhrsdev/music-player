package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.theme.AccentScheme;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Artwork-tinted mini-player (DESIGN.md 08/16): 76 dp, 24 dp corners, art + title/subtitle + previous / squircle
 * play/pause / next, and a progress line along the bottom edge that is wavy while playing (static wave: no per-frame
 * work on Home). Tap or swipe up opens the player, swipe sideways skips (tape direction: left = next), swipe down
 * dismisses.
 *
 * <p>The pill mirrors in RTL (art at the start), but the transport cluster is always laid out LTR (previous | play |
 * next, glyphs not mirrored), exactly like the full player's transport row and the system media controls (D35).
 * Both skips stay enabled whenever the pill is shown (it is hidden without a track): previous restarts the track when
 * more than 3 s in, and the owner wants both skips available everywhere, always (D36).
 */
public class MiniPlayerView extends LinearLayout {
    public interface Listener {
        void onOpen();

        void onPlayPause();

        void onNext();

        default void onPrevious() {}

        default void onDismiss() {}
    }

    private final ArtworkView art;
    private final TextView title;
    private final TextView subtitle;
    private final MorphPlayPauseView playPause;
    private final ImageButton previous;
    private final ImageButton next;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private final RectF bounds = new RectF();
    private final GestureDetector gestures;
    private final float radius;
    private final float d;
    private AccentScheme accent;
    private long position;
    private long duration;
    private boolean playing;
    private int progressInset, progressBottom;
    private boolean playingSet;
    /** The wave path is rebuilt only when the progress end moved (its phase is static, so same x = same path). */
    private float waveX = Float.NaN;
    @Nullable private Listener listener;

    public MiniPlayerView(Context context) {
        this(context, null);
    }

    public MiniPlayerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;
        radius = Tokens.RADIUS_MINI_PLAYER * d;
        setWillNotDraw(false);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        android.content.res.Resources res = getResources();
        setMinimumHeight(res.getDimensionPixelSize(R.dimen.mp_mini_player_height));
        int pv = res.getDimensionPixelSize(R.dimen.mp_mini_pad_v);
        setPaddingRelative(res.getDimensionPixelSize(R.dimen.mp_mini_pad_start), pv,
                res.getDimensionPixelSize(R.dimen.mp_mini_pad_end), pv);
        progressInset = res.getDimensionPixelSize(R.dimen.mp_mini_progress_inset);
        progressBottom = res.getDimensionPixelSize(R.dimen.mp_mini_progress_bottom);
        setElevation(8 * d);
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.mp_cd_open_player));

        art = new ArtworkView(context);
        art.setCornerRadiusDp(16f);
        int artPx = res.getDimensionPixelSize(R.dimen.mp_mini_art);
        addView(art, new LayoutParams(artPx, artPx));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        title = text(context, R.style.TextAppearance_MusicPlayer_Title);
        subtitle = text(context, R.style.TextAppearance_MusicPlayer_Supporting);
        texts.addView(title);
        texts.addView(subtitle);
        LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(res.getDimensionPixelSize(R.dimen.mp_mini_gap));
        tlp.setMarginEnd(res.getDimensionPixelSize(R.dimen.mp_mini_text_end));
        addView(texts, tlp);

        // Transport: always LTR like the full player (a mirrored row would put "next" where "previous" is expected).
        LinearLayout transport = new LinearLayout(context);
        transport.setOrientation(HORIZONTAL);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        transport.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        previous = skipButton(context, R.drawable.ic_mp_skip_previous, R.string.mp_cd_previous);
        previous.setOnClickListener(v -> { if (listener != null) listener.onPrevious(); });
        transport.addView(previous);

        playPause = new MorphPlayPauseView(context);
        playPause.setOnClickListener(v -> { if (listener != null) listener.onPlayPause(); });
        // 56 dp touch target, disc drawn inside 4 dp padding (48 dp, < 86% of the view): never touches the pill.
        int playPx = res.getDimensionPixelSize(R.dimen.mp_mini_play), playPad = res.getDimensionPixelSize(R.dimen.mp_mini_play_pad);
        playPause.setPadding(playPad, playPad, playPad, playPad);
        transport.addView(playPause, new LayoutParams(playPx, playPx));

        next = skipButton(context, R.drawable.ic_mp_skip_next, R.string.mp_cd_next);
        next.setOnClickListener(v -> { if (listener != null) listener.onNext(); });
        transport.addView(next);
        addView(transport, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(3 * d);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(3 * d);

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                performClick();
                return true;
            }

            @Override public boolean onFling(@Nullable MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (listener == null) return false;
                if (Math.abs(vy) > Math.abs(vx)) {
                    if (vy < 0) listener.onOpen(); else listener.onDismiss();
                } else if (vx < 0) {
                    listener.onNext();
                } else {
                    listener.onPrevious();
                }
                return true;
            }
        });
        setOnClickListener(v -> { if (listener != null) listener.onOpen(); });
        setAccent(ThemeController.get(context).currentAccent());
    }

    /** 44 dp target with the 24 dp glyph of the old 48 dp button (10 dp padding instead of 12). */
    private ImageButton skipButton(Context c, int icon, int description) {
        ImageButton b = new ImageButton(c, null, 0, R.style.Widget_MusicPlayer_IconButton);
        int touch = getResources().getDimensionPixelSize(R.dimen.mp_mini_touch);
        int pad = getResources().getDimensionPixelSize(R.dimen.mp_mini_skip_pad);
        b.setMinimumWidth(touch);
        b.setMinimumHeight(touch);
        b.setPadding(pad, pad, pad, pad);
        b.setImageResource(icon);
        b.setContentDescription(c.getString(description));
        b.setLayoutParams(new LayoutParams(touch, touch));
        return b;
    }

    private static TextView text(Context c, int style) {
        TextView t = new TextView(c);
        t.setTextAppearance(style);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextDirection(TEXT_DIRECTION_FIRST_STRONG_LTR);
        t.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    public void bind(Track track) {
        art.bind(track);
        title.setText(track.title());
        subtitle.setText(track.artistName());
        duration = track.durationMs();
        invalidate();
    }

    /** E.g. "NEFFEX · JBL Charge 6". */
    public void setSubtitle(CharSequence text) {
        subtitle.setText(text);
    }

    /** Called with every 4 Hz state tick: does nothing unless the value changed. */
    public void setPlaying(boolean playing) {
        if (this.playing == playing && playingSet) return;
        playingSet = true;
        this.playing = playing;
        playPause.setPlaying(playing, true);
        waveX = Float.NaN; // static line <-> wave: rebuild
        invalidate();
    }

    public void setBuffering(boolean buffering) {
        playPause.setBuffering(buffering);
    }

    public void setProgress(long positionMs, long durationMs) {
        // Only redraw when the visible progress moves at least ~1 px worth, and then only the progress strip: the pill
        // (gradient, shadow, art, texts) is not touched.
        long old = position;
        position = positionMs;
        duration = durationMs;
        float w = getWidth() - 2f * progressInset;
        if (w <= 0 || durationMs <= 0) {
            invalidate();
        } else if (Math.abs(positionMs - old) * w / durationMs >= 1f) {
            int y = Math.round(getHeight() - progressBottom), r = Math.round(4 * d) + 2;
            invalidate(0, y - r, getWidth(), y + r);
        }
    }

    /** Colors from the artwork: background = container, text = onContainer, button = primary. */
    public void setAccent(AccentScheme scheme) {
        accent = scheme;
        title.setTextColor(scheme.onContainer());
        subtitle.setTextColor((scheme.onContainer() & 0x00FFFFFF) | 0xC7000000);
        playPause.setColors(scheme.primary(), scheme.onPrimary());
        ColorStateList glyph = ColorStateList.valueOf(scheme.onContainer());
        previous.setImageTintList(glyph);
        next.setImageTintList(glyph);
        progressPaint.setColor(scheme.primary());
        trackPaint.setColor((scheme.onContainer() & 0x00FFFFFF) | 0x33000000);
        updateBackground();
        invalidate();
    }

    private void updateBackground() {
        if (accent == null) {
            bgPaint.setColor(Tokens.color(getContext(), R.attr.mpPrimaryContainer) | 0xFF000000);
            return;
        }
        if (getWidth() == 0) return;
        int c = accent.container() | 0xFF000000; // opaque over scrolling content
        bgPaint.setShader(new LinearGradient(0, 0, getWidth(), 0,
                c, com.mhrsdev.musicplayer.ui.theme.ColorMath.lerp(c, 0xFF000000, 0.25f), Shader.TileMode.CLAMP));
    }

    public ArtworkView getArtworkView() {
        return art;
    }

    public MorphPlayPauseView getPlayPauseView() {
        return playPause;
    }

    ImageButton previousButton() {
        return previous;
    }

    ImageButton nextButton() {
        return next;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bounds.set(0, 0, w, h);
        waveX = Float.NaN;
        updateBackground();
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(bounds, radius, radius, bgPaint);
        // Inset from the rounded ends and lifted off the bottom edge so the line never meets the curve.
        float left = progressInset, right = getWidth() - progressInset, y = getHeight() - progressBottom;
        float x = SeekMath.xFor(position, duration, left, right);
        canvas.drawLine(Math.min(right, x + 4 * d), y, right, y, trackPaint);
        if (x > left) {
            if (playing) {
                if (waveX != x) {
                    waveX = x;
                    wave.rewind();
                    float wl = 18 * d, a = 2 * d;
                    wave.moveTo(left, y);
                    for (float px = left + 2 * d; px < x; px += 2 * d) wave.lineTo(px, y + WaveMath.y(px - left, wl, a, 0f));
                }
                canvas.drawPath(wave, progressPaint);
            } else {
                canvas.drawLine(left, y, x, y, progressPaint);
            }
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility") // taps reach performClick via onSingleTapUp
    @Override public boolean onTouchEvent(MotionEvent e) {
        return gestures.onTouchEvent(e) || super.onTouchEvent(e);
    }

    @Override public boolean performClick() {
        return super.performClick();
    }
}
