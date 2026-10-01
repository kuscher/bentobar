# BentoBar: notes for working on the code

(Called DiscoBar until 0.4.1, package `io.github.kuscher.discobar`; renamed in 0.5 with a new
package, so it installs next to DiscoBar rather than over it. Same signing key.)

A Bartender-style status bar organiser for Googlebooks (Googlebook OS = Android 17 desktop). A
plain APK: no adb grants, root or system changes in the product (the user's hard requirement).

## Layout
- `app/src/main/java/io/github/kuscher/bentobar/`
  - `bar/BarService.kt`: the AccessibilityService plus `BarController`. It scans the status bar,
    places the strip, hides it (fullscreen, keyguard, screen off, a shade covering the bar), runs
    menus, keep awake (a flag on the strip window) and colour sampling, and has adb `debug()` hooks.
  - `bar/StatusBarScan.kt`: finds the status bar window (TYPE_SYSTEM at y=0) and the free area
    (the `DesktopStatusBarSpacer` node on Googlebook OS, else the widest gap).
  - `bar/Overlay.kt`: a Compose host in a TYPE_ACCESSIBILITY_OVERLAY window (outside-touch and
    key callbacks for menus).
  - `bar/BarUi.kt`: the strip (chevron, FitRow, items, clicks/right-clicks/wheel).
  - `bar/Menus.kt`: the menu card, the right-click item menu and the ‹ menu.
  - `items/`: `ItemType` + `ItemState`, `Items` registry + `Ticker` (1 Hz while anything is
    visible), `Env` (samplers, launch helpers), `Timers`, `Calendar`, `Notify` (channels,
    glyph icons, `Chips` for the Live Update chip), and the item types in `SystemItems.kt`,
    `TimeItems.kt` and `ToolItems.kt`.
  - `data/`: `Model.kt` (`BarConfig`, `ItemConfig`, `Section`…) and `Store` (JSON in
    SharedPreferences, a process-wide StateFlow shared by the service and settings).
  - `ui/`: `MainActivity` (nav rail), `BarPage` (preview, sections, item detail), `Reorder` (drag
    and drop across the section cards: the gesture sits on the container, a copy of the row floats
    above the cards, the Store changes once on the drop), `Pages` (Add, Look, Setup, About), `Theme`,
    `MenuKit` and `Controls` (shared UI pieces).
  - "Show when…": a type's `trigger` (in `ItemType.kt`) words its pop-out rule, and its `Threshold`
    is the one source of the option key and default for both `state()` and the settings slider.
  - `tile/Tiles.kt`: the BentoBar, Keep awake and Timer tiles.
  - `util/`: `Sym.kt` (generated), `Ui.kt` (fonts, `SymIcon`), `Fmt.kt`, `Dates.kt` (locale-aware dates
    and times from skeletons), `DebugReceiver.kt`.
- Text: every user-facing string is a resource. `res/values/strings.xml` is US English (the default);
  `res/values-en-rGB/strings.xml` holds only the strings whose British spelling differs. Item titles and
  blurbs are `@StringRes` ids on `ItemType`; non-Compose code uses `Env.str`/`Env.plural`. Counts use
  `<plurals>`, values use positional format args. `res/xml/locales_config.xml` lists the languages.
- `tools/logo.py`: draws the icon (a bento box seen from above: a status bar compartment with a ‹
  and dots cut out, over three item compartments; rice, salmon, tamago and edamame on ink blue):
  launcher foreground/monochrome/background, `ic_bentobar` (24 dp, app header, ‹ menu) and
  `ic_tile_bar` (the QS tile), plus docs/images/icon.png for the README. No font needed.
- `tools/icons.py`: subsets Material Symbols Rounded into `assets/fonts` (outlined and filled),
  writes `Sym.kt`, and exports some glyphs as vector drawables (`res/drawable/sym_*.xml`, for
  tiles and notifications). Add an icon name there, then rerun it.
- `tools/readme_images.py`: composes docs/images/*.png (README) from raw captures in
  ~/.cache/bentobar/shots (see its docstring). Capture with a **demo layout** (`./bento debug cfg` to
  save the user's, `./bento debug import <base64>` to load the demo and later restore theirs) so no
  calendar titles or other personal data show, and with `./bento winshot TITLE|app FILE`, which
  saves a PNG of one of BentoBar's own windows (no pointer, no other apps; written to the debug
  app's cache and pulled with run-as, since a broadcast result can't carry a big image).
- `docs/research/`: the device findings (probe results) and the official-docs research, with URLs.
- `probe/`: the throwaway feasibility probe (Gradle-free build). Not part of the app.

## Dev loop
- `./bento app` builds the debug APK, installs it, enables the service and opens settings.
- `./bento debug dump|open TYPE|ctx TYPE|chevron|barmenu|hover on|off|scroll TYPE N|timer MIN|awake [MIN|off]|bar on|off|finish|reset|add TYPE [section]|set ID k=v|look KEY VALUE|windows`.
  The receiver (`src/debug`, debug builds only) is guarded by DUMP, so only adb can call it.
- `./bento shot`, `./bento menushot` and `./bento appshot` capture the status bar, the open menu and
  the settings window. Menu crops include the menu's shadow margin, which can show other windows
  behind it, so don't publish them.
- Release (`docs/RELEASING.md`): bump `versionCode` and `versionName`, add `docs/release-notes/<version>.md`
  and Play's "What's new" (`store-submission/listing/en-US/release-notes.txt`, 500 characters at most),
  commit, push, then `git tag v<version> && git push origin v<version>`. GitHub Actions
  (`.github/workflows/release.yml`) builds, signs and publishes: `BentoBar.apk` + `SHA256SUMS` as the
  GitHub release, the bundle as a draft on Google Play's closed-testing track. Someone still presses
  "Send for review" in the Play Console. "Run workflow" on the Actions tab is a dry run.
- The release key (alias `bentobar`, cert SHA-256 17:1F:D5:44:…:29:E5:F9:0C; a new key since 2026-09-30, the one Google Play uses too, so GitHub
  installs of 0.5 and earlier must be uninstalled once) lives in the secrets of the GitHub environment
  `release`; you never need the key file. The keystore and its password are backed up in the user's
  Drive folder "Googlebook app signing keys (new keys, 2026-09-30)" (https://drive.google.com/drive/folders/1pZslZAb2fVsigLt6MApNU2A-plq-gXeI), file bentobar-keystore.jks, with a README.
  On a machine that has `~/.config/bentobar/keystore.jks` and `keystore.pass`, `./gradlew :app:assembleRelease`
  still signs with it; without them the release build is unsigned. A debug and a release install
  can't replace each other (different keys), so uninstall first.
- adb uses VSCodeBook's Unix socket (`~/.config/vscodebook/android.env`), never tcp:5037.

## Things learned the hard way (see docs/research/device-findings.md)
- **The status bar window can be opaque.** On the HP it's glyphs on transparent; on the Acer
  Googlebook 14 it's white glyphs on its own black while an app is maximized, and see-through over
  the wallpaper (like the HP) otherwise. The colour sampler tells background and text
  apart (`barColors`), decides dark/light from the background, and `bar/Contrast.kt` holds every
  strip colour to 4.5:1, with pure black as the last fallback for mid grays where neither white nor
  near-black reaches it (`ContrastTest.everyOpaqueBarReaches45` sweeps every gray and a color grid).
  Re-check on any new device: `look fg=… bg=… contrast=…` in the log.
- **Settings state is observed, not read while drawing** (`ui/SetupState.kt`, `bar/BarStatus.kt`).
  In desktop windowing, Settings opens in its own window and BentoBar's stays resumed, so onResume
  alone misses changes; and with strong skipping (Kotlin 2.x) a composable reading Android state
  inside isn't redrawn when its parameters are unchanged.
- **Least privilege** (user feedback): the accessibility config subscribes only to
  `typeWindowsChanged`. No content events, key filtering or motion events. Code reads only the
  status bar window. Don't add broader access for nice-to-haves.
- `notificationTimeout` must stay 0. With 200, Android merges window events; our own strip's
  resize could swallow the status bar's "hidden" event.
- Without content events, the node cache goes stale: `clearCache()` before a full scan. Every 2 s
  a light check refreshes only the spacer node; a full scan runs every 30 s or on change.
- The status bar hides about 1.9 s after an app goes fullscreen (the system's timing); BentoBar
  follows within about 100 ms of the window event.
- Live Update chips show only ONE per app, drop text over about 7 characters, and are hidden while
  the posting app is "visible" (uid TOP). **BentoBar's own settings window open means no chip.**
  With only the accessibility service running (BFGS) the chip shows.
- The Agent-task status bar API is in the platform-37.2 SDK stubs but not on the device (37.1),
  and it needs the assistant role anyway. We compile against 37.2 stubs: check each new API on
  the device.
- `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` isn't public; we open Accessibility settings with the
  `:settings:fragment_args_key` highlight extras.
- Compose in the strip: `ItemState`, `ItemConfig` and `StripEntry` are `@Immutable` so unchanged
  items skip recomposition; items with a `widthKey` (ticking numbers) sit in a fixed-width slot sized for their widest
  reading (`Fmt.widthTemplate`: every number becomes 888, clock digits keep their shape), so neighbors never move.
  The release build uses about 0.8% of one core with network speed on (debug builds are 3–4×
  slower; measure release).
- **Window width:** never WRAP_CONTENT for overlay windows. ViewRootImpl first measures a WRAP_CONTENT
  window at `config_prefDialogWidth` (580 dp here) and Compose never reports MEASURED_STATE_TOO_SMALL,
  so a wider strip was cut off and pushed under the chevron. `MeasuredStrip` reports the natural
  width and the controller sets it exactly; menus get their exact width too.
- **CPU load:** /proc/stat, /proc/loadavg, /proc/uptime and /proc/pressure are denied to apps, and
  `SystemHealthManager.getCpuHeadroom()` throws UnsupportedOperationException here. Readable:
  `/sys/devices/system/cpu/cpuN/cpuidle/stateM/time` (idle residency, µs), cpufreq
  `scaling_cur_freq`, and `/sys/class/kgsl/kgsl-3d0/gpubusy` (Adreno). Load = 1 − Δidle/Δwall per
  core; it matched `top` within ~1 point under load (reads a few points high at idle).
  `./bento debug cpuprobe` checks what's readable on a new device.
- **CPU cost:** measure release builds after `cmd package compile -m speed -f` (adb installs are
  only verified, so JIT dominates at first). The big costs were full node-tree scans on every
  window event (title changes included) and sampling folded items; now ~0.5% of one core with CPU
  and network ticking. The manifest is `profileable` by the shell for simpleperf.
- **What the strip draws and what the ticker samples are one rule** (`BarConfig.couldShow`, built on
  `shows`). Two separate predicates drifted when Show everything became the default: hidden items were
  drawn but never sampled, so a clock froze (`HiddenModeTest.everythingDrawnIsSampled`).
- **Time:** timers and keep awake store wall-clock times (they survive a restart) plus a `ClockAnchor`
  (elapsed realtime and the boot count), so a clock change moves the deadline with it instead of
  ending it early; the timer alarm is `ELAPSED_REALTIME_WAKEUP`, armed again in `Timers.init` (alarms
  don't survive a reboot). Live Update calls (`setRequestPromotedOngoing`, `setShortCriticalText`) are
  API 36.1: behind `Notify.liveUpdates`, since minSdk is 34.
- **Notification actions launch activities directly** (`PendingIntent.getActivity` for the chip's Join
  and Open). A broadcast receiver that then starts an activity is a notification trampoline, blocked
  for targetSdk 31+, and the chip shows exactly when the accessibility service (whose binding allows
  background starts) may be off.
- **Keyboard:** the "BentoBar menu" launcher entry (`ui/BarMenuActivity`, no window) opens the ‹ menu
  listing every item, focused. A focusable accessibility overlay does take key focus after the
  activity finishes (checked on the Acer). Users bind it in the Shortcut Helper; on the Acer, Action
  with B, C, E, F, P and U launch apps and can't be reassigned, and A, G, H, I, L, N, Q, S, V and W
  are system shortcuts; D, J, K, M, O, R, T, X, Y and Z are free.
- **Play Protect's live threat detection** (on-device, Android 17) flagged BentoBar on the Acer as
  "App displays over other apps" (`dumpsys safety_center`, source `GoogleAppProtectionService`,
  `SuspiciousAppIssueType`). It looks for accessibility services that keep imperceptible content on
  screen, and for screen capture. So: no invisible or 1×1 overlays (keep awake is
  FLAG_KEEP_SCREEN_ON on the visible strip, a screen wake lock only while the strip is hidden or
  empty, never on the lock screen; an empty strip's window is made invisible), status bar
  screenshots only on a change and only while the strip shows (never on a timer), every overlay visible and there because the user asked for it, and no adb hooks in release.
- StudioSnap's helper (`~/studiosnap/ss enable`) used to overwrite the whole
  `enabled_accessibility_services` list and switch BentoBar off; fixed there on 2026-09-28. Both
  helpers now add or remove only their own entry (short or full component form).
