# Picking up BentoBar

The dev VM can restart mid-task. This file is the status and the next steps; keep it current and
commit at every milestone.

## Where things live
- Repo `~/bentobar`, GitHub github.com/kuscher/bentobar (public; was kuscher/discobar, which redirects).
  Releases carry `BentoBar.apk` (stable name for releases/latest/download/BentoBar.apk) + SHA256SUMS.
- Releasing (since 2026-10-01, `docs/RELEASING.md`): push a tag `v<version>` and GitHub Actions builds,
  signs and publishes the GitHub release and puts the bundle on Google Play's closed-testing track as a
  draft ("Send for review" stays a button in the Play Console). No key needed on this machine. Release
  texts are in `docs/release-notes/<version>.md`. The dry run ("Run workflow") passed on 2026-10-01; the
  first tag released that way is `v0.6` (version code 7, 2026-10-01): the next release needs a new
  version. 0.6 is the first build signed with the new key on GitHub, so a 0.5 installed from GitHub has to
  be uninstalled once (Look › Copy settings first; docs/release-notes/0.6.md says how).
- Release key: in the secrets of the GitHub environment `release` (the Play key in `play`). New key since
  2026-09-30 (also Google Play's), backed up with its password to Drive folder "Googlebook app signing keys (new keys, 2026-09-30)", file bentobar-keystore.jks.
  A machine with `~/.config/bentobar/keystore.jks` + `keystore.pass` (not in git) can still sign a local build.
- Research: `docs/research/device-findings.md` (probe results on the HP Googlebook 14) and
  `docs/research/android-docs.md` (official docs with URLs).

## Unreleased (2026-10-01): Google Play offers BentoBar to PC-type devices only
- The manifest requires `android.hardware.type.pc` (Googlebooks report it). The user asked for Play to
  target Googlebooks, or at least desktop Android devices; Play had 5,744 supported device models for
  0.5, mostly phones. With Summa and StudioSnap (minSdk 34 like BentoBar) this left only the five
  Googlebooks. Android doesn't enforce the feature at install time.
- **Production on Play waits for the next release.** The user wants BentoBar in production, but 0.5 on
  Play has the unbind crash and no device filter, so it was not promoted. The next version (everything
  under "Unreleased") should go to closed testing and production together. Before that: try the crash
  fix on the HP, bump the version, write `docs/release-notes/<version>.md` and the Play text, and
  update the Play forms for the new optional Usage access (`PACKAGE_USAGE_STATS`): data safety, the
  privacy page (googlebook.studio/privacy/bentobar), and the Accessibility video if Setup looks
  different now.

## Unreleased (2026-10-01): crash on unbind fixed
- 0.5 crashed on the HP (twice, 2026-10-01) when the accessibility service was unbound, e.g. by
  `uiautomator dump` (UiAutomation suspends accessibility services): "BentoBar keeps stopping", and
  the service stayed in "Binding" until its entry was taken out of `enabled_accessibility_services`
  and put back.
- Cause: `Overlay.destroy()` moved a strip that had never been shown (lifecycle still INITIALIZED)
  straight to DESTROYED, which LifecycleRegistry refuses. Now `OverlayLifecycle` (in `bar/Overlay.kt`,
  tested by `OverlayLifecycleTest`) makes show, hide and destroy safe in every state, and
  `BarService` stops a controller exactly once (unbind, destroy, or a second connect).
- The fix is on `main`. **Not released** (still 0.5, version code 6) and **not tested on a device**:
  only the unit tests and a debug build on the Mac. Worth checking on the HP before the next release:
  `uiautomator dump` twice in a row, and switching the service off with "Show in status bar" off.

## Unreleased (2026-09-30): fixes and polish from a second device (Acer Googlebook 14)
Found testing 0.5 on an Acer Googlebook 14, whose status bar is opaque black (the HP's is
transparent), with a release installed from GitHub (Play Protect, restricted settings). Nine PRs,
merged in order; CHANGELOG "Unreleased" has the user-facing list. What changed, by area:
- **Strip:** color sampled apart from the bar's own background, every color held to 4.5:1
  (`bar/Contrast.kt`); Google Sans Flex (`google-sans-flex`, the Acer's name for it); live numbers
  in fixed-width slots (`Fmt.widthTemplate`); items that don't fit go to the ‹ menu; items fade in;
  drag items sideways in the bar itself (neighbors slide aside, the order is saved on release);
  the screenshot preview no longer hides the strip; Next meeting is pinned to the far left and new
  items are added at the left.
- **Settings:** Setup, the header and banners follow observed state (`ui/SetupState.kt`,
  `bar/BarStatus.kt`); switches for what BentoBar uses (notifications, calendar, exact alarms);
  drag to reorder and "Show when…" rules in words on the Bar page; Hidden items is one setting
  (Show everything, the default, or behind ‹ on click or hover; ‹ folds on a click elsewhere and
  the wheel reveals); Presenting mode; Undo for add and delete; toasts for messages without an
  action.
- **Calendar:** two items with clear rules. Next meeting: a call link or other guests, own
  calendars, until 03:00 tonight (`items/Meetings.kt`). Calendar: today's day number in the bar, a
  7-day agenda without meetings. Events synced in by task apps (Todoist) are left out of both.
- **System menus:** richer CPU and memory detail; top apps for data and storage with opt-in Usage
  access.
- **Text:** all in resources, US English by default, British spellings for en-GB/AU/NZ/IE/IN/ZA;
  a copy-edit pass.
- **Defaults** (new installs and Reset): Next meeting (when one is near), Calendar, Timer, Keep awake.
- Tests: JVM unit tests for contrast, width templates, call links, meetings, migrations, hidden
  modes, reorder and thresholds (`./gradlew :app:testDebugUnitTest`).
- Not yet done: the jitter some report when hovering across the bar (adb `debug trace on` logs
  every move with its cause, to find it); Alt+↑/↓ in the Bar page list may be taken by the system.

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
- Drag to reorder in the bar itself (e.g. with a modifier key); settings has it now.
- Profiles (e.g. "Presenting"), triggers beyond "when active".
- External displays: one strip per display's status bar.
- Draw the strip with plain Views to cut CPU further.
- GitHub releases + README screenshots, x86_64 CI like PDF Toolbox's.
