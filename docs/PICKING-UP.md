# Picking up DiscoBar

The dev VM can restart mid-task. This file is the status and the next steps; keep it current and
commit at every milestone.

## Where things live
- Repo `~/discobar` (local git, not pushed yet: ask the user before creating a GitHub repo).
- Release key `~/.config/discobar/keystore.jks` + `keystore.pass` (not in git). Keystore backed
  up to Drive folder "DiscoBar release key" (id 1DnbGwls_3wZeVpJaNWeOT9jHdoozhSM7, checksum
  verified); the password is for the user's password manager.
- Research: `docs/research/device-findings.md` (probe results on the HP Googlebook 14) and
  `docs/research/android-docs.md` (official docs with URLs).

## 0.3 (2026-09-28): CPU item, width and collapse fixes, 3× lighter
- User reported: items cut off; ‹ overlapping the timer when expanded; random collapsing; asked
  for a CPU load item; asked whether memory/CPU readings reflect other apps (yes: device-wide;
  the Linux VM alone holds ~12 GB of the 31 GB; DiscoBar ~26 MB).
- Causes and fixes are in CHANGELOG 0.3 and CLAUDE.md ("Window width", "CPU load", "CPU cost").
- The user's own settings on the HP: position CENTER, chevron OFF, spacing 9, CPU item shown.

## 0.2 (2026-09-28): renamed DiscoBar, tighter bar
- The user asked: call it DiscoBar; fix spacing (the finished timer's "Done" sat far from its icon,
  other things too big); hover reveal off by default; the release key in Drive. All done.
- Cause of the gaps: Compose `remember` in the strip was positional, so an item popping in
  inherited a neighbour's held width. Now keyed by item id; width holding only for ticking numbers
  (`ItemState.widthKey`), 5 s. Icons 15 sp (measured against the system's glyphs), spacing 12 dp,
  menus more compact.
- DiscoBar 0.2 release is installed and enabled on the HP (fresh install: notifications not granted).

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
