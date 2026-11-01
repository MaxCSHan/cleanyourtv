package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private TextView continueChoice;
    private TextView settingsButtonChoice;
    private TextView nowPlayingChoice;
    private TextView screensaverChoice;
    private boolean nowPlaying;
    private boolean continueRow;
    private boolean settingsButton;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        continueRow = config.continueWatchingRow;
        continueChoice = findViewById(R.id.continueChoice);
        if (config.continueWatchingDays > 0) {
            continueChoice.setVisibility(View.VISIBLE);
            continueChoice.setOnClickListener(v -> {
                continueRow = !continueRow;
                choices.edit().putBoolean(Config.CONTINUE_ROW_KEY, continueRow).apply();
                changed();
                refresh();
            });
        }
        nowPlaying = config.nowPlaying;
        nowPlayingChoice = findViewById(R.id.nowPlayingChoice);
        nowPlayingChoice.setVisibility(View.VISIBLE);
        nowPlayingChoice.setOnClickListener(v -> {
            nowPlaying = !nowPlaying;
            choices.edit().putBoolean(Config.NOW_PLAYING_KEY, nowPlaying).apply();
            changed();
            refresh();
        });
        screensaverChoice = findViewById(R.id.screensaverChoice);
        if (Settings.System.canWrite(this)) {
            screensaverChoice.setVisibility(View.VISIBLE);
            screensaverChoice.setOnClickListener(v -> showScreensaverPicker());
        }
        boolean rotating = config.wallpaperRotationMinutes > 0 && config.wallpapers.size() > 1;
        int[] minutes = {1, 5, 15, 30, 60};
        pickerRow(R.id.intervalChoice, R.string.wallpaper_interval_label, rotating, minutes,
                m -> getString(R.string.screensaver_minutes, m), config.wallpaperRotationMinutes,
                m -> choices.edit().putInt(Config.ROTATION_KEY, m).apply());
        boolean sequential = WallpaperRotation.ORDER_SEQUENTIAL.equals(config.wallpaperOrder);
        pickerRow(R.id.orderChoice, R.string.wallpaper_order_label, rotating, new int[]{0, 1},
                i -> getString(i == 1 ? R.string.wallpaper_order_sequential : R.string.wallpaper_order_shuffle),
                sequential ? 1 : 0, i -> choices.edit().putString(Config.ORDER_KEY,
                        i == 1 ? WallpaperRotation.ORDER_SEQUENTIAL : WallpaperRotation.ORDER_SHUFFLE).apply());
        pickerRow(R.id.fadeChoice, R.string.wallpaper_fade_label, rotating, new int[]{0, 800, 1500, 3000},
                ms -> ms == 0 ? getString(R.string.off) : getString(R.string.seconds_value, ms / 1000f),
                config.wallpaperFadeMs, ms -> choices.edit().putInt(Config.FADE_KEY, ms).apply());
        pickerRow(R.id.spotlightChoice, R.string.dock_spotlight_label, config.dockRow, new int[]{100, 70, 55, 40},
                a -> a == 100 ? getString(R.string.off) : (100 - a) + "%", config.dockUnfocusedOpacity,
                a -> choices.edit().putInt(Config.SPOTLIGHT_KEY, a).apply());
        pickerRow(R.id.dockMarginChoice, R.string.dock_margin_label, config.dockRow, new int[]{0, 10, 30, 60},
                dp -> getString(dp == 0 ? R.string.dock_margin_lowest : dp == 10 ? R.string.dock_margin_low
                        : dp == 30 ? R.string.dock_margin_standard : R.string.dock_margin_high),
                config.dockBottomMargin, dp -> choices.edit().putInt(Config.DOCK_MARGIN_KEY, dp).apply());
        settingsButton = config.showSettingsButton;
        settingsButtonChoice = findViewById(R.id.settingsButtonChoice);
        if (config.settingsKeycodes.length > 0) {
            settingsButtonChoice.setVisibility(View.VISIBLE);
            settingsButtonChoice.setOnClickListener(v -> {
                settingsButton = !settingsButton;
                choices.edit().putBoolean(Config.SETTINGS_BUTTON_KEY, settingsButton).apply();
                if (!settingsButton) Toast.makeText(this, R.string.settings_button_hidden, Toast.LENGTH_LONG).show();
                changed();
                refresh();
            });
        }
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            SharedPreferences.Editor clear = choices.edit();
            for (String key : TvChoices.ALL) clear.remove(key);
            clear.commit();
            Config current = Config.load(this);
            continueRow = current.continueWatchingRow;
            settingsButton = current.showSettingsButton;
            nowPlaying = current.nowPlaying;
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            recreate();   // every row again with the theme's values
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface Label { String of(int value); }
    private interface Pick { void save(int value); }

    /**
     * A row that picks one of fixed values: "Label: value", a list to choose from on OK, saved at once (Home
     * rebuilds). A theme value that is not one of them still shows, with nothing ticked.
     */
    private void pickerRow(int id, int label, boolean shown, int[] values, Label name, int current, Pick pick) {
        TextView row = findViewById(id);
        if (!shown) return;
        row.setVisibility(View.VISIBLE);
        int[] now = {current};
        row.setText(getString(R.string.row_state, getString(label), name.of(now[0])));
        row.setOnClickListener(v -> {
            String[] names = new String[values.length];
            int checked = -1;
            for (int i = 0; i < values.length; i++) {
                names[i] = name.of(values[i]);
                if (values[i] == now[0]) checked = i;
            }
            new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setSingleChoiceItems(names, checked, (dialog, which) -> {
                        dialog.dismiss();
                        now[0] = values[which];
                        pick.save(values[which]);
                        row.setText(getString(R.string.row_state, getString(label), name.of(now[0])));
                        changed();
                        refresh();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    /** The TV's own screensaver delay: written to the system, so the TV's settings and the Mac see the same. */
    private void showScreensaverPicker() {
        int current = screensaverMinutes();
        String[] labels = new String[ScreensaverDelay.MINUTES.length];
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = delayLabel(ScreensaverDelay.MINUTES[i]);
            if (ScreensaverDelay.MINUTES[i] == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_delay_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                                ScreensaverDelay.timeoutMs(ScreensaverDelay.MINUTES[which]));
                    } catch (SecurityException e) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int screensaverMinutes() {
        return ScreensaverDelay.minutes(Settings.System.getLong(getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, ScreensaverDelay.NEVER_MS));
    }

    private String delayLabel(int minutes) {
        return minutes == 0 ? getString(R.string.screensaver_never) : getString(R.string.screensaver_minutes, minutes);
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        continueChoice.setText(getString(R.string.continue_row_state, getString(continueRow ? R.string.on : R.string.off)));
        nowPlayingChoice.setText(getString(R.string.now_playing_state, getString(nowPlaying ? R.string.on : R.string.off)));
        screensaverChoice.setText(getString(R.string.screensaver_delay_state, delayLabel(screensaverMinutes())));
        settingsButtonChoice.setText(getString(R.string.settings_button_state,
                getString(settingsButton ? R.string.on : R.string.off)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        for (String key : TvChoices.ALL) if (choices.contains(key)) return true;
        return false;
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
