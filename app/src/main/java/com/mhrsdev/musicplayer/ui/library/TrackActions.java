package com.mhrsdev.musicplayer.ui.library;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Track actions that leave the app's own data: share the audio file, delete it from the device. */
public class TrackActions {
    private static final AtomicInteger REQUEST_KEYS = new AtomicInteger();

    private TrackActions() { }

    /** ACTION_SEND with the content URIs; the receiver gets a temporary read grant. */
    public static void share(FragmentActivity a, List<Track> tracks) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (Track t : tracks) if (t.mediaUri() != null) uris.add(Uri.parse(t.mediaUri()));
        if (uris.isEmpty()) return;
        Intent send;
        if (uris.size() == 1) {
            send = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        send.setType("audio/*");
        ClipData clip = ClipData.newRawUri(null, uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        send.setClipData(clip); // the chooser and the target both receive the grant through the clip
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String title = tracks.size() == 1 ? tracks.get(0).title() : a.getString(R.string.u2_share);
        try {
            a.startActivity(Intent.createChooser(send, title));
        } catch (RuntimeException noTarget) {
            TrackMenuSheet.toast(a, a.getString(R.string.u2_share_failed));
        }
    }

    /**
     * Deletes the files. API 30+: the system's own confirmation (MediaStore.createDeleteRequest). Below: our
     * confirmation, then a plain delete (API 29 may ask for consent through a RecoverableSecurityException).
     * Successfully deleted tracks are removed from the library.
     */
    public static void deleteFromDevice(FragmentActivity a, List<Track> tracks) {
        List<Track> deletable = new ArrayList<>();
        for (Track t : tracks) if (t.mediaUri() != null) deletable.add(t);
        if (deletable.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            List<Uri> uris = new ArrayList<>();
            for (Track t : deletable) uris.add(Uri.parse(t.mediaUri()));
            PendingIntent pi = MediaStore.createDeleteRequest(a.getContentResolver(), uris);
            launch(a, pi.getIntentSender(), () -> forget(a, deletable));
            return;
        }
        String message = deletable.size() == 1
                ? a.getString(R.string.u2_delete_confirm_one, deletable.get(0).title())
                : a.getResources().getQuantityString(R.plurals.u2_delete_confirm_many, deletable.size(), deletable.size());
        new AlertDialog.Builder(a).setTitle(R.string.u2_delete_device).setMessage(message)
                .setPositiveButton(R.string.u2_delete, (d, w) -> withLegacyWriteAccess(a, () -> deleteLegacy(a, deletable)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** API 26-28: a plain delete needs WRITE_EXTERNAL_STORAGE, asked the first time. Later versions need nothing here. */
    private static void withLegacyWriteAccess(FragmentActivity a, Runnable then) {
        String write = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || a.checkSelfPermission(write) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            then.run();
            return;
        }
        ActivityResultLauncher<String>[] holder = new ActivityResultLauncher[1];
        holder[0] = a.getActivityResultRegistry().register("u2_write_" + REQUEST_KEYS.incrementAndGet(),
                new ActivityResultContracts.RequestPermission(), granted -> {
                    holder[0].unregister();
                    if (granted) then.run(); else TrackMenuSheet.toast(a, a.getString(R.string.u2_delete_failed));
                });
        holder[0].launch(write);
    }

    private static void deleteLegacy(FragmentActivity a, List<Track> tracks) {
        ContentResolver resolver = a.getContentResolver();
        List<Track> done = new ArrayList<>();
        for (Track t : tracks) {
            try {
                if (resolver.delete(Uri.parse(t.mediaUri()), null, null) > 0) done.add(t);
            } catch (SecurityException e) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e instanceof RecoverableSecurityException r) {
                    IntentSender sender = r.getUserAction().getActionIntent().getIntentSender();
                    launch(a, sender, () -> deleteLegacy(a, List.of(t))); // retry after consent
                } else {
                    TrackMenuSheet.toast(a, a.getString(R.string.u2_delete_failed));
                }
            }
        }
        if (!done.isEmpty()) forget(a, done);
    }

    private static void forget(FragmentActivity a, List<Track> tracks) {
        AppContainer c = Screens.container(a);
        List<String> ids = new ArrayList<>();
        for (Track t : tracks) ids.add(t.id());
        c.libraryBrowser().forgetTracks(ids);
        TrackMenuSheet.toast(a, a.getResources().getQuantityString(R.plurals.u2_deleted, ids.size(), ids.size()));
    }

    /** Starts a system consent screen from any point in time (the launcher unregisters itself after the result). */
    private static void launch(FragmentActivity a, IntentSender sender, Runnable onOk) {
        String key = "u2_delete_" + REQUEST_KEYS.incrementAndGet();
        ActivityResultLauncher<IntentSenderRequest>[] holder = new ActivityResultLauncher[1];
        holder[0] = a.getActivityResultRegistry().register(key, new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    holder[0].unregister();
                    if (result.getResultCode() == Activity.RESULT_OK) onOk.run();
                });
        holder[0].launch(new IntentSenderRequest.Builder(sender).build());
    }
}
