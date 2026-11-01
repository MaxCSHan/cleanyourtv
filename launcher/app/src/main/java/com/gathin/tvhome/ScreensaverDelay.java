package com.gathin.tvhome;

/**
 * When the TV's screensaver starts on its own: Android TV starts it when the screen would time out
 * (screensaver_activate_on_sleep), so the delay is the system's screen_off_timeout. Changed from Launcher settings
 * when the launcher may write system settings (`appops set com.gathin.tvhome WRITE_SETTINGS allow`, granted over
 * ADB); without that the row is not shown.
 */
final class ScreensaverDelay {
    /** The choices in minutes; 0 is never. */
    static final int[] MINUTES = {0, 5, 10, 15, 30, 60};
    /** screen_off_timeout for never, as the TV's own settings write it. */
    static final int NEVER_MS = Integer.MAX_VALUE;

    private ScreensaverDelay() {}

    static int timeoutMs(int minutes) {
        return minutes <= 0 ? NEVER_MS : minutes * 60_000;
    }

    /** Whole minutes for a screen_off_timeout, 0 for never (a day or more), at least 1 otherwise. */
    static int minutes(long timeoutMs) {
        if (timeoutMs <= 0 || timeoutMs >= 86_400_000L) return 0;
        return (int) Math.max(1, Math.round(timeoutMs / 60_000.0));
    }
}
