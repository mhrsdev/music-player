package com.mhrsdev.musicplayer.data.update;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/** {@link Connectivity} on {@link ConnectivityManager} (ACCESS_NETWORK_STATE is already declared). */
final class SystemConnectivity implements Connectivity {
    private final ConnectivityManager cm;

    SystemConnectivity(Context context) {
        cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override public boolean isOnline() {
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override public boolean isMetered() {
        return cm == null || cm.isActiveNetworkMetered();
    }
}
