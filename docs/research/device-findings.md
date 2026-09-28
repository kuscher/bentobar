# DiscoBar: device findings (HP Googlebook 14, Android 17 / SDK 37, build CL3B.260622.270)

Checked over adb with the throwaway probe in `probe/` (package local.discobar.probe).

## The desktop status bar (2026-09-27)
- One window, `StatusBar`, type STATUS_BAR, 1920x41 px at the top (display 1920x1200, density 1.125,
  so 36.4 dp). Status bar inset top = 41. Taskbar is a separate NAVIGATION_BAR window, 132 px, bottom.
- SystemUI runs the desktop status bar (`mIsDesktopStatusBarEnabled=true`), a Compose tree. The
  shade/quick settings open as a popup panel under the bar (Rect(1376,41 - 1826,266)).
- Accessibility tree of the status bar window (AccessibilityWindowInfo type 3 = TYPE_SYSTEM, bounds
  [0,0][1920,41]) exposes every item with bounds and view ids:
  - `dateTimeChip` [27..168] (clock "9:19" desc "9:19 AM", date "Sun, Sep 27"), clickable
  - `DesktopStatusBarSpacer` [168..1681] (empty middle: where DiscoBar items can live)
  - `ImeIndicator` [1681..1722] desc "Keyboard", text "US", clickable
  - `ContextualCursor` [1722..1763] desc "Start Magic pointer", clickable (the pointer+sparkle icon)
  - `notificationIcons` [1763..1817] desc "Notifications" (bell with unread dot), clickable
  - `system_icons` [1817..1893] desc "Quick settings." containing `statusIcons` (Wi-Fi "Wifi signal
    full.") and `battery` ("Battery charging, 100 percent.")
- App notification icons are NOT shown individually on this bar (only the bell), so
  notification-icon "items" would be invisible here.
- Legacy icon slots (StatusBarIconController, 38 slots incl. connected_display, screen_record,
  sensors_off) still exist. Settings.Secure `icon_blacklist` hides e.g. `wifi` live, but clock,
  date, IME, Magic Pointer, bell and battery ignore it. It needs WRITE_SECURE_SETTINGS (adb only),
  so it is OUT of scope: the user wants a plain installable APK, no adb or system changes.

## New public APIs in SDK 37 (android-37.jar)
- `Notification.MetricStyle` + `Notification.Metric` (FixedText/FixedFloat/FixedInt/FixedDate/
  FixedTime/TimeDifference.forTimer/forStopwatch, setCriticalMetric, semantic styles).
- `StatusBarManager.setAgentTask(AgentTaskUpdate, ...)`, `canSetAgentTask()`,
  `isAgentTaskFeatureSupported()`, package `android.agenticon`.
- Live Updates: `Notification.Builder#setRequestPromotedOngoing`, `setShortCriticalText`,
  `NotificationManager#canPostPromotedNotifications`, permission POST_PROMOTED_NOTIFICATIONS.

## Probe results (2026-09-28)
- **Accessibility overlay on the bar works.** A `TYPE_ACCESSIBILITY_OVERLAY` window at (x, 0), height =
  status bar inset (41 px), `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`,
  `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, draws on top of the status bar and looks native. Mouse
  taps, touch taps and mouse-wheel scroll (`input mouse scroll ... VSCROLL`) reach its views. Hover
  and right-click (context click) not tested yet (need a real pointer).
- **Fullscreen apps:** when an app hides the status bar (`--windowingMode 1` + `hide(statusBars())`),
  the status bar window disappears from `getWindows()` (TYPE_WINDOWS_CHANGED). The overlay stays on
  top of the fullscreen app unless we hide it, so DiscoBar must follow the status bar window.
- **Live Update chips show on the desktop bar**, right side, just left of the IME indicator, as a
  pink-tinted pill with the small icon + text. Verified kinds: `setShortCriticalText("12.3M")`,
  a count-down chronometer (`setUsesChronometer` + `setChronometerCountDown`) showing "04:57",
  `MetricStyle` critical metric (FixedText "12.3" with unit "MB/s" shows just "12.3"), and
  `MetricStyle` TimeDifference.forTimer showing "04:57". `canPostPromotedNotifications()` true by
  default for a sideloaded app; only POST_NOTIFICATIONS (runtime) + POST_PROMOTED_NOTIFICATIONS
  (normal) needed.
  - **Only ONE chip is visible at a time** (four posted → only the newest showed).
  - **Long text is dropped:** "Standup in 12 min" → icon-only chip. ~7 characters max.
  - **Click on a chip** opens that notification as a card under the chip (title, text, actions);
    the chip turns icon-only while open. So a chip can carry up to 3 action buttons as a mini menu.
- **Agent task API (`StatusBarManager#setAgentTask`, `android.agenticon`) is NOT on this device**
  (NoSuchMethodError). It is in the platform-37.2 SDK stubs, but the device runs 37.1. So compile
  against 37.2 stubs carefully: guard or avoid anything newer than the device.

## More findings (2026-09-28, DiscoBar 0.3)
- `config_prefDialogWidth` is 580 dp (652 px): a WRAP_CONTENT overlay window is capped there unless
  its view reports MEASURED_STATE_TOO_SMALL (Compose doesn't). Use exact window widths.
- CPU load sources, tested from the app (untrusted_app): /proc/stat, /proc/loadavg, /proc/uptime,
  /proc/pressure/cpu → denied. SystemHealthManager.getCpuHeadroom / getGpuHeadroom →
  UnsupportedOperationException. Readable: /sys/devices/system/cpu/present ("0-11"),
  cpuN/cpuidle/state{0,1}/time (WFI, cpu-sleep-0), cpufreq policyN/scaling_cur_freq,
  /sys/class/kgsl/kgsl-3d0/gpubusy ("busy total"). Idle-residency load matched `top` (55–61% vs
  56–62% with 6 of 12 cores spinning).
- Window events: WINDOWS_CHANGE_* flags seen in practice are mostly LAYER (0x10), TITLE and
  focus; only ADDED/REMOVED/BOUNDS matter for the status bar. Node prefetch
  (`getRoot(FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST | FLAG_PREFETCH_UNINTERRUPTIBLE)`) reads the
  ~35-node status bar tree in one round trip.
- Memory for context: 31.4 GB RAM; the Linux Terminal VM (crosvm_debian) held 12.2 GB RSS.
