# Picking up BentoBar

The dev VM can restart mid-task. This file is the status and the next steps; keep it current and
commit at every milestone.

## Where things live
- Repo `~/bentobar`, GitHub github.com/kuscher/bentobar (public; was kuscher/discobar, which redirects).
  Releases carry `BentoBar.apk` (stable name for releases/latest/download/BentoBar.apk) + SHA256SUMS.
- Release key `~/.config/bentobar/keystore.jks` + `keystore.pass` (not in git). New key since 2026-09-30
  (also Google Play's), backed up with its password to a private folder, file bentobar-keystore.jks.
- Research: `docs/research/device-findings.md` (probe results on the HP Googlebook 14) and
  `docs/research/android-docs.md` (official docs with URLs).

## 0.5 (2026-09-29): renamed BentoBar, new icon
- The user asked: rename DiscoBar to BentoBar, a better and more modern icon, and the GitHub repo
  and the app updated to the new brand.
- New package `io.github.kuscher.bentobar` (the Kotlin namespace too), so it installs beside
  DiscoBar; migration is Copy settings / Paste settings (Look page). Same key (alias `discobar`),
  key dir moved to ~/.config/bentobar, helper `./disco` → `./bento`, cache ~/.cache/bentobar.
- Icon (tools/logo.py): bento box from above (status bar compartment with ‹ and dots as holes,
  plus three compartments), rice/salmon/tamago/edamame on ink blue; no font dependency.
- On the HP: the user's DiscoBar layout (saved in ~/.cache/bentobar/user-layout-from-discobar.json)
  was imported into BentoBar, calendar and notification grants carried over, DiscoBar removed.
- The ASUS still has DiscoBar 0.4.1 (service never enabled) until it's connected again.

## 0.4 (2026-09-28): new icon, README with screenshots, first GitHub release
- Icon (replaced in 0.5): status bar pill over the Tools glyph on blue (tools/logo.py). README images from a demo
  layout (tools/readme_images.py); the user's layout was saved and restored byte-identical.
- History was rewritten before the first push to the noreply identity with co-author trailers.

## 0.3 (2026-09-28): CPU item, width and collapse fixes, 3× lighter
- User reported: items cut off; ‹ overlapping the timer when expanded; random collapsing; asked
  for a CPU load item; asked whether memory/CPU readings reflect other apps (yes: device-wide;
  the Linux VM alone holds ~12 GB of the 31 GB; BentoBar ~26 MB).
- Causes and fixes are in CHANGELOG 0.3 and CLAUDE.md ("Window width", "CPU load", "CPU cost").
- The user's own settings on the HP: position CENTER, chevron OFF, spacing 9, CPU item shown.

## 0.2 (2026-09-28): renamed DiscoBar, tighter bar
- The user asked: call it DiscoBar (was BarBook); fix spacing (the finished timer's "Done" sat far from its icon,
  other things too big); hover reveal off by default; the release key backed up. All done.
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
