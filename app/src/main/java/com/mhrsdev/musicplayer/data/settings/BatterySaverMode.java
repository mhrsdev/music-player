package com.mhrsdev.musicplayer.data.settings;

/** Whether the cheaper "battery saver" visuals are forced: AUTO follows the system, ON / OFF override it. */
public enum BatterySaverMode {
    AUTO, ON, OFF;

    /** Whether cheaper visuals / no background prefetch apply, given the system's battery-saver state. */
    public boolean active(boolean systemPowerSave) {
        return this == AUTO ? systemPowerSave : this == ON;
    }
}
