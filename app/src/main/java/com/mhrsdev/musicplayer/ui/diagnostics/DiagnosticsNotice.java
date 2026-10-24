package com.mhrsdev.musicplayer.ui.diagnostics;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.diag.Diagnostics;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.util.concurrent.Executor;

/**
 * One-time card on Home after the previous run crashed, froze or was killed while visible / playing: "Mowj closed
 * unexpectedly last time" with Share report and a close button. Either action marks the incident as seen, so the card
 * never comes back for it. Hidden (GONE, no work) otherwise; the check is one small file read on the io thread.
 */
public class DiagnosticsNotice extends LinearLayout {
    private final TextView share;
    private final ImageView close;
    private long incident;

    public DiagnosticsNotice(Context context) {
        super(context);
        float dp = getResources().getDisplayMetrics().density;
        int on = Tokens.color(context, R.attr.mpOnPrimaryContainer);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.update_banner_bg);
        setPaddingRelative(Math.round(20 * dp), Math.round(14 * dp), Math.round(4 * dp), Math.round(6 * dp));
        setVisibility(GONE);
        setElevation(6 * dp);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.TOP);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.settings_ic_diagnostics);
        icon.setImageTintList(ColorStateList.valueOf(on));
        LayoutParams ilp = new LayoutParams(Math.round(24 * dp), Math.round(24 * dp));
        ilp.setMarginEnd(Math.round(14 * dp));
        ilp.topMargin = Math.round(2 * dp);
        top.addView(icon, ilp);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        TextView title = text(context, R.string.diag_notice_title, 16, on);
        TextView body = text(context, R.string.diag_notice_body, 14, on);
        body.setAlpha(0.8f);
        texts.addView(title);
        texts.addView(body);
        top.addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        close = new ImageView(context);
        close.setImageResource(R.drawable.ic_mp_close);
        close.setImageTintList(ColorStateList.valueOf(on));
        close.setBackgroundResource(themeResource(context, android.R.attr.selectableItemBackgroundBorderless));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription(context.getString(R.string.diag_notice_dismiss));
        LayoutParams clp = new LayoutParams(Math.round(48 * dp), Math.round(48 * dp));
        clp.topMargin = Math.round(-12 * dp);
        top.addView(close, clp);
        addView(top);

        share = text(context, R.string.diag_notice_share, 15, on);
        share.setTypeface(share.getTypeface(), android.graphics.Typeface.BOLD);
        share.setGravity(Gravity.CENTER_VERTICAL);
        share.setMinHeight(Math.round(48 * dp));
        share.setPaddingRelative(Math.round(38 * dp), 0, Math.round(16 * dp), 0);
        share.setBackgroundResource(themeResource(context, android.R.attr.selectableItemBackground));
        share.setClickable(true);
        share.setFocusable(true);
        addView(share, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    private static TextView text(Context context, int res, int sp, int color) {
        TextView t = new TextView(context);
        t.setText(res);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Adds the (hidden) card on top of {@code home}'s root and shows it when there is an unseen incident. */
    public static void attach(Fragment home, ViewGroup root) {
        Context c = home.requireContext();
        DiagnosticsNotice n = new DiagnosticsNotice(c);
        float dp = c.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lp.setMargins(Math.round(16 * dp), Math.round(8 * dp), Math.round(16 * dp), 0);
        root.addView(n, lp);
        Diagnostics store = Diagnostics.get(c);
        Executor io = Screens.container(home).ioExecutor;
        n.share.setOnClickListener(v -> {
            n.setVisibility(GONE);
            io.execute(() -> {
                String report = store.report(ExitText.header());
                store.markSeen(System.currentTimeMillis());
                n.post(() -> ExitText.share(n.getContext(), report));
            });
        });
        n.close.setOnClickListener(v -> {
            n.setVisibility(GONE);
            long t = n.incident;
            io.execute(() -> store.markSeen(t));
        });
        // Re-checked when the store changes: the exit history is read a moment after start.
        Diagnostics.changes().observe(home.getViewLifecycleOwner(), v -> io.execute(() -> {
            long t = store.unseenIncident();
            n.post(() -> {
                n.incident = t;
                n.setVisibility(t > 0 ? VISIBLE : GONE);
            });
        }));
    }

    /** Visible for tests. */
    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    private static int themeResource(Context context, int attr) {
        android.content.res.TypedArray a = context.obtainStyledAttributes(new int[] {attr});
        try {
            return a.getResourceId(0, 0);
        } finally {
            a.recycle();
        }
    }
}
