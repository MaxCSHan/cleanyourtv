# smart-tv-mm: a TCL Smart TV Pro fork

Branch `smart-tv-mm` of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv): upstream's launcher with a
few small, off-by-default switches for one TCL Smart TV Pro (`G05_4K_GB`, MediaTek mt5889, Android 11). Every
change is a single commit on top of upstream `main`, written so it could be offered upstream as is. The branch is
rebased on upstream regularly.

| Switch | What it does |
|---|---|
| `wallpaper_fade_ms` | 0 to 3000: crossfade between rotating wallpapers instead of a cut (default 0, off with reduced motion) |
| `continue_watching_days` | 0 to 90: under the focused dock app's name, the show it left unfinished ("Continue: Show · S2 E18 · 47 min left"), from the TV's Watch Next rows no older than this; text only (default 0, off) |
| `continue_watching_row` | with `continue_watching_days` on: the newest unfinished shows from every app as a row of pills above the shelf; Up from the dock reaches it, OK resumes the show through the link its app wrote; the line under the name is left out while it is on (default false) |
| `continue_watching_covers` | with `continue_watching_row` on: each show as a card with its cover, fetched by a separate covers app (`mm.tv.covers`) so the launcher stays offline; the app's icon without it (default false) |
| `dock_title_size` | 24 to 72: the dock app's name above the dock, in sp (default 48) |
| `dock_only_home` | while the dock is focused, the next grid row no longer peeks in under it; Down still reaches the grid (default false) |
| `dock_name_below` | the focused dock app's name under its tile, as in the grid; with `dock_title` false it replaces the large name above the dock (default false) |
| `dock_unfocused_opacity` | 30 to 100: the dock's other tiles at this opacity so the focused one stands out; the grid is not dimmed (default 100, off) |
| `dock_bottom_margin` | 0 to 120: room under the dock row, in dp; less sits the dock lower over the wallpaper, the tiles keep their size (default 30, as upstream) |
| `continue_row_gap` | 0 to 60: the gap under the continue-watching row, in dp; without `dock_title` it is measured from the dock instead of the hidden name's room (default 20) |
| `show_settings_button` | false hides the gear at the top right; Settings is then the remote's Settings button (`settings_key_keycodes`, with `settings_key_panel` a panel that includes Launcher settings); without one the gear always shows (default true) |
| `now_playing` | what plays while Home shows (Spotify and the like) as the continue-watching row's first pill, alone there (titled Now playing) when `continue_watching_row` is off, with the song, the artist and the cover the app hands over; OK opens its player. Read from the TV's media sessions, offline; needs notification-listener access for `NowPlayingListener`, granted over ADB (default false). A new song waits up to a second for its own cover, then the pill crossfades in place |
| `now_playing_keys` | with `now_playing`: P+/P- (`CHANNEL_UP`/`CHANNEL_DOWN`) on Home skip to the next or previous song while one shows, as inside Spotify's own app; otherwise the keys pass on (default false) |
| `home_dates` | today's date under the clock clearer (16sp, brighter), and a wallpaper's caption from `captions.txt` (a date taken) on the same line at the right, same font, softer, after a photo mark (default false) |
| `keep_tv_choices` | what the owner picks in Launcher settings stays theirs: a theme push no longer clears it. The choices are written to `choices.json` in the theme directory, and a computer changes one with `choices-set.json` (applied once, then removed; null goes back to the theme) (default false) |

Launcher settings is a panel on the right over Home, like the TV's own settings (`SettingsPanelActivity`): categories
with their values, switches, choices on their own page with a tick, Back one level up, Home redrawn behind it as a
value changes. Upstream's full-screen Launcher settings (`LauncherSettingsActivity`, its layout and its README test)
is removed in this fork; its keys stay. In it (hold OK on the gear, or 桌面設定 in the remote's Settings panel), three rows switch
`continue_watching_row`, `now_playing` and `show_settings_button` on the TV. A theme push keeps them, and with `keep_tv_choices`
upstream's choices there too: they are the owner's. With `WRITE_SETTINGS` granted as an app-op over ADB (`appops set com.gathin.tvhome WRITE_SETTINGS allow`), a row
there sets when the TV's screensaver starts (`screen_off_timeout`: never, 5 to 60 minutes). Rows also set the photo
interval, order and crossfade (with a rotating collection) and the dock's spotlight and position, as on-TV choices
over `wallpaper_rotation_minutes`, `wallpaper_order`, `wallpaper_fade_ms`, `dock_unfocused_opacity` and `dock_bottom_margin`. Up from the continue-watching row reaches the top bar, and Down from the top bar the row.

The Photos add-on (`addons/photo-screensaver`, `com.gathin.photodream`) also differs, in its card's slideshow: Left
and Right step back and forth, OK switches the date taken on and off (the full date when the name carries the day),
holding OK opens its settings, the date in the launcher's Outfit font after the same photo mark, which add seconds per picture so the TV's owner sets it without a computer; the keys
show for a few seconds on the first start and on Up or Down, nothing stays on screen. Traditional Chinese strings.

Nothing else in the launcher differs: no network access and the same `minSdk`. The APK may pass upstream's 576 KB gate, up to 1 MB.
One permission is added: `READ_TV_LISTINGS`, used only by the two continue-watching switches and granted over ADB
(`adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS`). Because it is a new permission, those
switches stay in this fork rather than going upstream. `now_playing` adds a notification-listener service that does nothing itself; its access is granted over ADB (`cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener`).
