# smart-tv-mm: a TCL Smart TV Pro fork

Branch `smart-tv-mm` of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv): upstream's launcher with a
few small, off-by-default switches for one TCL Smart TV Pro (`G05_4K_GB`, MediaTek mt5889, Android 11). Every
change is a single commit on top of upstream `main`, written so it could be offered upstream as is. The branch is
rebased on upstream regularly.

| Switch | What it does |
|---|---|
| `wallpaper_fade_ms` | 0 to 3000: crossfade between rotating wallpapers instead of a cut (default 0, off with reduced motion) |
| `continue_watching_days` | 0 to 90: under the focused dock app's name, the show it left unfinished ("Continue: Show · S2 E18 · 47 min left"), from the TV's Watch Next rows no older than this; text only (default 0, off) |

Nothing else differs: no network access, the same `minSdk` and size gate. One permission is added:
`READ_TV_LISTINGS`, used only by `continue_watching_days` and granted over ADB
(`adb shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS`). Because it is a new permission, that
switch stays in this fork rather than going upstream.
