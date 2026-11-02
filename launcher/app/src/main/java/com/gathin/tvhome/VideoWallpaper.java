package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;

/**
 * wallpaper_video (fork): a clip from the theme's videos/ folder loops behind Home, muted. It plays in a SurfaceView,
 * which hands the decoder's frames to the hardware composer: on the TCL G05 (MediaTek) they go to the video plane at
 * the panel's 3840x2160 and through the TV's picture engine, while a TextureView would be drawn into the 1920x1080 UI
 * layer by the GPU (backlog B24, app/video-probe). The surface sits under the wallpaper picture, which stays as the
 * poster until the first frame and is shown again when the surface goes; a shade like the photos' own (darker under
 * the clock and the dock) lies between them. The player exists only while Home is in front: the app opened next
 * gets the decoder, and the clip carries on from the same point on the way back. Any player error leaves the
 * picture on screen.
 */
final class VideoWallpaper implements SurfaceHolder.Callback {
    private static final String TAG = "TvHome.Video";

    interface Shown { void on(boolean shown); }

    private final File clip;
    private final Shown shown;
    private MediaPlayer player;
    private SurfaceHolder holder;
    /** Home is in front; showing: the clip's frames are what the picture fades out over. */
    private boolean wanted, showing;
    private int position;

    VideoWallpaper(Context context, FrameLayout root, File clip, int shadePercent, Shown shown) {
        this.clip = clip;
        this.shown = shown;
        // Under the wallpaper picture (index 0): the shade, then the surface below everything.
        if (shadePercent > 0) {
            ImageView shade = new ImageView(context);
            shade.setScaleType(ImageView.ScaleType.FIT_XY);
            shade.setImageBitmap(shade(shadePercent));
            shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(shade, 0, new FrameLayout.LayoutParams(-1, -1));
        }
        SurfaceView surface = new SurfaceView(context);
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        surface.getHolder().addCallback(this);
        root.addView(surface, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    void start() {
        wanted = true;
        if (holder != null && player == null) open();
    }

    /** Home left the front: the decoder goes now; the last frame stays until the surface does. */
    void stop() {
        wanted = false;
        release();
    }

    void close() {
        stop();
    }

    private void open() {
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            p.setDataSource(clip.getPath());
            p.setDisplay(holder);
            p.setVolume(0f, 0f);   // muted: Home makes no sound
            p.setLooping(true);
            p.setOnPreparedListener(mp -> {
                if (mp != player) return;
                if (position > 0) mp.seekTo(position);
                mp.start();
            });
            p.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && mp == player && !showing) {
                    showing = true;
                    shown.on(true);
                }
                return false;
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "player error " + what + "/" + extra + ": the picture stays");
                if (mp == player) hide();
                return true;
            });
            p.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "cannot play " + clip.getName() + ": " + e.getClass().getSimpleName());
            hide();
        }
    }

    private void release() {
        if (player == null) return;
        try {
            position = player.getCurrentPosition();
        } catch (IllegalStateException ignored) {
            // Not prepared yet: start from the same point as before.
        }
        player.release();
        player = null;
    }

    /** The picture back over the surface, and no player. */
    private void hide() {
        release();
        if (showing) {
            showing = false;
            shown.on(false);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        holder = h;
        if (wanted && player == null) open();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        // The last frame goes with the surface: the picture covers it again, and the next first frame fades in.
        holder = null;
        hide();
    }

    /**
     * The photos' text shade (app/photos/server.py text_shade) as one small bitmap stretched over the screen: the top
     * third darkened under the clock, most on the left, and the lower part under the dock, each fading out smoothly.
     */
    private static Bitmap shade(int percent) {
        int w = 480, h = 270;
        int[] pixels = new int[w * h];
        float peak = percent / 100f * 255f;
        for (int y = 0; y < h; y++) {
            float top = ramp(y, 0.36f * h, 0f), bottom = ramp(y, 0.42f * h, h);
            for (int x = 0; x < w; x++) {
                float a = Math.max(top * (1f - 0.45f * x / w), bottom);
                pixels[y * w + x] = Math.round(peak * a) << 24;
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    }

    /** 0 at clear, 1 at full, smoothstep in between. */
    private static float ramp(float y, float clear, float full) {
        float t = Math.min(1f, Math.max(0f, (y - clear) / (full - clear)));
        return t * t * (3f - 2f * t);
    }
}
