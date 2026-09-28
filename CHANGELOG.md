# Changelog

## 0.4.1 (2026-09-28)

- Says clearly who made DiscoBar: a personal hobby project by Alexander Kuscher, not affiliated with
  or endorsed by the author's employer (and not endorsing it either), proudly developed entirely on
  a Googlebook. In the README, the app's About page and the license.

## 0.4 (2026-09-28)

- **New icon:** a status bar with a ‹ and item dots over the Tools glyph, white on a Settings-style
  blue ("a tool for your status bar"); the Quick Settings tile shows the bar.
- README with screenshots and an install guide; first GitHub release.
- The CPU menu shows an idle GPU as 0% instead of hiding the line; DiscoBar's own menu uses its icon.
- Nothing runs while the screen is off (the status bar check pauses too).

## 0.3 (2026-09-28)

- **CPU load item:** device-wide load from each core's idle-state counters (Android blocks
  /proc/stat and the CPU headroom API isn't supported on Googlebooks), with a chart, per-core bars,
  clock speeds and, on Qualcomm, GPU load. Memory now has a RAM icon.
- Fixed items being cut off and the ‹ button overlapping items when the bar got wide: Android
  capped the bar's window at its 580 dp dialog width. The window now takes the exact width.
- Revealed items stay until you click ‹ again; hiding them after a few seconds is an option
  (existing setups move from the old 8 s default to this).
- Without the ‹ button, hidden items only appear while active (nothing can fold them back).
- DiscoBar no longer hides itself for small system windows near the top; only a shade covers it.
- About 3× lighter: it reacts only to windows appearing, going or moving; reads the status bar
  in one call instead of ~40; and doesn't sample folded-away items.

## 0.2 (2026-09-28)

- Renamed to **DiscoBar** (package `io.github.kuscher.discobar`), with a disco-ball icon.
- Tighter bar: icons sized like the system's (15 sp), 12 dp between items, and no more big gaps
  (e.g. before a finished timer's "Done"): held widths now belong to each item and only apply to
  ticking numbers, for 5 s.
- More compact menus (smaller headers, tiles, timer face and calendar).
- Revealing hidden items on hover is now off by default (Look › Hidden items).
- New release key (the 0.1 key was never used for a published build).

## 0.1 (2026-09-28), as BarBook

First version, tested on an HP Googlebook 14 (Googlebook OS, Android 17).

- DiscoBar's own items in the status bar's free space, next to the system icons (or centred, or
  after the clock), in the status bar's own text colour.
- Sections like Bartender's: in the bar, hidden behind ‹ (revealed on click or hover), or off.
  Hidden items can show themselves while active (a running timer, a meeting about to start).
- 16 item types: network speed, battery details, memory, storage, date and month calendar, next
  meeting, world clock, timer/stopwatch/Pomodoro, countdown, keep awake, sound, tools, app folder,
  app shortcut, text, spacer.
- A drop-down menu per item, a right-click menu to move or restyle it, mouse-wheel actions
  (volume, timer minutes).
- The running timer (or a meeting about to start) as an Android Live Update chip when DiscoBar
  is off.
- Quick Settings tiles: DiscoBar on/off, Keep awake, Timer.
- Settings with a live preview, reordering, per-item options, look and behaviour, setup, backup
  to the clipboard.
