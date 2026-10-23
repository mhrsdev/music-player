package com.mhrsdev.musicplayer.ui.library;

import android.view.View;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.mhrsdev.musicplayer.databinding.LibraryStateBinding;

/** Drives the shared empty / loading layout. */
public final class StateView {
    private StateView() { }

    public static void hide(LibraryStateBinding s) {
        s.stateRoot.setVisibility(View.GONE);
    }

    public static void loading(LibraryStateBinding s, @StringRes int title) {
        s.stateRoot.setVisibility(View.VISIBLE);
        s.stateProgress.setVisibility(View.VISIBLE);
        s.stateProgress.setContentDescription(s.getRoot().getContext().getString(title));
        s.stateIcon.setVisibility(View.GONE);
        s.stateTitle.setVisibility(View.GONE);
        s.stateTitle.setText(title);
        s.stateBody.setVisibility(View.GONE);
        s.stateAction.setVisibility(View.GONE);
    }

    public static void empty(LibraryStateBinding s, CharSequence title, @Nullable CharSequence body,
            @Nullable CharSequence action, @Nullable View.OnClickListener onAction) {
        s.stateRoot.setVisibility(View.VISIBLE);
        s.stateProgress.setVisibility(View.GONE);
        s.stateIcon.setVisibility(View.VISIBLE);
        s.stateTitle.setVisibility(View.VISIBLE);
        s.stateTitle.setText(title);
        s.stateBody.setVisibility(body == null ? View.GONE : View.VISIBLE);
        s.stateBody.setText(body);
        s.stateAction.setVisibility(action == null ? View.GONE : View.VISIBLE);
        s.stateAction.setText(action);
        s.stateAction.setOnClickListener(onAction);
    }
}
