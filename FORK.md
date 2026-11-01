# smart-tv-mm: a TCL Smart TV Pro fork

Branch `smart-tv-mm` of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv): upstream's launcher with a
few small, off-by-default switches for one TCL Smart TV Pro (`G05_4K_GB`, MediaTek mt5889, Android 11). Every
change is a single commit on top of upstream `main`, written so it could be offered upstream as is. The branch is
rebased on upstream regularly.

| Switch | What it does |
|---|---|
| `wallpaper_fade_ms` | 0 to 3000: crossfade between rotating wallpapers instead of a cut (default 0, off with reduced motion) |

Nothing else differs: no network access, no new permissions, the same `minSdk` and size gate.
