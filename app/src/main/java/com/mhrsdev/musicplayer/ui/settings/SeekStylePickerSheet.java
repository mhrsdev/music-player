package com.mhrsdev.musicplayer.ui.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import com.mhrsdev.musicplayer.ui.widget.BaseSeekView;
import com.mhrsdev.musicplayer.ui.widget.SeekShapes;
import com.mhrsdev.musicplayer.ui.widget.SeekStyles;

/**
 * D25 seek-bar style picker: an opaque bottom sheet listing every style as a live preview card (the real view, a
 * sample loudness envelope, 40 % progress), with its name, a one-line description and a check on the current one.
 * A tap applies instantly through the settings store; an open player swaps its bar live (it observes the setting).
 * Previews "play" so the moving styles demo themselves, but only while this sheet is visible and motion is
 * allowed (the views gate their own frames on visibility, reduce motion and battery saver).
 */
public class SeekStylePickerSheet extends BottomSheetDialogFragment {
    public static final String TAG = "seek_style_picker";
    static final long DEMO_DURATION_MS = 214_000L;
    static final float DEMO_PROGRESS = 0.4f;

    private final LinearLayout[] cards = new LinearLayout[SeekBarStyle.values().length];
    private final ImageView[] checks = new ImageView[SeekBarStyle.values().length];

    public static void show(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) new SeekStylePickerSheet().show(fm, TAG);
    }

    @Override public void onStart() {
        super.onStart();
        View sheet = getDialog() == null ? null : getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackground(null);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return buildContent(requireContext(), this::apply);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Screens.container(requireContext()).settings().seekBarStyle().observe(getViewLifecycleOwner(), this::markSelected);
    }

    private void apply(SeekBarStyle style) {
        Screens.container(requireContext()).settings().setSeekBarStyle(style);
    }

    /** Sheet body; also used directly by the render test. */
    View buildContent(Context c, java.util.function.Consumer<SeekBarStyle> onPick) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.queue_sheet_bg);
        root.setPadding(dp(c, 16), 0, dp(c, 16), dp(c, 16));

        View handle = new View(c);
        handle.setBackgroundResource(R.drawable.queue_handle_bg);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.topMargin = dp(c, 12);
        root.addView(handle, hl);

        TextView title = text(c, R.string.settings_seek_style, 22, R.attr.mpOnSurface, true);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        title.setPadding(dp(c, 8), dp(c, 16), dp(c, 8), dp(c, 2));
        root.addView(title);
        TextView sub = text(c, R.string.seek_style_sheet_sub, 14, R.attr.mpOnSurfaceVariant, false);
        sub.setPadding(dp(c, 8), 0, dp(c, 8), dp(c, 12));
        root.addView(sub);

        NestedScrollView scroll = new NestedScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        float[] sample = SeekShapes.sampleEnvelope();
        int active = Tokens.primary(c);
        int inactive = SeekShapes.withAlpha(Tokens.color(c, R.attr.mpOnSurface), 0.26f);
        for (SeekBarStyle s : SeekBarStyle.values()) {
            list.addView(card(c, s, sample, active, inactive, onPick));
        }
        return root;
    }

    private View card(Context c, SeekBarStyle s, float[] sample, int active, int inactive,
                      java.util.function.Consumer<SeekBarStyle> onPick) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 16), dp(c, 12), dp(c, 12), dp(c, 4));
        card.setClickable(true);
        card.setFocusable(true);
        String name = c.getString(SeekStyles.nameRes(s));
        String desc = c.getString(SeekStyles.descRes(s));
        card.setContentDescription(name + ". " + desc);
        card.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            onPick.accept(s);
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.bottomMargin = dp(c, 8);
        card.setLayoutParams(cl);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(c, 0, 17, R.attr.mpOnSurface, true);
        n.setText(name);
        TextView d = text(c, 0, 13, R.attr.mpOnSurfaceVariant, false);
        d.setText(desc);
        texts.addView(n);
        texts.addView(d);
        head.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView check = new ImageView(c);
        check.setImageResource(R.drawable.ic_mp_check);
        check.setColorFilter(Tokens.color(c, R.attr.mpOnPrimary));
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(active);
        check.setBackground(dot);
        check.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        check.setContentDescription(c.getString(R.string.seek_style_selected));
        head.addView(check, new LinearLayout.LayoutParams(dp(c, 28), dp(c, 28)));
        card.addView(head);

        BaseSeekView preview = SeekStyles.create(c, s);
        preview.setEnabled(false); // the card is the touch target; the preview only shows
        preview.setFocusable(false);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        preview.setColors(active, inactive);
        preview.setDuration(DEMO_DURATION_MS);
        preview.setBuffered(DEMO_DURATION_MS);
        preview.setWaveform(sample);
        preview.setPosition(Math.round(DEMO_DURATION_MS * DEMO_PROGRESS));
        preview.setPlaying(true);
        card.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 56)));

        int idx = s.ordinal();
        cards[idx] = card;
        checks[idx] = check;
        paint(c, card, check, false);
        return card;
    }

    void markSelected(@Nullable SeekBarStyle selected) {
        Context c = getContext();
        if (c == null) return;
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] == null) continue;
            boolean on = selected != null && selected.ordinal() == i;
            paint(c, cards[i], checks[i], on);
        }
    }

    /** Selected: a primary-tinted container with a 2 dp primary outline and the check; others: plain container. */
    static void paint(Context c, LinearLayout card, ImageView check, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20f * c.getResources().getDisplayMetrics().density);
        int surface = Tokens.color(c, R.attr.mpSurfaceContainer);
        int primary = Tokens.primary(c);
        bg.setColor(on ? SeekShapes.blend(surface, primary, 0.12f) : surface);
        if (on) bg.setStroke(dp(c, 2), primary);
        card.setBackground(bg);
        card.setSelected(on);
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private static TextView text(Context c, int res, float sp, int colorAttr, boolean bold) {
        TextView t = new TextView(c);
        if (res != 0) t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Tokens.color(c, colorAttr));
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
