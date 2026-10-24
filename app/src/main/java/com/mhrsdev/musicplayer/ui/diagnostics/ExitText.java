package com.mhrsdev.musicplayer.ui.diagnostics;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.AttrRes;
import androidx.annotation.StringRes;
import com.mhrsdev.musicplayer.BuildConfig;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.diag.Diagnostics;
import com.mhrsdev.musicplayer.diag.ExitReasons;

/** Plain-language exit reasons, their accent, and the share intent (shared by the screen and the Home notice). */
class ExitText {
    private ExitText() { }

    @StringRes static int verdict(ExitReasons.Kind kind) {
        return switch (kind) {
            case CRASH -> R.string.diag_exit_crash;
            case FROZE -> R.string.diag_exit_froze;
            case LOW_MEMORY -> R.string.diag_exit_low_memory;
            case MEMORY_LIMIT -> R.string.diag_exit_memory_limit;
            case SYSTEM -> R.string.diag_exit_system;
            case USER -> R.string.diag_exit_user;
            case UPDATE -> R.string.diag_exit_update;
            case NORMAL -> R.string.diag_exit_normal;
            case UNKNOWN -> R.string.diag_exit_unknown;
        };
    }

    /** Error tone for crashes and freezes, tertiary for system kills, primary for ordinary closes. */
    @AttrRes static int accent(ExitReasons.Kind kind) {
        return switch (kind) {
            case CRASH, FROZE, MEMORY_LIMIT -> R.attr.mpError;
            case LOW_MEMORY, SYSTEM, UNKNOWN -> R.attr.mpTertiary;
            default -> R.attr.mpPrimary;
        };
    }

    static String header() {
        return Diagnostics.deviceInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    /** The system share sheet with the report as plain text (mail, messengers, notes...). */
    static void share(Context context, String report) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diag_share_subject))
                .putExtra(Intent.EXTRA_TEXT, report);
        Intent chooser = Intent.createChooser(send, context.getString(R.string.diag_share_chooser));
        if (!(context instanceof android.app.Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(chooser);
        } catch (RuntimeException e) { // no app can receive text (rare), or the intent was too large
            android.widget.Toast.makeText(context, R.string.diag_share_subject, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
