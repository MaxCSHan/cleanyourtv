package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * continue_watching_days: what a dock app left unfinished, as one line under its name on the shelf ("Continue:
 * Show · S2 E18 · 47 min left"), and with continue_watching_row the newest unfinished shows from every app, each with
 * the link that resumes it. Read from the TV's Watch Next rows, which apps such as Netflix write themselves, with
 * READ_TV_LISTINGS (granted over ADB; without it nothing shows). The rows' artwork is a web link and Home has no
 * network: with continue_watching_covers a separate covers app (mm.tv.covers) fetches the posters and hands them over
 * as files; without it the row is text and the app's icon. Read again each time Home resumes, off the main thread.
 */
final class ContinueWatching {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    /** TvContract.WatchNextPrograms types: continue, next episode, new, watchlist (not a resume point). */
    static final int TYPE_CONTINUE = 0, TYPE_NEXT = 1, TYPE_WATCHLIST = 3;
    /** The most shows the row holds. */
    static final int MAX_ITEMS = 5;
    private static final long DAY_MS = 86_400_000L;
    /** Engagement times before 2001 are an app writing seconds or a placeholder, not a real time. */
    private static final long VALID_FROM_MS = 1_000_000_000_000L;
    /** The covers app's provider: call "sync" fetches what is missing, /<sha1 of the poster link> opens one. */
    private static final String COVERS = "content://mm.tv.covers";
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
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
    };

    /** One show in the row: the app that wrote it, its text, the link that resumes it (may be null), its cover. */
    static final class Item {
        final String pkg, text, link, poster;
        final long when;
        Bitmap cover;

        Item(String pkg, String text, String link, String poster, long when) {
            this.pkg = pkg;
            this.text = text;
            this.link = link;
            this.poster = poster;
            this.when = when;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Item)) return false;
            Item i = (Item) o;
            return pkg.equals(i.pkg) && text.equals(i.text) && Objects.equals(link, i.link)
                    && (cover == null) == (i.cover == null);
        }

        @Override public int hashCode() {
            return Objects.hash(pkg, text, link);
        }
    }

    private final Context context;
    private final long maxAgeMs;
    private final boolean covers;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Map<String, String> lines = Collections.emptyMap();
    private List<Item> items = Collections.emptyList();

    ContinueWatching(Context context, int days, boolean covers) {
        this.context = context.getApplicationContext();
        maxAgeMs = days * DAY_MS;
        this.covers = covers;
    }

    /** The line for this app, or null when it has nothing recent to resume. */
    String lineFor(String pkg) {
        return pkg == null ? null : lines.get(pkg);
    }

    /** The newest unfinished shows from every app, newest first, at most MAX_ITEMS. */
    List<Item> items() {
        return items;
    }

    /** Reads the rows again; onChange runs on the main thread when any line or show changed. */
    void refresh(Runnable onChange) {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return;
        IO.execute(() -> {
            List<Item> nextItems = new ArrayList<>();
            Map<String, String> next = read(nextItems);
            if (covers && !nextItems.isEmpty()) loadCovers(nextItems);
            handler.post(() -> {
                if (next.equals(lines) && nextItems.equals(items)) return;
                lines = next;
                items = nextItems;
                onChange.run();
            });
        });
    }

    private Map<String, String> read(List<Item> shows) {
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
                String text = describe(title.trim(), c.getString(2), c.getString(3));
                int left = minutesLeft(c.getLong(6), c.getLong(7));
                if (left > 0) text += " · " + context.getString(R.string.continue_minutes_left, left);
                shows.add(new Item(pkg, type == TYPE_NEXT ? context.getString(R.string.continue_up_next, text) : text,
                        c.getString(8), c.getString(9), when));
                Long seen = newest.get(pkg);
                if (seen != null && seen >= when) continue;
                newest.put(pkg, when);
                result.put(pkg, context.getString(
                        type == TYPE_NEXT ? R.string.continue_up_next : R.string.continue_watching, text));
            }
        } catch (RuntimeException e) {
            // A provider that refuses or is missing: no lines, as without the permission.
            shows.clear();
            return Collections.emptyMap();
        }
        Collections.sort(shows, (a, b) -> Long.compare(b.when, a.when));
        if (shows.size() > MAX_ITEMS) shows.subList(MAX_ITEMS, shows.size()).clear();
        return result;
    }

    /** Asks the covers app to fetch what is missing, then decodes each show's cover; a show without one keeps null. */
    private void loadCovers(List<Item> shows) {
        Uri covers = Uri.parse(COVERS);
        try {
            context.getContentResolver().call(covers, "sync", null, null);
        } catch (RuntimeException e) {
            return;   // no covers app, or it refused: icons only
        }
        for (Item item : shows) {
            if (item.poster == null) continue;
            try (InputStream in = context.getContentResolver().openInputStream(
                    Uri.withAppendedPath(covers, sha1(item.poster)))) {
                item.cover = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                // not fetched (yet): this show keeps its icon
            }
        }
    }

    /** The covers app names each file after the SHA-1 of its poster link. */
    static String sha1(String s) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8))) {
            hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        }
        return hex.toString();
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
