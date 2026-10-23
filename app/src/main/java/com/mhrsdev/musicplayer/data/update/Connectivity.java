package com.mhrsdev.musicplayer.data.update;

/** What the automatic check needs to know about the network. */
interface Connectivity {

    boolean isOnline();

    boolean isMetered();
}
