package com.mhrsdev.musicplayer.domain.model;

/**
 * Headset / Bluetooth button behaviour. Gestures are counted on the one play/pause (hook) button that single-button
 * headsets and most earbuds send: single, double and triple press, or a long press (left to the system assistant by
 * default). Dedicated next / previous / play / pause keys are never remapped. Immutable.
 */
public record HeadsetConfig(HeadsetAction single, HeadsetAction doublePress, HeadsetAction triple,
        HeadsetAction longPress, boolean resumeOnConnect, boolean pauseOnDisconnect, boolean resumeAfterCall) {

    public static final HeadsetConfig DEFAULT = new HeadsetConfig(HeadsetAction.PLAY_PAUSE, HeadsetAction.NEXT,
            HeadsetAction.PREVIOUS, HeadsetAction.NONE, false, true, true);

    /** The action for {@code clicks} quick presses (1..3; more counts as 3). */
    public HeadsetAction forClicks(int clicks) {
        return switch (Math.min(clicks, 3)) {
            case 1 -> single;
            case 2 -> doublePress;
            case 3 -> triple;
            default -> HeadsetAction.NONE;
        };
    }

    /** Highest click count that still means something: the detector can fire at once when it is reached. */
    public int maxClicks() {
        if (triple != HeadsetAction.NONE) return 3;
        if (doublePress != HeadsetAction.NONE) return 2;
        return 1;
    }

    public HeadsetConfig withGesture(int index, HeadsetAction action) {
        return switch (index) {
            case 0 -> new HeadsetConfig(action, doublePress, triple, longPress, resumeOnConnect, pauseOnDisconnect, resumeAfterCall);
            case 1 -> new HeadsetConfig(single, action, triple, longPress, resumeOnConnect, pauseOnDisconnect, resumeAfterCall);
            case 2 -> new HeadsetConfig(single, doublePress, action, longPress, resumeOnConnect, pauseOnDisconnect, resumeAfterCall);
            default -> new HeadsetConfig(single, doublePress, triple, action, resumeOnConnect, pauseOnDisconnect, resumeAfterCall);
        };
    }

    public HeadsetConfig withResumeOnConnect(boolean on) {
        return new HeadsetConfig(single, doublePress, triple, longPress, on, pauseOnDisconnect, resumeAfterCall);
    }

    public HeadsetConfig withPauseOnDisconnect(boolean on) {
        return new HeadsetConfig(single, doublePress, triple, longPress, resumeOnConnect, on, resumeAfterCall);
    }

    public HeadsetConfig withResumeAfterCall(boolean on) {
        return new HeadsetConfig(single, doublePress, triple, longPress, resumeOnConnect, pauseOnDisconnect, on);
    }
}
