package com.gathin.photodream;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The switches in SettingsActivity. All off by default: then it is the plain full-screen slideshow. Seconds per
 * picture set here wins over sync.sh's seconds.txt, so the TV's owner can change it without a computer.
 */
final class Options {
    static final String CLOCK = "clock";
    static final String DATES = "photo_dates";
    static final String SKY = "sky";
    static final String PRINTS = "prints";
    static final String WEATHER = "weather";
    static final String SECONDS = "seconds";
    /** The choices the settings cycle through. Until one is picked, seconds.txt counts (20 s without one). */
    static final int[] SECONDS_CHOICES = {10, 15, 20, 30, 60, 120, 300};

    final boolean clock;
    final boolean dates;
    final boolean sky;
    final boolean prints;
    final boolean weather;
    /** Seconds per picture chosen on the TV, or 0 for seconds.txt. */
    final int seconds;

    private Options(SharedPreferences p) {
        clock = p.getBoolean(CLOCK, false);
        dates = p.getBoolean(DATES, false);
        sky = p.getBoolean(SKY, false);
        prints = p.getBoolean(PRINTS, false);
        weather = p.getBoolean(WEATHER, false);
        seconds = p.getInt(SECONDS, 0);
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE);
    }

    /** The choice after {@code current}, wrapping around; the first one above it when it is not a choice. */
    static int nextSeconds(int current) {
        for (int s : SECONDS_CHOICES) if (s > current) return s;
        return SECONDS_CHOICES[0];
    }

    static Options load(Context context) {
        return new Options(preferences(context));
    }
}
