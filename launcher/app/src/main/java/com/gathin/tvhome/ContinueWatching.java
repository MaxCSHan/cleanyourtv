package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * continue_watching_days: what a dock app left unfinished, as one line under its name on the shelf ("Continue:
 * Show · S2 E18 · 47 min left"). Read from the TV's Watch Next rows, which apps such as Netflix write themselves,
 * with READ_TV_LISTINGS (granted over ADB; without it the line never shows). Text only: the rows' artwork is a web
 * link and Home has no network. Read again each time Home resumes, off the main thread.
 */
final class ContinueWatching {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    /** TvContract.WatchNextPrograms types: continue, next episode, new, watchlist (not a resume point). */
    static final int TYPE_CONTINUE = 0, TYPE_NEXT = 1, TYPE_WATCHLIST = 3;
    private static final long DAY_MS = 86_400_000L;
    /** Engagement times before 2001 are an app writing seconds or a placeholder, not a real time. */
    private static final long VALID_FROM_MS = 1_000_000_000_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String[] COLUMNS = {
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
    };

    private final Context context;
    private final long maxAgeMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Map<String, String> lines = Collections.emptyMap();

    ContinueWatching(Context context, int days) {
        this.context = context.getApplicationContext();
        maxAgeMs = days * DAY_MS;
    }

    /** The line for this app, or null when it has nothing recent to resume. */
    String lineFor(String pkg) {
        return pkg == null ? null : lines.get(pkg);
    }

    /** Reads the rows again; onChange runs on the main thread when any app's line changed. */
    void refresh(Runnable onChange) {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return;
        IO.execute(() -> {
            Map<String, String> next = read();
            handler.post(() -> {
                if (next.equals(lines)) return;
                lines = next;
                onChange.run();
            });
        });
    }

    private Map<String, String> read() {
        Map<String, String> result = new HashMap<>();
        Map<String, Long> newest = new HashMap<>();
        long since = System.currentTimeMillis() - maxAgeMs;
        try (Cursor c = context.getContentResolver().query(
                TvContract.WatchNextPrograms.CONTENT_URI, COLUMNS, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String pkg = c.getString(0);
                String title = c.getString(1);
                int type = c.getInt(4);
                long when = c.getLong(5);
                if (pkg == null || title == null || title.trim().isEmpty() || type == TYPE_WATCHLIST) continue;
                if (when < VALID_FROM_MS || when < since) continue;
                Long seen = newest.get(pkg);
                if (seen != null && seen >= when) continue;
                newest.put(pkg, when);
                result.put(pkg, line(type, title.trim(), c.getString(2), c.getString(3), c.getLong(6), c.getLong(7)));
            }
        } catch (RuntimeException e) {
            // A provider that refuses or is missing: no lines, as without the permission.
            return Collections.emptyMap();
        }
        return result;
    }

    private String line(int type, String title, String season, String episode, long position, long duration) {
        String text = describe(title, season, episode);
        int left = minutesLeft(position, duration);
        if (left > 0) text += " · " + context.getString(R.string.continue_minutes_left, left);
        return context.getString(type == TYPE_NEXT ? R.string.continue_up_next : R.string.continue_watching, text);
    }

    /** "Show · S2 E18" when the row numbers its season; a title like "Show - Episode 4" already names the episode. */
    static String describe(String title, String season, String episode) {
        if (season == null || season.trim().isEmpty()) return title;
        String s = "S" + season.trim();
        if (episode != null && !episode.trim().isEmpty()) s += " E" + episode.trim();
        return title + " · " + s;
    }

    /** Whole minutes still to watch, or 0 when the row has no usable position. */
    static int minutesLeft(long position, long duration) {
        if (position <= 0 || duration <= position) return 0;
        return (int) Math.max(1, Math.round((duration - position) / 60_000.0));
    }
}
