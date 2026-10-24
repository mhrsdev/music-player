package com.mhrsdev.musicplayer.playback;

import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;

/**
 * The player every outside surface (notification, lock screen, shade, Bluetooth, Android Auto, Assistant) talks to.
 * ExoPlayer withholds "next" on the last item (repeat off), so Samsung / Media3 hid the button. Here previous and next
 * are always available once something is queued, and the ends are defined once, in {@link #skipNext} /
 * {@link #skipPrevious}, which {@link ExoPlayerController} and {@link HeadsetControls} call too, so every surface
 * behaves the same:
 * <ul>
 *   <li>next: the following item (in shuffle order when shuffle is on); on the last item it wraps to the first item
 *       of the order. Repeat-one does not stop it (an explicit skip). Play / pause state is kept.</li>
 *   <li>previous: Media3's rule (restart when more than 3 s in, else the previous item); on the first item it just
 *       restarts it.</li>
 * </ul>
 */
public class SessionPlayer extends ForwardingPlayer {
    private static final int[] SKIP_COMMANDS = {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM};

    public SessionPlayer(Player player) {
        super(player);
    }

    @Override public Commands getAvailableCommands() {
        Commands raw = super.getAvailableCommands();
        return getMediaItemCount() == 0 ? raw : raw.buildUpon().addAll(SKIP_COMMANDS).build();
    }

    @Override public boolean isCommandAvailable(int command) {
        return getAvailableCommands().contains(command);
    }

    @Override public void seekToNext() {
        skipNext(getWrappedPlayer());
    }

    @Override public void seekToNextMediaItem() {
        skipNext(getWrappedPlayer());
    }

    @Override public void seekToPrevious() {
        skipPrevious(getWrappedPlayer());
    }

    @Override public void seekToPreviousMediaItem() {
        skipPrevious(getWrappedPlayer());
    }

    /** Next item, wrapping from the last to the first of the (shuffle) order; the play state is untouched. Takes the raw player: a SessionPlayer would call itself. */
    static void skipNext(Player p) {
        if (p.getMediaItemCount() == 0) return;
        prepareIfIdle(p);
        // hasNext ignores repeat-one (Media3 maps it to off for navigation) and follows the shuffle order.
        if (p.hasNextMediaItem()) p.seekToNextMediaItem();
        else p.seekToDefaultPosition(p.getCurrentTimeline().getFirstWindowIndex(p.getShuffleModeEnabled()));
    }

    /** Previous item, or a restart when more than 3 s in / on the first item (Media3's rule). */
    static void skipPrevious(Player p) {
        if (p.getMediaItemCount() == 0) return;
        prepareIfIdle(p);
        p.seekToPrevious();
    }

    private static void prepareIfIdle(Player p) {
        if (p.getPlaybackState() == Player.STATE_IDLE) p.prepare();
    }
}
