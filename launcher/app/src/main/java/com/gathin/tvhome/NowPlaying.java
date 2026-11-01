package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * now_playing: what plays while Home is on screen (music from Spotify and the like), with its cover, read from the
 * TV's media sessions. Reading another app's session needs notification-listener access for NowPlayingListener,
 * granted over ADB (`cmd notification allow_listener`); without it nothing shows. The cover is the bitmap the app
 * hands over, never fetched. Watched only between start (onResume) and stop (onPause).
 */
final class NowPlaying {
    /** The playing track: its app, title, artist (may be empty), cover (may be null) and the player to open. */
    static final class Track {
        final String pkg, title, artist;
        final Bitmap art;
        final PendingIntent player;

        Track(String pkg, String title, String artist, Bitmap art, PendingIntent player) {
            this.pkg = pkg;
            this.title = title;
            this.artist = artist;
            this.art = art;
            this.player = player;
        }

        boolean sameSong(Track t) {
            return t != null && pkg.equals(t.pkg) && title.equals(t.title) && artist.equals(t.artist);
        }

        // The cover by its pixels: Spotify sends a new song's title with the old cover, then the new cover alone.
        // Covers are icon-sized by then, so the comparison is cheap.
        boolean sameCover(Track t) {
            return t != null && (art == null ? t.art == null : t.art != null && art.sameAs(t.art));
        }

        boolean sameAs(Track t) {
            return sameSong(t) && sameCover(t);
        }
    }

    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final Runnable onChange;
    private final int artPx;
    /** How long a new song that still carries the last song's cover waits for its own. */
    private static final long COVER_WAIT_MS = 1000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> watched = new ArrayList<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::watch;
    private final MediaController.Callback changed = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { update(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { update(); }
        @Override public void onSessionDestroyed() { update(); }
    };
    private Track track;
    /** A new song held back until its cover arrives (or COVER_WAIT_MS passes), so title and cover change together. */
    private Track pending;
    private final Runnable publishPending = () -> publish(pending);
    private boolean started;

    NowPlaying(Context context, int artPx, Runnable onChange) {
        sessions = context.getSystemService(MediaSessionManager.class);
        listener = new ComponentName(context, NowPlayingListener.class);
        this.artPx = artPx;
        this.onChange = onChange;
    }

    /** The track playing now, or null. */
    Track track() {
        return track;
    }

    void start() {
        if (started || sessions == null) return;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
            started = true;
            watch(sessions.getActiveSessions(listener));
        } catch (SecurityException notGranted) {
            // No listener access: nothing shows.
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        for (MediaController c : watched) c.unregisterCallback(changed);
        watched.clear();
        handler.removeCallbacks(publishPending);
        pending = null;
    }

    private void watch(List<MediaController> controllers) {
        for (MediaController c : watched) c.unregisterCallback(changed);
        watched.clear();
        if (controllers != null) {
            for (MediaController c : controllers) {
                c.registerCallback(changed, handler);
                watched.add(c);
            }
        }
        update();
    }

    private void update() {
        Track next = null;
        for (MediaController c : watched) {
            PlaybackState state = c.getPlaybackState();
            MediaMetadata meta = c.getMetadata();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING || meta == null) continue;
            String title = text(meta, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title.isEmpty()) continue;
            String artist = text(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            next = new Track(c.getPackageName(), title, artist, art(meta), c.getSessionActivity());
            break;
        }
        if (next == null ? track == null : next.sameAs(track)) {
            handler.removeCallbacks(publishPending);
            pending = null;
            return;
        }
        if (next != null && next.art != null && !next.sameSong(track) && next.sameCover(track)) {
            // The new song with the old cover: wait for its own, from the first such update on.
            if (!next.sameSong(pending)) {
                handler.removeCallbacks(publishPending);
                handler.postDelayed(publishPending, COVER_WAIT_MS);
            }
            pending = next;
            return;
        }
        publish(next);
    }

    private void publish(Track next) {
        handler.removeCallbacks(publishPending);
        pending = null;
        track = next;
        onChange.run();
    }

    private static String text(MediaMetadata meta, String key, String fallback) {
        String s = meta.getString(key);
        if (s == null || s.trim().isEmpty()) s = meta.getString(fallback);
        return Objects.toString(s, "").trim();
    }

    /** The app's own cover bitmap, made small: the row shows it at icon size. */
    private Bitmap art(MediaMetadata meta) {
        Bitmap b = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) return null;
        return b.getWidth() > artPx ? Bitmap.createScaledBitmap(b, artPx, artPx * b.getHeight() / b.getWidth(), true) : b;
    }
}
