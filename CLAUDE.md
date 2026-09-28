# DiscoBar: notes for working on the code

A Bartender-style status bar organiser for Googlebooks (Googlebook OS = Android 17 desktop). A
plain APK: no adb grants, root or system changes in the product (the user's hard requirement).

## Layout
- `app/src/main/java/io/github/kuscher/discobar/`
  - `bar/BarService.kt`: the AccessibilityService plus `BarController`. It scans the status bar,
    places the strip, hides it (fullscreen, keyguard, screen off, a shade covering the bar), runs
    menus, the keep-awake window and colour sampling, and has adb `debug()` hooks.
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
  - `ui/`: `MainActivity` (nav rail), `BarPage` (preview, sections, item detail), `Pages`
    (Add, Look, Setup, About), `Theme`, `MenuKit` and `Controls` (shared UI pieces).
  - `tile/Tiles.kt`: the DiscoBar, Keep awake and Timer tiles.
  - `util/`: `Sym.kt` (generated), `Ui.kt` (fonts, `SymIcon`), `Fmt.kt`, `DebugReceiver.kt`.
- `tools/logo.py`: draws the disco-ball icon (launcher foreground, monochrome, background, and the
  24 dp `ic_disco` used by the tile and the app).
- `tools/icons.py`: subsets Material Symbols Rounded into `assets/fonts` (outlined and filled),
  writes `Sym.kt`, and exports some glyphs as vector drawables (`res/drawable/sym_*.xml`, for
  tiles and notifications). Add an icon name there, then rerun it.
- `docs/research/`: the device findings (probe results) and the official-docs research, with URLs.
- `probe/`: the throwaway feasibility probe (Gradle-free build). Not part of the app.

## Dev loop
- `./disco app` builds the debug APK, installs it, enables the service and opens settings.
- `./disco debug dump|open TYPE|ctx TYPE|chevron|barmenu|hover on|off|scroll TYPE N|timer MIN|awake [MIN|off]|bar on|off|finish|reset|add TYPE [section]|set ID k=v|look KEY VALUE|windows`.
  The receiver is guarded by DUMP, so only adb can call it.
- `./disco shot`, `./disco menushot` and `./disco appshot` capture the status bar, the open menu and
  the settings window. Menu crops include the menu's shadow margin, which can show other windows
  behind it, so don't publish them.
- Release: `./gradlew :app:assembleRelease`, signed with `~/.config/discobar/keystore.jks` and
  `keystore.pass` (not in git; alias discobar, cert SHA-256 4F:2B:24:07:…:E9:B3:35:C5). The
  keystore is backed up in the user's Drive folder "DiscoBar release key"
  (https://drive.google.com/drive/folders/1DnbGwls_3wZeVpJaNWeOT9jHdoozhSM7) with a README; the
  password is not, it belongs in the user's password manager. A debug and a release install
  can't replace each other (different keys), so uninstall first.
- adb uses VSCodeBook's Unix socket (`~/.config/vscodebook/android.env`), never tcp:5037.

## Things learned the hard way (see docs/research/device-findings.md)
- **Least privilege** (user feedback): the accessibility config subscribes only to
  `typeWindowsChanged`. No content events, key filtering or motion events. Code reads only the
  status bar window. Don't add broader access for nice-to-haves.
- `notificationTimeout` must stay 0. With 200, Android merges window events; our own strip's
  resize could swallow the status bar's "hidden" event.
- Without content events, the node cache goes stale: `clearCache()` before a full scan. Every 2 s
  a light check refreshes only the spacer node; a full scan runs every 30 s or on change.
- The status bar hides about 1.9 s after an app goes fullscreen (the system's timing); DiscoBar
  follows within about 100 ms of the window event.
- Live Update chips show only ONE per app, drop text over about 7 characters, and are hidden while
  the posting app is "visible" (uid TOP). **DiscoBar's own settings window open means no chip.**
  With only the accessibility service running (BFGS) the chip shows.
- The Agent-task status bar API is in the platform-37.2 SDK stubs but not on the device (37.1),
  and it needs the assistant role anyway. We compile against 37.2 stubs: check each new API on
  the device.
- `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` isn't public; we open Accessibility settings with the
  `:settings:fragment_args_key` highlight extras.
- Compose in the strip: `ItemState`, `ItemConfig` and `StripEntry` are `@Immutable` so unchanged
  items skip recomposition; items with a `widthKey` (ticking numbers) hold their widest text width for 5 s so neighbours
  don't jump; the held width is keyed by item id (`key()` in the strip) and resets when the key changes.
  The release build uses about 0.8% of one core with network speed on (debug builds are 3–4×
  slower; measure release).
- StudioSnap's helper (`~/studiosnap/ss enable`) used to overwrite the whole
  `enabled_accessibility_services` list and switch DiscoBar off; fixed there on 2026-09-28. Both
  helpers now add or remove only their own entry (short or full component form).
