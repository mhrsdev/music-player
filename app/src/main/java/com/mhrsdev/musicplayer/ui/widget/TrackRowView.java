package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/**
 * Shared track list row (DESIGN.md 16): 72 dp, 52 dp art with 12 dp corners, title / [E] artist, duration, overflow.
 * Segmented-group backgrounds (outer 22 dp, inner 6 dp; add 3 dp item spacing in the RecyclerView).
 * States: now playing (tinted row, tertiary title), missing file (42% + hint), search match highlight.
 * Text keeps its own direction but aligns to the layout start (Persian titles in LTR lists sit left).
 */
public class TrackRowView extends LinearLayout {
    public static final int GROUP_NONE = 0;
    public static final int GROUP_SINGLE = 1;
    public static final int GROUP_FIRST = 2;
    public static final int GROUP_MIDDLE = 3;
    public static final int GROUP_LAST = 4;

    @IntDef({GROUP_NONE, GROUP_SINGLE, GROUP_FIRST, GROUP_MIDDLE, GROUP_LAST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface GroupPosition {}

    private final ArtworkView art;
    private final TextView title;
    private final TextView explicit;
    private final TextView subtitle;
    private final TextView duration;
    private final ImageButton more;
    private final GradientDrawable bg = new GradientDrawable();
    private final GradientDrawable mask = new GradientDrawable();
    private final int containerColor;
    private final int nowPlayingColor;
    private final int titleColor;
    private final int accentTitle;
    private final float outer;
    private final float inner;
    @GroupPosition private int group = GROUP_NONE;
    private boolean nowPlaying;
    private boolean missing;
    @Nullable private CharSequence baseSubtitle;

    public TrackRowView(Context context) {
        this(context, null);
    }

    public TrackRowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        outer = Tokens.RADIUS_GROUP_OUTER * d;
        inner = Tokens.RADIUS_GROUP_INNER * d;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight((int) (72 * d));
        setPaddingRelative((int) (12 * d), (int) (10 * d), (int) (4 * d), (int) (10 * d));
        setClickable(true);
        setFocusable(true);
        containerColor = Tokens.color(context, R.attr.mpSurfaceContainer);
        nowPlayingColor = Tokens.color(context, R.attr.mpPrimaryContainer);
        titleColor = Tokens.onSurface(context);
        accentTitle = Tokens.color(context, R.attr.mpTertiary);
        setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((titleColor & 0x00FFFFFF) | 0x1A000000), bg, mask));
        mask.setColor(0xFFFFFFFF);

        art = new ArtworkView(context);
        addView(art, new LayoutParams((int) (52 * d), (int) (52 * d)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        title = new TextView(context);
        title.setTextAppearance(R.style.TextAppearance_MusicPlayer_Title);
        styleText(title);
        texts.addView(title);
        LinearLayout subRow = new LinearLayout(context);
        subRow.setOrientation(HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        explicit = new TextView(context);
        explicit.setText("E");
        explicit.setTextSize(10.5f);
        explicit.setTypeface(Typeface.DEFAULT_BOLD);
        explicit.setGravity(Gravity.CENTER);
        explicit.setTextColor(Tokens.color(context, R.attr.mpBackground));
        explicit.setContentDescription(context.getString(R.string.mp_cd_explicit));
        GradientDrawable eb = new GradientDrawable();
        eb.setCornerRadius(4 * d);
        eb.setColor(Tokens.onSurfaceVariant(context));
        explicit.setBackground(eb);
        LayoutParams elp = new LayoutParams((int) (16 * d), (int) (16 * d));
        elp.setMarginEnd((int) (6 * d));
        subRow.addView(explicit, elp);
        subtitle = new TextView(context);
        subtitle.setTextAppearance(R.style.TextAppearance_MusicPlayer_Supporting);
        styleText(subtitle);
        subRow.addView(subtitle, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(subRow);
        LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart((int) (16 * d));
        tlp.setMarginEnd((int) (8 * d));
        addView(texts, tlp);

        duration = new TextView(context);
        duration.setTextAppearance(R.style.TextAppearance_MusicPlayer_Supporting);
        duration.setFontFeatureSettings("tnum");
        addView(duration, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        more = new ImageButton(context, null, 0, R.style.Widget_MusicPlayer_IconButton);
        more.setImageResource(R.drawable.ic_mp_more);
        more.setContentDescription(context.getString(R.string.mp_cd_more));
        addView(more, new LayoutParams((int) (48 * d), (int) (48 * d)));
        updateBackground();
    }

    private static void styleText(TextView t) {
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextDirection(TEXT_DIRECTION_FIRST_STRONG_LTR);
        t.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
    }

    public void bind(Track track) {
        art.bind(track);
        ScriptFont.setText(title, track.title());
        baseSubtitle = track.artistName();
        explicit.setVisibility(track.explicit() ? VISIBLE : GONE);
        Locale l = getResources().getConfiguration().getLocales().get(0);
        duration.setText(SeekMath.formatTime(track.durationMs(), l));
        applySubtitle();
    }

    public void setSubtitle(@Nullable CharSequence text) {
        baseSubtitle = text;
        applySubtitle();
    }

    /** Bold the matched range of the title (search). Call after {@link #bind}. */
    public void setTitleHighlight(int start, int end) {
        CharSequence t = title.getText();
        if (start < 0 || end > t.length() || start >= end) return;
        SpannableString s = new SpannableString(t.toString());
        s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ScriptFont.setText(title, s);
    }

    public void setGroupPosition(@GroupPosition int position) {
        if (group == position) return;
        group = position;
        updateBackground();
    }

    public void setNowPlaying(boolean nowPlaying) {
        if (this.nowPlaying == nowPlaying) return;
        this.nowPlaying = nowPlaying;
        title.setTextColor(nowPlaying ? accentTitle : titleColor);
        setSelected(nowPlaying);
        androidx.core.view.ViewCompat.setStateDescription(this,
                nowPlaying ? getContext().getString(R.string.mp_cd_now_playing) : null);
        updateBackground();
    }

    public void setMissing(boolean missing) {
        this.missing = missing;
        setAlpha(missing ? 0.42f : 1f);
        applySubtitle();
    }

    public void setShowDuration(boolean show) {
        duration.setVisibility(show ? VISIBLE : GONE);
    }

    public void setOnMoreClickListener(@Nullable View.OnClickListener l) {
        more.setOnClickListener(l);
        more.setVisibility(l == null ? GONE : VISIBLE);
    }

    public ArtworkView getArtworkView() {
        return art;
    }

    private void applySubtitle() {
        ScriptFont.setText(subtitle, missing ? getContext().getString(R.string.mp_track_missing) : baseSubtitle);
    }

    private void updateBackground() {
        float tl, bl;
        switch (group) {
            case GROUP_SINGLE: tl = outer; bl = outer; break;
            case GROUP_FIRST: tl = outer; bl = inner; break;
            case GROUP_LAST: tl = inner; bl = outer; break;
            case GROUP_MIDDLE: tl = inner; bl = inner; break;
            default: tl = outer; bl = outer; break;
        }
        float[] radii = {tl, tl, tl, tl, bl, bl, bl, bl};
        bg.setCornerRadii(radii);
        mask.setCornerRadii(radii);
        int c = nowPlaying ? nowPlayingColor : (group == GROUP_NONE ? 0 : containerColor);
        bg.setColor(c);
    }
}
