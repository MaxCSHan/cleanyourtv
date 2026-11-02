package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** wallpaper_video's clips (fork): MP4 only, the names theme-sync sends. */
    static final String VIDEOS = "videos";
    private static final Pattern VIDEO = Pattern.compile("[A-Za-z0-9_.-]+\\.mp4", Pattern.CASE_INSENSITIVE);
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    || TvChoices.SET_NAME.equals(name) ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        if (segments.size() == 2 && VIDEOS.equals(segments.get(0)) && video(segments.get(1))) {
            return VIDEOS + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    static boolean video(String name) {
        return name != null && !name.startsWith(".") && VIDEO.matcher(name).matches();
    }

    /** The first clip in the folder by name, or null; an empty file (a send cut short) is skipped. */
    static java.io.File firstVideo(java.io.File folder) {
        String[] names = folder == null ? null : folder.list();
        if (names == null) return null;
        java.util.Arrays.sort(names);
        for (String name : names) {
            java.io.File f = new java.io.File(folder, name);
            if (video(name) && f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
