<p align="center">
  <img src="docs/images/icon.png" width="112" alt="DiscoBar icon">
</p>

<h1 align="center">DiscoBar</h1>

<p align="center">
  <b>Add, hide and organise items in your Googlebook's status bar.</b><br>
  What Bartender and iStat Menus do for the Mac menu bar, as an ordinary Android app: no root, no adb.
</p>

<p align="center">
  <a href="../../releases/latest/download/DiscoBar.apk"><b>⬇ Download DiscoBar.apk</b></a>
  &nbsp;·&nbsp; <a href="#install">Install</a>
  &nbsp;·&nbsp; <a href="#privacy">Privacy</a>
  &nbsp;·&nbsp; <a href="CHANGELOG.md">What's new</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Googlebook_OS-Android_17-4F6BED" alt="Googlebook OS, Android 17">
  <img src="https://img.shields.io/badge/internet_permission-none-2E7D32" alt="No internet permission">
  <img src="https://img.shields.io/badge/license-MIT-555555" alt="MIT license">
  <img src="https://img.shields.io/badge/developed_entirely_on-a_Googlebook-E0407E" alt="Developed entirely on a Googlebook">
</p>

<p align="center"><sub>A personal hobby project by <a href="https://github.com/kuscher">Alexander Kuscher</a>, proudly developed entirely on a Googlebook.
Not affiliated with or endorsed by any employer (<a href="#about-this-project">more</a>).</sub></p>

<p align="center">
  <img src="docs/images/hero.png" width="880" alt="DiscoBar's items in the Googlebook status bar, with the CPU menu open under the CPU item">
</p>

## What it does

DiscoBar puts **your own items** into the empty part of the status bar, right next to the
system's icons, in the same font and colour. Items you don't need all the time go **behind the ‹
button**; one click reveals them, another folds them away.

<p align="center">
  <img src="docs/images/bar.png" width="880" alt="The status bar with DiscoBar folded, then with its hidden items revealed">
  <br><sub>Folded (top) and revealed (bottom): a world clock, battery and memory slide in next to the running timer.</sub>
</p>

- **Show when active:** a hidden item can pop out on its own while it has something to say, such
  as a running timer, a meeting about to start or a busy CPU, and disappear again after.
- **Click any item** for its menu: charts, details and actions.
- **Right-click an item** to hide it, move it or change how it looks.
- **Scroll** over the sound item to change the volume, or over the timer to add minutes.
- **Quick Settings tiles** switch DiscoBar off (for presenting), keep the screen awake, or start a
  25-minute timer.
- **Live Update chip:** when DiscoBar is off, a running timer still shows as Android's own status
  bar chip.

DiscoBar stays out of the way. It moves aside when the system's own icons or chips change, and
hides when an app goes full screen or the screen locks.

## Items

| Item | In the bar | In its menu |
|---|---|---|
| **CPU load** | busy % | chart, per-core load, clock speeds, GPU load |
| **Network speed** | ↓ download ↑ upload | chart, totals since start-up, connection |
| **Memory** | RAM in use | used, available, chart |
| **Battery details** | watts, %, temperature or time to full | power in or out, voltage, current, health, cycles, thermal state |
| **Storage** | free space | used and free, shortcuts to clean up |
| **Date and calendar** | the date, in your format | a month view with your events |
| **Next meeting** | title and countdown | today's agenda, **Join** for video calls |
| **Clock** | a second clock: seconds, 24-hour or another city | world clocks |
| **Timer** | countdown, stopwatch or Pomodoro | start, pause, +1 min, stop |
| **Countdown** | days and hours to a date | exact time left |
| **Keep awake** | coffee cup: click to switch | 15 min to "until I turn it off" |
| **Sound** | volume | volume slider, play/pause/next |
| **Tools** | toolbox | screenshot, lock, overview, all apps, power, settings shortcuts |
| **App folder** / **App shortcut** | your apps | a grid of apps / one click to open |
| **Text or emoji**, **Spacer** | anything you like | optional link |

<p align="center">
  <img src="docs/images/menus.png" width="880" alt="DiscoBar menus: network, timer, the right-click item menu, tools, and the list of hidden items">
</p>

## Settings

A live preview of your bar at the top; below it, your items in three groups (**in the bar**,
**hidden behind ‹** and **off**) with each item's options on the right. Changes apply to the
status bar right away.

<p align="center">
  <img src="docs/images/settings.png" width="880" alt="DiscoBar settings: the bar preview, the item list and the CPU item's options">
</p>

<p align="center">
  <img src="docs/images/settings-pages.png" width="880" alt="Adding items, and the look and behaviour options">
  <br><sub>Adding items, and choosing where DiscoBar sits and how it looks.</sub>
</p>

## Install

DiscoBar is made for Googlebooks (Googlebook OS, Android 17). Other Android 14+ devices with a
status bar may work, but haven't been tested.

1. On your Googlebook, download
   **[DiscoBar.apk](../../releases/latest/download/DiscoBar.apk)** from the latest release.
2. Open it from Chrome's downloads or the Files app. If Android asks, allow Chrome (or Files) to
   install apps, then tap **Install**.
3. Open **DiscoBar** and go to **Setup** › **Turn on**. Android opens Accessibility settings.
4. Because DiscoBar came from a download, Android guards this switch the first time:
   1. Open **DiscoBar** and tap the switch. Android says *"Restricted setting"*. Tap **OK**.
   2. Back in DiscoBar, tap **App info**, then **⋮** (top right) › **Allow restricted
      settings**, and confirm with your PIN.
   3. Return to Accessibility › **DiscoBar** and turn it on.
5. Optional: in **Setup**, allow notifications (timer alerts, the Live Update chip) and calendar
   access (next meeting, events in the month view).

About a day later, Android shows *"Review app with full device access"*. That's a standard check
for every app that uses an accessibility service. Keep DiscoBar if you're happy with what it does
(below).

To update, install a newer `DiscoBar.apk` over the old one; your layout stays.

<a id="privacy"></a>
## Privacy: why an accessibility service, and what DiscoBar does with it

Android lets an app draw on top of the status bar only through an accessibility service, so
Android warns that DiscoBar can "view and control your screen". Here is everything DiscoBar does
with that access:

- It listens only for **windows appearing, going or moving**, not their content, so it can hide
  for full-screen apps and shades.
- It reads the **status bar's layout, and no other window**, to find free space.
- It copies the **colour of the status bar clock**, from a picture of the status bar's own window
  (not your screen), so your items match.
- It runs **system actions** (screenshot, lock, overview, all apps, power) when you pick them in
  the Tools menu.

DiscoBar doesn't read other apps, doesn't watch your keyboard, mouse or touches, and has **no
internet permission**: your layout, your calendar and everything it measures stay on your
Googlebook.

| Permission | Used for |
|---|---|
| Accessibility service | drawing items on the status bar and running Tools actions |
| Notifications *(optional)* | timer alerts, and the Live Update chip |
| Calendar *(optional)* | Next meeting, and events in the month view |
| Alarms and reminders *(optional)* | timers that ring on the second while the Googlebook sleeps |
| Network state, launcher apps | the network menu, and picking apps for shortcuts |

## Good to know

- **System icons stay.** Android doesn't let an installed app hide or reorder the system's own
  icons (clock, Wi-Fi, battery, notifications), so DiscoBar organises its own items next to them.
- **One chip.** Android shows one Live Update chip per app, with about seven characters of text,
  and hides it while that app's own window is open.
- **CPU load** comes from each core's idle time (Android doesn't let apps read overall CPU usage),
  and matches the system's `top` within a point or two.
- **Advanced Protection** (Android 17) turns off accessibility services that aren't assistive
  tools, and with it DiscoBar's bar. The chip and tiles keep working.
- **Light on battery:** about 0.5% of one CPU core with the CPU and network items updating every
  second, and nothing at all while the screen is off.

## Build

Needs JDK 21 and the Android SDK (platform 37).

```sh
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # signed if ~/.config/discobar/keystore.jks and keystore.pass exist
```

`./disco` is the development helper (build, install, enable, test hooks, screenshots) for a
Googlebook connected over Wireless debugging. [CLAUDE.md](CLAUDE.md) explains how the code is
organised, and [docs/research](docs/research) has the platform findings behind the design.

## About this project

DiscoBar is my personal hobby project, made by me, [Alexander Kuscher](https://github.com/kuscher).
It has no affiliation with my employer: my employer didn't make, sponsor, review or endorse it,
and DiscoBar doesn't endorse my employer or its products either. The views, choices and any
mistakes here are mine alone.

It was proudly developed entirely on a Googlebook: written, built and tested on the device
itself, in its Linux terminal and on its own Android, from the first probe of the status bar to
this release.

— Alexander ([@kuscher](https://github.com/kuscher))

## License

DiscoBar is free software under the [MIT License](LICENSE). It includes Google's
[Material Symbols](https://fonts.google.com/icons) (Apache License 2.0) and uses Jetpack Compose,
AndroidX, Kotlin and kotlinx.serialization (Apache License 2.0).

DiscoBar is an independent project. It isn't made by or affiliated with Google, or with Surtees
Studios (Bartender) or Bjango (iStat Menus).
