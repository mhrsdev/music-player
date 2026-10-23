package com.mhrsdev.musicplayer.ui.update;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LifecycleOwner;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.ReleaseInfo;

/**
 * Dismissible "Update available" strip for Home. Hidden until {@link #bind} delivers a release; tapping it calls
 * {@code onOpen} (open the update screen), the close button calls {@code onDismiss} (skip that version).
 */
public class UpdateBanner extends LinearLayout {
    private final TextView label;
    private final ImageView close;

    public UpdateBanner(Context context) {
        this(context, null);
    }

    public UpdateBanner(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float dp = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.update_banner_bg);
        setMinimumHeight(Math.round(56 * dp));
        setPaddingRelative(Math.round(20 * dp), 0, Math.round(4 * dp), 0);
        setClickable(true);
        setFocusable(true);
        setVisibility(GONE);

        label = new TextView(context);
        label.setTextSize(15);
        label.setMaxLines(2);
        label.setTextColor(themeColor(context, R.attr.mpOnPrimaryContainer));
        addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        close = new ImageView(context);
        close.setImageResource(R.drawable.ic_mp_close);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(themeColor(context, R.attr.mpOnPrimaryContainer)));
        close.setBackgroundResource(themeResource(context, android.R.attr.selectableItemBackgroundBorderless));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription(context.getString(R.string.update_banner_dismiss));
        addView(close, new LayoutParams(Math.round(48 * dp), Math.round(48 * dp)));
    }

    public void bind(LifecycleOwner owner, LiveData<ReleaseInfo> available, Runnable onOpen, Runnable onDismiss) {
        setOnClickListener(v -> onOpen.run());
        close.setOnClickListener(v -> onDismiss.run());
        available.observe(owner, r -> {
            setVisibility(r == null ? GONE : VISIBLE);
            if (r != null) label.setText(getContext().getString(R.string.update_banner, r.versionName()));
        });
    }

    private static int themeResource(Context context, int attr) {
        TypedArray a = context.obtainStyledAttributes(new int[] {attr});
        try {
            return a.getResourceId(0, 0);
        } finally {
            a.recycle();
        }
    }

    private static int themeColor(Context context, int attr) {
        TypedArray a = context.obtainStyledAttributes(new int[] {attr});
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }
}
