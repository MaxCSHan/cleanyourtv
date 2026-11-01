package com.gathin.photodream;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * The module card on cleanyourtv's Home: the same scene as the screensaver, right away, until Back. Left and
 * Right step back and forth, OK switches the dates on and off, holding OK or pressing Menu opens the settings.
 * Nothing stays on screen: which keys do what shows for a few seconds on the first start, and again on Up or Down.
 * The screen stays on while it shows.
 */
public class SlideshowActivity extends Activity {

    private static final String HINT_SEEN = "hint_seen";
    private static final long HINT_MS = 5000L, FEEDBACK_MS = 1600L;

    private Scene scene;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Built again on every start, so options changed in the settings show at once.
        scene = new Scene(this);
        setContentView(scene.root);
        scene.start();
        SharedPreferences p = Options.preferences(this);
        if (!p.getBoolean(HINT_SEEN, false)) {
            p.edit().putBoolean(HINT_SEEN, true).apply();
            scene.hint(getString(R.string.keys_hint), HINT_MS);
        }
    }

    @Override
    protected void onStop() {
        if (scene != null) scene.stop();
        scene = null;
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openSettings();
            return true;
        }
        if (isOk(keyCode)) {
            // Not every remote has a Menu key that reaches apps, so holding OK opens the settings too.
            event.startTracking();
            return true;
        }
        if (scene != null && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT)) {
            boolean back = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            return scene.step(back ? -1 : 1) || super.onKeyDown(keyCode, event);
        }
        if (scene != null && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
            scene.hint(getString(R.string.keys_hint), HINT_MS);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            openSettings();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            // A hold already opened the settings and comes up cancelled; a short press switches the dates.
            if (!event.isCanceled() && scene != null) toggleDates();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void toggleDates() {
        SharedPreferences p = Options.preferences(this);
        boolean on = !p.getBoolean(Options.DATES, false);
        p.edit().putBoolean(Options.DATES, on).apply();
        if (!scene.showDates(on)) {   // the prints take the dates when built again
            scene.stop();
            scene = new Scene(this);
            setContentView(scene.root);
            scene.start();
        }
        scene.hint(getString(on ? R.string.dates_on : R.string.dates_off), FEEDBACK_MS);
    }

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }
}
