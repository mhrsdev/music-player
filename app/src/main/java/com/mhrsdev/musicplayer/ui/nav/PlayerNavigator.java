package com.mhrsdev.musicplayer.ui.nav;

/**
 * Implemented by {@code MainActivity}. The player and queue screens (owned by {@code ui/player} / {@code ui/queue})
 * are opened through this so no screen depends on the activity class. Get it with {@link #from(android.content.Context)}.
 */
public interface PlayerNavigator {

    /** Opens the full-screen player over the current screen (added to the back stack; back collapses it). */
    void openFullPlayer();

    /** Opens the queue sheet (over the full player if it is open). */
    void openQueue();

    /** Closes the full player (and the queue) if open; no-op otherwise. */
    void closeFullPlayer();

    /**
     * The full player fully covers the window ({@code true}: its enter fade ended) or is about to reveal the shell
     * again ({@code false}: exit fade, swipe-down drag). The host stops drawing what is hidden (see {@code PlayerCover}).
     */
    default void setPlayerCovering(boolean covering) {}

    /** Walks up the context-wrapper chain; null if no host implements the navigator. */
    @androidx.annotation.Nullable
    static PlayerNavigator from(android.content.Context context) {
        while (context instanceof android.content.ContextWrapper wrapper) {
            if (context instanceof PlayerNavigator nav) return nav;
            context = wrapper.getBaseContext();
        }
        return null;
    }
}
