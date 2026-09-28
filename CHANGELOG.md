# Changelog

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
