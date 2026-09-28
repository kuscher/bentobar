# Picking up BarBook

The dev VM can restart mid-task. This file is the status and the next steps; keep it current and
commit at every milestone.

## Where things live
- Repo `~/barbook` (local git, not pushed yet: ask the user before creating a GitHub repo).
- Release key `~/.config/barbook/keystore.jks` + `keystore.pass` (not in git; not backed up privately yet; ask the user).
- Research: `docs/research/device-findings.md` (probe results on the HP Googlebook 14) and
  `docs/research/android-docs.md` (official docs with URLs).

## Status: 0.1 works on the device (2026-09-28)
Verified on the HP Googlebook 14 (Android 17, SDK 37.1):
- The strip draws in the status bar's free space next to "US", in the clock's colour (sampled
  from the status bar window), with filled icons like the system's.
- Hides for fullscreen apps (follows the status bar within about 100 ms), screen off and
  keyguard. Stays shown with the desktop QS and notification popups (they open below the bar).
- Menus: network (chart), calendar (month grid), tools (grid of global actions and settings),
  timer (running and idle), the right-click item menu, and the ‹ menu listing hidden items.
- Hidden section: ‹ expands inline; hover reveal (controller logic verified with test hooks; a
  real pointer hover is for the user to check); "show when active" pops out a running timer.
- Scroll over the Sound item changes media volume on Android 17 (1 → 0 → 1 verified).
- Live Update chip: the timer chip appears when the bar is off (FALLBACK) and moves back into
  the strip when it's on.
- Settings app: preview, sections with reorder, item detail with options, Add catalog, Look,
  Setup (restricted-settings steps, Advanced Protection notice), About.
- Release APK 2.4 MB (R8). About 0.8% of one core with network speed updating; 26 MB PSS.

## Not yet checked by hand (for the user)
- Real mouse: hover reveal, right-click menus, wheel over items, Escape closing a menu.
- The download → install → restricted-settings flow from a GitHub APK (adb installs aren't guarded).
- Keep awake over a long idle period; timer alarm while the device sleeps.
- Calendar items with real events (calendar permission not granted during testing).
- Light wallpaper (dark status bar icons) → sampled dark text.

## Ideas for next versions
- Now playing (needs notification-listener access, another restricted setting).
- Weather (needs internet: would break the "no internet permission" promise; maybe a separate flavour).
- Drag to reorder in the bar itself (e.g. with a modifier key) and in settings.
- Profiles (e.g. "Presenting"), triggers beyond "when active".
- External displays: one strip per display's status bar.
- Draw the strip with plain Views to cut CPU further.
- GitHub releases + README screenshots, x86_64 CI like PDF Toolbox's.
