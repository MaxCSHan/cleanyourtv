package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ContinueWatchingTest {

    @Test
    public void namesSeasonAndEpisodeWhenTheRowNumbersTheSeason() {
        assertEquals("Show · S2 E18", ContinueWatching.describe("Show", "2", "18"));
        assertEquals("Show · S2", ContinueWatching.describe("Show", "2", null));
    }

    @Test
    public void keepsTheTitleAloneWithoutASeason() {
        // LINE TV: "Show - Episode 4" with episode 4 but no season; the title already says it.
        assertEquals("Show - Episode 4", ContinueWatching.describe("Show - Episode 4", null, "4"));
        assertEquals("Show", ContinueWatching.describe("Show", " ", "4"));
    }

    @Test
    public void minutesLeftRoundsAndNeedsAPosition() {
        assertEquals(48, ContinueWatching.minutesLeft(6_000L, 2_878_000L));
        assertEquals(1, ContinueWatching.minutesLeft(1_389_000L, 1_390_000L));
        assertEquals(0, ContinueWatching.minutesLeft(0L, 2_878_000L));
        assertEquals(0, ContinueWatching.minutesLeft(5_000L, 0L));
        assertEquals(0, ContinueWatching.minutesLeft(5_000L, 5_000L));
    }

    @Test
    public void coverNamesAreTheCoversAppsSha1() throws Exception {
        // The covers app (mm.tv.covers) stores each poster under this name; both sides must agree.
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", ContinueWatching.sha1("abc"));
    }
}
