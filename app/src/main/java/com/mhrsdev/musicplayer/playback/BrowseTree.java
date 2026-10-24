package com.mhrsdev.musicplayer.playback;

import android.content.res.Resources;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The browse tree Android Auto / Assistant see: root -> Recently added, Most played, Liked songs, Playlists, Albums,
 * Artists -> tracks. Playable items are {@link TrackMapper} items (media id = track id, content URIs, no bitmaps), so a
 * selected item can be played as is. All methods block on the database: call them off the main thread.
 */
class BrowseTree {
    static final String ROOT = "[root]";
    static final String RECENT = "[recent]", MOST = "[most]", LIKED = "[liked]", PLAYLISTS = "[playlists]",
            ALBUMS = "[albums]", ARTISTS = "[artists]";
    private static final String PLAYLIST = "[playlist]", ALBUM = "[album]", ARTIST = "[artist]";
    private static final int SHORT_LIST = 100, LONG_LIST = 300;

    private final Resources res;
    @Nullable private final LibraryReader library;

    BrowseTree(Resources res, @Nullable LibraryReader library) {
        this.res = res;
        this.library = library;
    }

    MediaItem root() {
        return folder(ROOT, res.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
    }

    /** A folder or a track; null for an unknown id. */
    @Nullable
    MediaItem item(String id) {
        return switch (id) {
            case ROOT -> root();
            case RECENT -> folder(id, res.getString(R.string.browse_recent), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
            case MOST -> folder(id, res.getString(R.string.browse_most_played), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
            case LIKED -> folder(id, res.getString(R.string.browse_liked), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
            case PLAYLISTS -> folder(id, res.getString(R.string.browse_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS);
            case ALBUMS -> folder(id, res.getString(R.string.browse_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS);
            case ARTISTS -> folder(id, res.getString(R.string.browse_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS);
            default -> {
                if (id.startsWith("[") || library == null) yield null;
                Track t = library.track(id);
                yield t == null ? null : TrackMapper.toMediaItem(t);
            }
        };
    }

    List<MediaItem> children(String parentId) {
        if (library == null) return Collections.emptyList();
        switch (parentId) {
            case ROOT:
                return List.of(item(RECENT), item(MOST), item(LIKED), item(PLAYLISTS), item(ALBUMS), item(ARTISTS));
            case RECENT: return tracks(library.recentlyAdded(SHORT_LIST));
            case MOST: return tracks(library.mostPlayed(SHORT_LIST));
            case LIKED: return tracks(library.liked(LONG_LIST));
            case PLAYLISTS: {
                List<MediaItem> out = new ArrayList<>();
                for (Playlist p : library.playlists()) {
                    out.add(folder(PLAYLIST + p.id(), p.name(), MediaMetadata.MEDIA_TYPE_PLAYLIST));
                }
                return out;
            }
            case ALBUMS: {
                List<MediaItem> out = new ArrayList<>();
                for (Album a : cap(library.albums())) {
                    out.add(folder(ALBUM + a.id(), a.title(), MediaMetadata.MEDIA_TYPE_ALBUM, a.artistName(), a.artworkUri()));
                }
                return out;
            }
            case ARTISTS: {
                List<MediaItem> out = new ArrayList<>();
                for (Artist a : cap(library.artists())) {
                    out.add(folder(ARTIST + a.id(), a.name(), MediaMetadata.MEDIA_TYPE_ARTIST, null, a.imageUri()));
                }
                return out;
            }
            default:
                if (parentId.startsWith(PLAYLIST)) return tracks(library.playlistTracks(parentId.substring(PLAYLIST.length())));
                if (parentId.startsWith(ALBUM)) return tracks(library.albumTracks(parentId.substring(ALBUM.length())));
                if (parentId.startsWith(ARTIST)) return tracks(library.artistTracks(parentId.substring(ARTIST.length())));
                return Collections.emptyList();
        }
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() > LONG_LIST ? list.subList(0, LONG_LIST) : list;
    }

    private static List<MediaItem> tracks(List<Track> tracks) {
        List<MediaItem> out = new ArrayList<>(tracks.size());
        for (Track t : tracks) if (TrackMapper.isPlayable(t)) out.add(TrackMapper.toMediaItem(t));
        return out;
    }

    private static MediaItem folder(String id, String title, int type) {
        return folder(id, title, type, null, null);
    }

    private static MediaItem folder(String id, String title, int type, @Nullable String subtitle, @Nullable String artworkUri) {
        MediaMetadata.Builder meta = new MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle)
                .setIsBrowsable(true).setIsPlayable(false).setMediaType(type);
        if (artworkUri != null) meta.setArtworkUri(android.net.Uri.parse(artworkUri));
        return new MediaItem.Builder().setMediaId(id).setMediaMetadata(meta.build()).build();
    }
}
