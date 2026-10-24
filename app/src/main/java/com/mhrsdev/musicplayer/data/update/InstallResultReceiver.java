package com.mhrsdev.musicplayer.data.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;
import java.util.function.IntConsumer;

/**
 * Receives the install session's status. {@code STATUS_PENDING_USER_ACTION} opens the system confirmation; every other
 * status is passed to the repository when the process has one listening (the app is replaced on success anyway).
 */
public class InstallResultReceiver extends BroadcastReceiver {
    /** Set by {@link UpdateRepositoryImpl}; receives {@code PackageInstaller.STATUS_*}. Null in a fresh process. */
    @Nullable static volatile IntConsumer listener;

    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
            if (confirm == null) return;
            try {
                // Allowed while the app is visible (the person just tapped Install); a background start is refused by
                // background-activity-launch rules (stricter on Android 17) and must not crash the receiver.
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                IntConsumer l = listener;
                if (l != null) l.accept(PackageInstaller.STATUS_FAILURE);
            }
            return;
        }
        IntConsumer l = listener;
        if (l != null) l.accept(status);
    }
}
