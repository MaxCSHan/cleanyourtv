package com.gathin.tvhome;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * keep_tv_choices: what the owner picks in Launcher settings stays theirs. A theme push no longer clears it; a
 * computer changes one by sending choices-set.json (applied once, then removed; a key set to null goes back to the
 * theme's value), and reads them all from choices.json, written here whenever they change. Both sit in the theme
 * directory, which ADB reaches.
 */
final class TvChoices {
    static final String SET_NAME = "choices-set.json", STATE_NAME = "choices.json";
    private static final String[] INTS = {"columns", Config.ROWS_BELOW_DOCK_KEY, Config.DOCK_OPACITY_KEY};
    private static final String[] BOOLS = {"animations", "advanced_effects", Config.CONTINUE_ROW_KEY,
            Config.NOW_PLAYING_KEY, Config.SETTINGS_BUTTON_KEY};
    private static final String[] STRINGS = {Config.CLOCK_FORMAT_KEY, Config.WALLPAPER_CHOICE_KEY, OpenOnStart.KEY};

    private TvChoices() {}

    /** Applies choices-set.json if there is one and removes it; Config checks the ranges as for any choice. */
    static void apply(SharedPreferences choices, File dir) {
        File f = new File(dir, SET_NAME);
        if (!f.isFile()) return;
        try {
            apply(choices.edit(), new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)))
                    .commit();
        } catch (IOException | JSONException ignored) {
            // A broken file changes nothing.
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    static SharedPreferences.Editor apply(SharedPreferences.Editor e, JSONObject set) throws JSONException {
        for (String k : INTS) if (set.has(k)) { if (set.isNull(k)) e.remove(k); else e.putInt(k, set.getInt(k)); }
        for (String k : BOOLS) if (set.has(k)) { if (set.isNull(k)) e.remove(k); else e.putBoolean(k, set.getBoolean(k)); }
        for (String k : STRINGS) if (set.has(k)) { if (set.isNull(k)) e.remove(k); else e.putString(k, set.getString(k)); }
        return e;
    }

    /** The choices made, as JSON; a choice not made is left out (the theme decides it). */
    static String state(Map<String, ?> all) {
        JSONObject out = new JSONObject();
        try {
            for (String[] keys : new String[][]{INTS, BOOLS, STRINGS}) {
                for (String k : keys) if (all.containsKey(k)) out.put(k, all.get(k));
            }
        } catch (JSONException ignored) {
            // Keys are fixed strings; put only fails on a null key.
        }
        return out.toString();
    }

    /** Writes choices.json when it would change. */
    static void export(SharedPreferences choices, File dir) {
        File f = new File(dir, STATE_NAME);
        String text = state(choices.getAll());
        try {
            if (f.isFile() && text.equals(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))) return;
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Not readable from the computer this time; the TV works the same.
        }
    }
}
