# Changelog

## Unreleased

- **Calendar and Next meeting: two items with clear rules.** **Date and calendar** shows the date;
  its menu is the month and the picked day's events, timed and all-day (meetings, flights, holidays,
  birthdays), with Join for video calls and Directions for places. **Next meeting** shows only real
  meetings: timed, on a calendar you can edit, with a video call or someone else invited, so a
  flight or a solo appointment never counts. It looks no further than 3 a.m. tomorrow, shows the
  title and a countdown (accented from 15 minutes before, set it under "Show when", until it ends),
  and just its icon when there are no more meetings today. Its menu lists today's meetings with
  Join and Directions. Tasks that apps like Todoist sync into your calendar are left out of both,
  and of the chip.
- **Drag to reorder in settings.** Drag a row (anywhere with a mouse, by its handle with a finger)
  within its card or onto another one; the other rows slide aside. The up and down arrows are gone:
  the ≡ menu, Alt+Up/Down and screen reader actions on each row do the same moves.
- **"Show when…" in words.** An item's settings say when it pops out of hiding ("Show when CPU load
  is above 80%"), with a slider for the number, and the list says it in short ("shows above 80%
  CPU"). Battery and storage get their own thresholds (20% and 10% by default, as before).
- **Readable on any status bar.** On a status bar with its own solid color (black on the Acer
  Googlebook 14), BentoBar's items came out dark gray on black. It now tells the bar's color from
  the clock's, and its text, accent and warning colors always reach 4.5:1 contrast. The preview in
  settings shows the real colors.
- **Setup tells the truth.** Steps, the Turn on and Allow buttons, and the header update as soon as
  something changes, including in Settings' own window. The header says when there's no room in
  the status bar or a full-screen app hides it, instead of always "Live".
- **Nothing disappears silently.** Items that don't fit move into the ‹ menu and are marked in
  settings. The gap after the network speed now closes again 5 seconds after a big reading.
- **Accessibility.** Icon-only buttons have names, switches and sliders are named by their labels,
  screen readers can press status bar items, menus show keyboard focus, and hovering an item shows
  its name.
- Delete has an undo; the right-click menu's "Remove from the bar" is now "Turn off", which is what
  it did.
- The install guide covers Play Protect's "App blocked" screen.

## 0.5 (2026-09-29): DiscoBar is now BentoBar

- **New name: BentoBar.** Like a bento box, it keeps your status bar items neatly in their
  compartments. The package is now `io.github.kuscher.bentobar`, so BentoBar installs as a new app
  next to DiscoBar instead of updating it. To move over: in DiscoBar, **Look › Copy settings**; in
  BentoBar, **Look › Paste settings**; turn BentoBar on in Setup; uninstall DiscoBar. Same signing
  key as before.
- **New icon:** a bento box seen from above, with a status bar compartment (‹ and item dots) over
  the compartments for the items it holds: rice, salmon, tamago and edamame in an ink-blue box. The
  themed icon, the settings header, the ‹ menu and the Quick Settings tile use the same shape.
- The splash screen shows the icon on its ink background.
- The GitHub repository is now github.com/kuscher/bentobar (old links redirect).

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
