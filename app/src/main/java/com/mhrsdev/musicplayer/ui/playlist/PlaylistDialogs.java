package com.mhrsdev.musicplayer.ui.playlist;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;

/** Small dialogs for playlist management: name entry (create / rename) and delete confirmation. */
public class PlaylistDialogs {
    private PlaylistDialogs() { }

    /** Asks for a name and creates the playlist; {@code created} (may be null) receives it. */
    public static void create(Context context, LibraryRepository library, @Nullable Callback<Playlist> created) {
        askName(context, R.string.library_new_playlist, "", R.string.playlist_create,
                name -> library.createPlaylist(name, created));
    }

    public static void rename(Context context, String current, Callback<String> renamed) {
        askName(context, R.string.u2_rename_playlist, current, R.string.u2_rename, renamed);
    }

    public static void confirmDelete(Context context, String name, Runnable delete) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.u2_delete_playlist)
                .setMessage(context.getString(R.string.u2_delete_playlist_message, name))
                .setPositiveButton(R.string.u2_delete, (d, w) -> delete.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void askName(Context context, int title, String initial, int positive, Callback<String> onName) {
        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setHint(R.string.playlist_new_hint);
        field.setText(initial);
        field.setSelection(initial.length());
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        FrameLayout box = new FrameLayout(context);
        int pad = Math.round(24 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 3, pad, 0);
        box.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title).setView(box)
                .setPositiveButton(positive, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Runnable submit = () -> {
            String name = field.getText().toString().trim();
            if (name.isEmpty()) {
                field.setError(context.getString(R.string.playlist_name_required));
                return;
            }
            dialog.dismiss();
            onName.onResult(name);
        };
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit.run()));
        field.setOnEditorActionListener((v, id, e) -> {
            submit.run();
            return true;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        field.requestFocus();
    }
}
