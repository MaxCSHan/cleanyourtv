package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScreensaverDelayTest {
    @Test public void neverIsTheSystemsLargestTimeout() {
        assertEquals(Integer.MAX_VALUE, ScreensaverDelay.timeoutMs(0));
        assertEquals(0, ScreensaverDelay.minutes(Integer.MAX_VALUE));
        assertEquals(0, ScreensaverDelay.minutes(-1));
    }

    @Test public void minutesRoundTrip() {
        for (int m : ScreensaverDelay.MINUTES) assertEquals(m, ScreensaverDelay.minutes(ScreensaverDelay.timeoutMs(m)));
        assertEquals(1, ScreensaverDelay.minutes(30_000));
        assertEquals(4, ScreensaverDelay.minutes(240_000));
    }
}
