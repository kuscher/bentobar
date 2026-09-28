# DiscoBar

**Add, hide and organise items in your Googlebook's status bar**, the way Bartender and similar
apps do for the Mac menu bar. It's an ordinary app you install: no root, no adb, no changes to
Android.

- **Add items:** network speed, battery power draw, memory, free storage, the date with a month
  calendar, your next meeting with a Join button, a second clock or world clock, a timer,
  stopwatch and Pomodoro, a countdown, keep-awake, sound and media controls, a Tools menu
  (screenshot, lock, overview, all apps, settings shortcuts), app shortcuts and folders, your own
  text or emoji, and spacers.
- **Organise them:** keep items in the bar, hide them behind the **‹** button (they slide in when
  you click it or rest the pointer on DiscoBar), or switch them off. Hidden items can **show
  themselves when active**: a running timer or a meeting about to start pops out on its own.
- **Use them:** click an item for its menu (charts, details, actions). Right-click an item to hide it,
  move it or change how it looks. Scroll over the sound item to change the volume, or over the timer
  to add minutes.
- **Quick Settings tiles** switch DiscoBar's items off (e.g. to present), keep the screen awake, or
  start a 25-minute timer.
- **Live Update chip:** when DiscoBar is off, your running timer still shows as Android's own
  status bar chip.

DiscoBar matches the status bar: same font, same colour as the clock, and it moves out of the way
when the system's icons change or an app goes full screen.

## Install

1. Download **DiscoBar.apk** from the [latest release](../../releases/latest) on your Googlebook.
2. Open it from Chrome's downloads (or the Files app). If Android asks, allow Chrome (or Files) to
   install apps, then tap **Install**.
3. Open **DiscoBar** and go to **Setup**. Tap **Turn on**: Android opens Accessibility settings.
4. Because DiscoBar came from a download, Android guards this switch:
   1. Open **DiscoBar** and tap the switch. Android says *"Restricted setting"*; tap **OK**.
   2. Back in DiscoBar, tap **App info**, then **⋮** (top right) › **Allow restricted settings**,
      and confirm with your PIN.
   3. Return to Accessibility › **DiscoBar** and turn it on.
5. Optional: allow notifications (timer alerts and the Live Update chip) and calendar access
   (next meeting, events in the month view).

About a day later Android shows *"Review app with full device access"*. That's a standard check
for every app that uses an accessibility service; keep DiscoBar if you're happy with what it does
(below).

## Why an accessibility service, and what it does with it

Android lets an app draw on top of the status bar only through an accessibility service, so
Android warns that DiscoBar can "view and control your screen". Here is everything DiscoBar does
with that access:

- It watches which windows are on screen, without their content, so it can hide when an app goes
  full screen, the screen locks, or a shade slides over the bar.
- It reads the **status bar's layout, and no other window**, to find the free space for your
  items.
- It copies the **colour of the status bar clock**, from a picture of the status bar's own window
  (not your screen), so your items match.
- It runs system actions (screenshot, lock, overview, all apps, power menu) when you pick them in
  the Tools menu.

DiscoBar doesn't read other apps, doesn't watch your keyboard, mouse or touches, and has **no
internet permission**: your settings, calendar and everything it measures stay on the device.

## What it can't do

Android doesn't let an installable app change the system's own status bar icons. So DiscoBar
can't hide or reorder the clock, Wi-Fi, battery or notification bell; it adds and organises its
own items next to them. Android also shows only one Live Update chip per app, with about seven
characters of text, and hides it while that app's own window is open.

With **Advanced Protection** on (Android 17), Android turns off accessibility services that
aren't assistive tools, so DiscoBar can't run. The Live Update chip and tiles still work.

## Build

Needs JDK 21 and the Android SDK (platform 37).

```sh
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # signed if ~/.config/discobar/keystore.jks and keystore.pass exist
```

`./disco` is the development helper (build, install, enable, test hooks, screenshots) for a
Googlebook connected over Wireless debugging. See [CLAUDE.md](CLAUDE.md) for how the code is
organised.

## Licence

DiscoBar is free software under the [MIT License](LICENSE). It includes Google's Material Symbols
(Apache License 2.0) and uses Jetpack Compose, AndroidX, Kotlin and kotlinx.serialization (Apache
License 2.0).

DiscoBar is an independent project. It isn't made by or affiliated with Google, or with Surtees
Studios, the makers of Bartender for Mac.
