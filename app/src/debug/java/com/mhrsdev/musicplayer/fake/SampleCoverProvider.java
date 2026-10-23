package com.mhrsdev.musicplayer.fake;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Debug-only: serves app/src/debug/assets/sample-covers/ as content://com.mhrsdev.musicplayer.samplecovers/NAME.jpg so
 * the unchanged ArtworkLoader can decode {@link SampleData} covers (galleries, Robolectric screenshots). Not in release.
 */
public final class SampleCoverProvider extends ContentProvider {
    @Override public boolean onCreate() {
        return true;
    }

    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[a-z0-9-]+\\.jpg")) throw new FileNotFoundException(String.valueOf(uri));
        try {
            return getContext().getAssets().openFd("sample-covers/" + name);
        } catch (IOException e) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
    }

    @Override public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
