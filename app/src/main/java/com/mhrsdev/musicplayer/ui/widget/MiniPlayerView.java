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
 * Artwork-tinted mini-player (DESIGN.md 08/16): 76 dp, 24 dp corners, art + title/subtitle + cookie play/pause +
 * next, and a progress line along the bottom edge that is wavy while playing (static wave: no per-frame work on
 * Home). Tap or swipe up opens the player, swipe sideways skips (tape direction: left = next), swipe down dismisses.
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
        setMinimumHeight((int) (76 * d));
        int p = (int) (12 * d);
        setPadding(p, (int) (10 * d), (int) (6 * d), (int) (12 * d));
        setElevation(8 * d);
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.mp_cd_open_player));

        art = new ArtworkView(context);
        art.setCornerRadiusDp(16f);
        addView(art, new LayoutParams((int) (52 * d), (int) (52 * d)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        title = text(context, R.style.TextAppearance_MusicPlayer_Title);
        subtitle = text(context, R.style.TextAppearance_MusicPlayer_Supporting);
        texts.addView(title);
        texts.addView(subtitle);
        LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart((int) (14 * d));
        tlp.setMarginEnd((int) (8 * d));
        addView(texts, tlp);

        playPause = new MorphPlayPauseView(context);
        playPause.setOnClickListener(v -> { if (listener != null) listener.onPlayPause(); });
        addView(playPause, new LayoutParams((int) (48 * d), (int) (48 * d)));

        next = new ImageButton(context, null, 0, R.style.Widget_MusicPlayer_IconButton);
        next.setImageResource(R.drawable.ic_mp_skip_next);
        next.setContentDescription(context.getString(R.string.mp_cd_next));
        next.setOnClickListener(v -> { if (listener != null) listener.onNext(); });
        LayoutParams nlp = new LayoutParams((int) (48 * d), (int) (48 * d));
        nlp.setMarginStart((int) (4 * d));
        addView(next, nlp);

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

    public void setPlaying(boolean playing) {
        this.playing = playing;
        playPause.setPlaying(playing, true);
        invalidate();
    }

    public void setBuffering(boolean buffering) {
        playPause.setBuffering(buffering);
    }

    public void setProgress(long positionMs, long durationMs) {
        // Only redraw when the visible progress moves at least ~1 px worth.
        long old = position;
        position = positionMs;
        duration = durationMs;
        float w = getWidth() - 2 * radius;
        if (w <= 0 || durationMs <= 0 || Math.abs(positionMs - old) * w / durationMs >= 1f) invalidate();
    }

    /** Colors from the artwork: background = container, text = onContainer, button = primary. */
    public void setAccent(AccentScheme scheme) {
        accent = scheme;
        title.setTextColor(scheme.onContainer());
        subtitle.setTextColor((scheme.onContainer() & 0x00FFFFFF) | 0xC7000000);
        playPause.setColors(scheme.primary(), scheme.onPrimary());
        next.setImageTintList(ColorStateList.valueOf(scheme.onContainer()));
        progressPaint.setColor(scheme.primary());
        trackPaint.setColor((scheme.onContainer() & 0x00FFFFFF) | 0x33000000);
        updateBackground();
        invalidate();
    }

    private void updateBackground() {
        if (getWidth() == 0 || accent == null) return;
        int c = accent.container();
        bgPaint.setShader(new LinearGradient(0, 0, getWidth(), 0,
                c, com.mhrsdev.musicplayer.ui.theme.ColorMath.lerp(c, 0xFF000000, 0.25f), Shader.TileMode.CLAMP));
    }

    public ArtworkView getArtworkView() {
        return art;
    }

    public MorphPlayPauseView getPlayPauseView() {
        return playPause;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bounds.set(0, 0, w, h);
        updateBackground();
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(bounds, radius, radius, bgPaint);
        float left = radius, right = getWidth() - radius, y = getHeight() - 5 * d;
        float x = SeekMath.xFor(position, duration, left, right);
        canvas.drawLine(Math.min(right, x + 4 * d), y, right, y, trackPaint);
        if (x > left) {
            if (playing) {
                wave.rewind();
                float wl = 18 * d, a = 2 * d;
                wave.moveTo(left, y);
                for (float px = left + 2 * d; px < x; px += 2 * d) wave.lineTo(px, y + WaveMath.y(px - left, wl, a, 0f));
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
