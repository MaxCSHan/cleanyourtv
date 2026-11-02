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
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Launcher settings as the TV's own settings look. A panel on the right over Home, categories with
 * their current values under them, on/off rows with a switch, choices on a page of their own with a tick, Back one
 * level up. Upstream's full-screen Launcher settings is removed in this fork; the choices keep its keys (kept by
 * TvChoices), and the change count moves at once, so Home behind the panel redraws with it.
 */
public final class SettingsPanelActivity extends Activity {
    /** Result extra: Home starts reordering the apps (the Reorder row). */
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    /** One row: what it shows and what OK does. trail: 0 none, else a drawable (chevron, switch, tick). */
    private static final class Row {
        final int icon;
        final String title, summary;
        final int trail;
        final Runnable ok;

        Row(int icon, String title, String summary, int trail, Runnable ok) {
            this.icon = icon;
            this.title = title;
            this.summary = summary;
            this.trail = trail;
            this.ok = ok;
        }
    }

    /** A page: its title and how to build its rows (built again whenever it shows, so values are current). */
    private static final class Page {
        final String title;
        final java.util.function.Supplier<List<Row>> rows;
        int focus;

        Page(String title, java.util.function.Supplier<List<Row>> rows) {
            this.title = title;
            this.rows = rows;
        }
    }

    private SharedPreferences choices;
    private Config config;
    private final Deque<Page> pages = new ArrayDeque<>();
    private LinearLayout rowsView;
    private TextView titleView, crumbView;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings_panel);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        config = Config.load(this);
        rowsView = findViewById(R.id.rows);
        titleView = findViewById(R.id.title);
        crumbView = findViewById(R.id.crumb);
        scroll = findViewById(R.id.scroll);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        findViewById(R.id.panel).setOnClickListener(v -> { });
        open(new Page(getString(R.string.launcher_settings), this::home));
        View panel = findViewById(R.id.panel);
        panel.setTranslationX(60 * getResources().getDisplayMetrics().density);
        panel.setAlpha(0f);
        panel.animate().translationX(0f).alpha(1f).setDuration(220).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && pages.size() > 1) {
            pages.pop();
            show(-1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- pages ---------------------------------------------------------------------------------------

    private List<Row> home() {
        List<Row> rows = new ArrayList<>();
        rows.add(page(R.drawable.ic_home_settings, R.string.panel_home,
                join(getString(R.string.panel_columns_value, config.columns),
                        onOff(R.string.continue_row, config.continueWatchingRow)), this::homePage));
        if (config.dockRow) {
            rows.add(page(R.drawable.ic_dock, R.string.panel_dock,
                    join(getString(R.string.dock_opacity_label) + " " + config.dockOpacity + "%",
                            getString(R.string.row_state, getString(R.string.panel_spotlight),
                                    spotlightName(config.dockUnfocusedOpacity))), this::dockPage));
        }
        rows.add(page(R.drawable.ic_photo_mark, R.string.panel_wallpaper, wallpaperSummary(), this::wallpaperPage));
        if (Settings.System.canWrite(this)) {
            rows.add(page(R.drawable.ic_moon, R.string.panel_screensaver,
                    getString(R.string.screensaver_delay_state, delayName(screensaverMinutes())), this::screensaverPage));
        }
        rows.add(page(R.drawable.ic_sparkle, R.string.panel_effects,
                join(onOff(R.string.panel_animations, config.animations),
                        onOff(R.string.advanced_effects, config.animations && config.advancedEffects)), this::effectsPage));
        rows.add(page(R.drawable.ic_settings, R.string.panel_system, getString(R.string.panel_system_summary),
                this::systemPage));
        return rows;
    }

    private void homePage() {
        open(new Page(getString(R.string.panel_home), () -> {
            List<Row> rows = new ArrayList<>();
            rows.add(choice(0, R.string.columns_label, String.valueOf(config.columns),
                    new int[]{4, 5, 6, 7}, String::valueOf, config.columns,
                    v -> choices.edit().putInt("columns", v).apply()));
            rows.add(choice(0, R.string.rows_below_dock_label, String.valueOf(config.rowsBelowDock),
                    new int[]{0, 1, 2, 3}, String::valueOf, config.rowsBelowDock,
                    v -> choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, v).apply()));
            if (config.continueWatchingDays > 0) {
                rows.add(toggle(R.string.continue_row, config.continueWatchingRow, Config.CONTINUE_ROW_KEY));
            }
            rows.add(toggle(R.string.now_playing, config.nowPlaying, Config.NOW_PLAYING_KEY));
            String[] clocks = {"system", "12", "24"};
            int[] clockNames = {R.string.clock_format_system, R.string.clock_format_12, R.string.clock_format_24};
            int clock = Math.max(0, java.util.Arrays.asList(clocks).indexOf(config.clockFormat));
            rows.add(choice(0, R.string.panel_clock, getString(clockNames[clock]), new int[]{0, 1, 2},
                    i -> getString(clockNames[i]), clock,
                    i -> choices.edit().putString(Config.CLOCK_FORMAT_KEY, clocks[i]).apply()));
            if (config.settingsKeycodes.length > 0) {
                rows.add(new Row(0, getString(R.string.panel_settings_button), getString(
                        R.string.panel_settings_button_summary), toggleIcon(config.showSettingsButton), () -> {
                    boolean on = !config.showSettingsButton;
                    choices.edit().putBoolean(Config.SETTINGS_BUTTON_KEY, on).apply();
                    if (!on) Toast.makeText(this, R.string.settings_button_hidden, Toast.LENGTH_LONG).show();
                    changed();
                }));
            }
            if (config.openOnStartChoice) {
                rows.add(new Row(0, getString(R.string.open_on_start_label), openOnStartName(),
                        R.drawable.ic_chevron, this::openOnStartPage));
            }
            if (config.launcherSettingsButton) {
                rows.add(new Row(R.drawable.ic_swap_vert, getString(R.string.reorder), null, 0, () -> {
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
                    finish();
                }));
            }
            return rows;
        }));
    }

    private void dockPage() {
        open(new Page(getString(R.string.panel_dock), () -> {
            List<Row> rows = new ArrayList<>();
            rows.add(choice(0, R.string.dock_opacity_label, config.dockOpacity + "%", new int[]{100, 70, 50, 30},
                    v -> v + "%", config.dockOpacity, v -> choices.edit().putInt(Config.DOCK_OPACITY_KEY, v).apply()));
            rows.add(choice(0, R.string.dock_spotlight_label, spotlightName(config.dockUnfocusedOpacity),
                    new int[]{100, 70, 55, 40}, this::spotlightName, config.dockUnfocusedOpacity,
                    v -> choices.edit().putInt(Config.SPOTLIGHT_KEY, v).apply()));
            rows.add(choice(0, R.string.dock_margin_label, marginName(config.dockBottomMargin),
                    new int[]{0, 10, 30, 60}, this::marginName, config.dockBottomMargin,
                    v -> choices.edit().putInt(Config.DOCK_MARGIN_KEY, v).apply()));
            return rows;
        }));
    }

    private void wallpaperPage() {
        open(new Page(getString(R.string.panel_wallpaper), () -> {
            List<Row> rows = new ArrayList<>();
            rows.add(new Row(0, getString(R.string.wallpaper_label), wallpaperSummary(), R.drawable.ic_chevron,
                    this::wallpaperPickPage));
            if (config.videoClip != null) {
                rows.add(toggle(R.string.wallpaper_video, config.wallpaperVideo != null, Config.VIDEO_KEY));
            }
            if (config.wallpaperRotationMinutes > 0 && config.wallpapers.size() > 1) {
                rows.add(choice(0, R.string.wallpaper_interval_label, minutesName(config.wallpaperRotationMinutes),
                        new int[]{1, 5, 15, 30, 60}, this::minutesName, config.wallpaperRotationMinutes,
                        v -> choices.edit().putInt(Config.ROTATION_KEY, v).apply()));
                boolean sequential = WallpaperRotation.ORDER_SEQUENTIAL.equals(config.wallpaperOrder);
                rows.add(choice(0, R.string.wallpaper_order_label, orderName(sequential ? 1 : 0), new int[]{0, 1},
                        this::orderName, sequential ? 1 : 0, v -> choices.edit().putString(Config.ORDER_KEY,
                                v == 1 ? WallpaperRotation.ORDER_SEQUENTIAL : WallpaperRotation.ORDER_SHUFFLE).apply()));
                rows.add(choice(0, R.string.wallpaper_fade_label, fadeName(config.wallpaperFadeMs),
                        new int[]{0, 800, 1500, 3000}, this::fadeName, config.wallpaperFadeMs,
                        v -> choices.edit().putInt(Config.FADE_KEY, v).apply()));
            }
            return rows;
        }));
    }

    /** The theme's photos, one of them, or none. A photo is named by its date taken when captions.txt has one. */
    private void wallpaperPickPage() {
        File dir = Config.dir(this);
        List<File> images = new ArrayList<>(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        Map<String, String> dates = images.isEmpty() ? Collections.emptyMap()
                : WallpaperCaptions.read(new File(dir, "wallpapers"));
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        open(new Page(getString(R.string.wallpaper_label), () -> {
            List<Row> rows = new ArrayList<>();
            rows.add(pick(getString(R.string.wallpaper_theme_default), current == null,
                    () -> choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply()));
            rows.add(pick(getString(R.string.wallpaper_none), "none".equals(current),
                    () -> choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply()));
            for (File image : images) {
                String name = image.getName();
                String label = dates.containsKey(name) ? dates.get(name) : name;
                rows.add(pick(label, name.equals(current),
                        () -> choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, name).apply()));
            }
            return rows;
        }));
    }

    private void screensaverPage() {
        open(new Page(getString(R.string.panel_screensaver), () -> {
            List<Row> rows = new ArrayList<>();
            int current = screensaverMinutes();
            for (int m : ScreensaverDelay.MINUTES) {
                rows.add(pick(delayName(m), m == current, () -> {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                                ScreensaverDelay.timeoutMs(m));
                    } catch (SecurityException e) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                }));
            }
            return rows;
        }));
    }

    private void effectsPage() {
        open(new Page(getString(R.string.panel_effects), () -> {
            List<Row> rows = new ArrayList<>();
            boolean animations = config.animations, advanced = config.animations && config.advancedEffects;
            rows.add(new Row(0, getString(R.string.panel_animations), null, toggleIcon(animations), () -> {
                SharedPreferences.Editor e = choices.edit().putBoolean("animations", !animations);
                if (animations) e.putBoolean("advanced_effects", false);
                e.apply();
                changed();
            }));
            if (animations) {
                rows.add(new Row(0, getString(R.string.advanced_effects), getString(R.string.advanced_effects_warning),
                        toggleIcon(advanced), () -> {
                    choices.edit().putBoolean("advanced_effects", !advanced).apply();
                    changed();
                }));
            }
            return rows;
        }));
    }

    private void systemPage() {
        open(new Page(getString(R.string.panel_system), () -> {
            List<Row> rows = new ArrayList<>();
            rows.add(new Row(R.drawable.ic_settings, getString(R.string.tv_settings), null, R.drawable.ic_chevron,
                    () -> start(SystemSettings.intent(this, Settings.ACTION_SETTINGS))));
            rows.add(new Row(R.drawable.ic_home_settings, getString(R.string.home_settings), null,
                    R.drawable.ic_chevron, () -> start(new Intent(this, HomeSettingsActivity.class))));
            boolean any = false;
            for (String key : TvChoices.ALL) any |= choices.contains(key);
            if (any) {
                rows.add(new Row(0, getString(R.string.follow_theme),
                        getString(R.string.follow_theme_hint), 0, () -> new AlertDialog.Builder(this)
                        .setTitle(R.string.follow_theme)
                        .setMessage(R.string.follow_theme_hint)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            SharedPreferences.Editor clear = choices.edit();
                            for (String key : TvChoices.ALL) clear.remove(key);
                            clear.commit();
                            changed();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()));
            }
            return rows;
        }));
    }

    /** Off, then every app with a TV entry point by name. */
    private void openOnStartPage() {
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
        String current = choices.getString(OpenOnStart.KEY, "");
        open(new Page(getString(R.string.open_on_start_label), () -> {
            List<Row> rows = new ArrayList<>();
            rows.add(pick(getString(R.string.off), current.isEmpty(), () -> choices.edit().remove(OpenOnStart.KEY).apply()));
            for (String[] a : apps) {
                rows.add(pick(a[0], a[1].equals(current), () -> {
                    OpenOnStart.skipThisBoot(this);
                    choices.edit().putString(OpenOnStart.KEY, a[1]).apply();
                }));
            }
            return rows;
        }));
    }

    // ---- rows ----------------------------------------------------------------------------------------

    private Row page(int icon, int title, String summary, Runnable open) {
        return new Row(icon, getString(title), summary, R.drawable.ic_chevron, open);
    }

    private Row toggle(int title, boolean on, String key) {
        return new Row(0, getString(title), null, toggleIcon(on), () -> {
            choices.edit().putBoolean(key, !on).apply();
            changed();
        });
    }

    private interface Name { String of(int value); }
    private interface Save { void to(int value); }

    /** A row that opens a page of values, the current one ticked; picking one saves it and comes back. */
    private Row choice(int icon, int title, String summary, int[] values, Name name, int current, Save save) {
        return new Row(icon, getString(title), summary, R.drawable.ic_chevron, () -> open(new Page(getString(title),
                () -> {
                    List<Row> rows = new ArrayList<>();
                    for (int v : values) rows.add(pick(name.of(v), v == current, () -> save.to(v)));
                    return rows;
                })));
    }

    /** One value on a choice page: OK saves it (unless it is already the one) and goes back a page. */
    private Row pick(String label, boolean chosen, Runnable save) {
        return new Row(0, label, null, chosen ? R.drawable.ic_check : 0, () -> {
            if (!chosen) {
                save.run();
                changed();
            }
            pages.pop();
            show(-1);
        });
    }

    private int toggleIcon(boolean on) {
        return on ? R.drawable.ic_toggle_on : R.drawable.ic_toggle_off;
    }

    // ---- showing -------------------------------------------------------------------------------------

    private void open(Page page) {
        if (!pages.isEmpty()) pages.peek().focus = focusedIndex();
        pages.push(page);
        show(1);
    }

    /** Builds the top page again; direction 1 slides it in from the right, -1 from the left, 0 in place. */
    private void show(int direction) {
        Page page = pages.peek();
        int keep = direction == 0 ? focusedIndex() : page.focus;
        rowsView.removeAllViews();
        titleView.setText(page.title);
        crumbView.setText(pages.size() > 1 ? crumb() : "");
        crumbView.setVisibility(pages.size() > 1 ? View.VISIBLE : View.GONE);
        for (Row row : page.rows.get()) rowsView.addView(rowView(row));
        View target = rowsView.getChildAt(Math.max(0, Math.min(keep, rowsView.getChildCount() - 1)));
        if (target != null) target.requestFocus();
        if (direction == 0) return;
        if (direction > 0) scroll.scrollTo(0, 0);
        float d = getResources().getDisplayMetrics().density;
        rowsView.setTranslationX(direction * 24 * d);
        rowsView.setAlpha(0f);
        rowsView.animate().translationX(0f).alpha(1f).setDuration(180).start();
    }

    /** The pages above this one, as a path: "桌面設定 · Dock". */
    private String crumb() {
        List<String> titles = new ArrayList<>();
        for (Page p : pages) titles.add(0, p.title);
        return android.text.TextUtils.join(" · ", titles.subList(0, titles.size() - 1));
    }

    private int focusedIndex() {
        View f = rowsView.getFocusedChild();
        return f == null ? 0 : rowsView.indexOfChild(f);
    }

    private View rowView(Row row) {
        View v = getLayoutInflater().inflate(R.layout.item_settings_row, rowsView, false);
        ImageView icon = v.findViewById(R.id.icon);
        if (row.icon != 0) icon.setImageResource(row.icon);
        else icon.setVisibility(View.GONE);
        ((TextView) v.findViewById(R.id.title)).setText(row.title);
        TextView summary = v.findViewById(R.id.summary);
        if (row.summary == null || row.summary.isEmpty()) summary.setVisibility(View.GONE);
        else summary.setText(row.summary);
        ImageView trail = v.findViewById(R.id.trail);
        if (row.trail == 0) {
            trail.setVisibility(View.GONE);
        } else {
            trail.setImageResource(row.trail);
            // A switch keeps its own colours on a white focused row and a dark one.
            if (row.trail == R.drawable.ic_toggle_on || row.trail == R.drawable.ic_toggle_off) trail.setImageTintList(null);
        }
        v.setOnClickListener(x -> row.ok.run());
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) v.getLayoutParams()).bottomMargin = Math.round(4 * getResources().getDisplayMetrics().density);
        return v;
    }

    /** A choice was saved: Home rebuilds behind the panel (and on its next resume), and this page shows it. */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).commit();
        config = Config.load(this);
        show(0);
    }

    private void start(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    // ---- names ---------------------------------------------------------------------------------------

    private String join(String a, String b) {
        return a + " · " + b;
    }

    private String onOff(int label, boolean on) {
        return getString(R.string.row_state, getString(label), getString(on ? R.string.on : R.string.off));
    }

    private String wallpaperSummary() {
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        String which = current == null ? getString(R.string.wallpaper_theme_default)
                : "none".equals(current) ? getString(R.string.wallpaper_none) : current;
        if (current == null && config.wallpaperRotationMinutes > 0 && config.wallpapers.size() > 1) {
            return join(getString(R.string.panel_photos_count, config.wallpapers.size()),
                    minutesName(config.wallpaperRotationMinutes));
        }
        return which;
    }

    /** How much the other dock apps dim: Off, or 30% and up. */
    private String spotlightName(int alpha) {
        return alpha >= 100 ? getString(R.string.off) : (100 - alpha) + "%";
    }

    private String marginName(int dp) {
        return getString(dp == 0 ? R.string.dock_margin_lowest : dp <= 10 ? R.string.dock_margin_low
                : dp <= 30 ? R.string.dock_margin_standard : R.string.dock_margin_high);
    }

    private String minutesName(int m) {
        return getString(R.string.screensaver_minutes, m);
    }

    private String orderName(int sequential) {
        return getString(sequential == 1 ? R.string.wallpaper_order_sequential : R.string.wallpaper_order_shuffle);
    }

    private String fadeName(int ms) {
        return ms == 0 ? getString(R.string.off) : getString(R.string.seconds_value, ms / 1000f);
    }

    private int screensaverMinutes() {
        return ScreensaverDelay.minutes(Settings.System.getLong(getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, ScreensaverDelay.NEVER_MS));
    }

    private String delayName(int minutes) {
        return minutes == 0 ? getString(R.string.screensaver_never) : getString(R.string.screensaver_minutes, minutes);
    }

    private String openOnStartName() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
