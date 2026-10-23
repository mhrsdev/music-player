package com.mhrsdev.musicplayer.data.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** An APK passes when it is this app (same package name) and is signed with exactly the installed app's certificates. */
final class PackageApkInspector implements ApkInspector {
    private final PackageManager pm;
    private final String packageName;

    PackageApkInspector(Context context) {
        pm = context.getPackageManager();
        packageName = context.getPackageName();
    }

    @SuppressWarnings("deprecation") // GET_SIGNATURES is the only way below API 28
    @Override public Verdict inspect(File apk) {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getPath(), flags);
        if (archive == null || !packageName.equals(archive.packageName)) return Verdict.INVALID;
        try {
            Set<Signature> theirs = signers(archive);
            return !theirs.isEmpty() && theirs.equals(signers(pm.getPackageInfo(packageName, flags)))
                    ? Verdict.OK : Verdict.DIFFERENT_SIGNER;
        } catch (PackageManager.NameNotFoundException e) {
            return Verdict.INVALID; // cannot happen: we are the installed app
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<Signature> signers(PackageInfo info) {
        Signature[] sigs = Build.VERSION.SDK_INT >= 28
                ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        return sigs == null ? new HashSet<>() : new HashSet<>(Arrays.asList(sigs));
    }
}
