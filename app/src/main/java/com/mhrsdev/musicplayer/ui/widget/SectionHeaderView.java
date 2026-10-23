package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/** Section title (headline 26 sp) + optional supporting line + optional trailing 48 dp action. 20 dp inset. */
public class SectionHeaderView extends LinearLayout {
    private final TextView title;
    private final TextView subtitle;
    private final ImageButton action;

    public SectionHeaderView(Context context) {
        this(context, null);
    }

    public SectionHeaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int inset = (int) Tokens.dp(context, 20f);
        setPaddingRelative(inset, (int) Tokens.dp(context, 20f), (int) Tokens.dp(context, 8f), (int) Tokens.dp(context, 8f));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        title = new TextView(context);
        title.setTextAppearance(R.style.TextAppearance_MusicPlayer_Headline);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle = new TextView(context);
        subtitle.setTextAppearance(R.style.TextAppearance_MusicPlayer_Supporting);
        subtitle.setVisibility(GONE);
        texts.addView(title);
        texts.addView(subtitle);
        addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        action = new ImageButton(context, null, 0, R.style.Widget_MusicPlayer_IconButton);
        action.setVisibility(GONE);
        int t = (int) Tokens.dp(context, 48f);
        addView(action, new LayoutParams(t, t));
    }

    public void setTitle(CharSequence text) {
        title.setText(text);
    }

    public void setSubtitle(@Nullable CharSequence text) {
        subtitle.setText(text);
        subtitle.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    /** Trailing action (e.g. shuffle, chevron); pass 0 to hide. */
    public void setAction(@DrawableRes int icon, @Nullable CharSequence contentDescription,
                          @Nullable View.OnClickListener onClick) {
        action.setVisibility(icon == 0 ? GONE : VISIBLE);
        if (icon != 0) action.setImageResource(icon);
        action.setContentDescription(contentDescription);
        action.setOnClickListener(onClick);
    }
}
