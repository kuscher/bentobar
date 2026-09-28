# DiscoBar: Android platform research (official docs)

Research date: 2026-09-28. Target: Googlebook OS (Android 17 / API 37, desktop mode), app delivered as a normal sideloaded APK (GitHub; maybe Play later), no adb grants, no root.

Sources are official Google pages (developer.android.com, android-developers.googleblog.com, support.google.com, source.android.com, cs.android.com / android.googlesource.com). Anything not confirmed on an official page is marked **(unverified)**. A few device facts are tagged **(probe)**; they come from the on-device probe on 2026-09-27 (SDK 37.1 build CL3B.260622.270), not from docs.

Status: complete (all 8 topics + gotchas).

## 1. Accessibility services for sideloaded apps (Android 13–17)

### 1.1 Restricted settings (13–14) and Enhanced Confirmation Mode (ECM, 15+)
- **What the user sees:** the service's switch is greyed out. Tapping it shows **"Restricted setting"**: *"For your security, this setting is currently unavailable."* ([PermissionController strings.xml](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/PermissionController/res/values/strings.xml)).
- **How to unlock:** Settings > Apps > (app) > ⋮ **More** > **Allow restricted settings**, then follow the prompts. Google warns to do this only for developers you trust ([Android Help 12623953](https://support.google.com/android/answer/12623953)).
- **Order matters:**
  - The ⋮ item appears **only after** the user has hit the dialog once. Showing the dialog calls `setClearRestrictionAllowed()` ([EnhancedConfirmationDialogActivity.kt](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/PermissionController/src/com/android/permissioncontroller/ecm/EnhancedConfirmationDialogActivity.kt)).
  - Settings shows the item when `isClearRestrictionAllowed()` is true; on 13–14 the test is app-op `ACCESS_RESTRICTED_SETTINGS == MODE_IGNORED`.
  - Tapping it asks for the lock-screen credential ([AppInfoDashboardFragment.java](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-release/src/com/android/settings/applications/appinfo/AppInfoDashboardFragment.java)).
  - The unlock is **per package**: `clearRestriction` marks the whole app NOT_GUARDED.
- **Which installs are guarded** (`isPackageEcmGuarded` in [EnhancedConfirmationService.java](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android17-release/service/java/com/android/ecm/EnhancedConfirmationService.java), android15/16/17-release):
  - Never guarded: preinstalled apps, and packages or installers allow-listed by certificate in `/system/etc/sysconfig/enhanced-confirmation.xml`.
  - **Always guarded:** `PACKAGE_SOURCE_LOCAL_FILE` or `PACKAGE_SOURCE_DOWNLOADED_FILE`, i.e. an APK opened from a browser or file manager ([PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller), API 33).
  - Otherwise, guarded unless the immediate installer is preinstalled or allow-listed. With **no** trusted installers listed, every installer is trusted.
  - `adb install` (shell) is not guarded.
  - **(probe)** On the Googlebook, `enhanced-confirmation.xml` lists **no trusted installers**, so only the LOCAL_FILE/DOWNLOADED_FILE rule applies: Chrome/Files installs are guarded. Installs through a store app are guarded only if that store sets one of those sources **(unverified per store)**.
  - ECM is off on TV and Automotive. Android 17 adds OEM overlay `config_enhancedConfirmationModeExemptSettings`, which can exempt single settings or all of them (`"*"`).
- **Protected per package** (the 15, 16 and 17 lists are identical):
  - SMS permissions and `BIND_DEVICE_ADMIN`
  - app-ops `BIND_ACCESSIBILITY_SERVICE` and `ACCESS_NOTIFICATIONS` (notification listener)
  - `SYSTEM_ALERT_WINDOW`, `GET_USAGE_STATS`, `LOADER_USAGE_STATS`
  - the dialer and SMS roles

  Whether Googlebook's Settings enforces SAW and usage access is **(unverified)**.
- **Scam-call guard (16+, flag-gated):** during a call from an untrusted number, enabling a **non-tool** service is blocked (*"Can't complete action during call"*).

### 1.2 Android 17: Advanced Protection Mode (AAPM) vs non-tool services
- Official help: AAPM *"Restricts accessibility services to verified accessibility tools"* and *"will block the installation of apps from unknown sources and … updates for apps originally installed from unknown sources"* ([Android Help 16339980](https://support.google.com/android/answer/16339980); [dev page](https://developer.android.com/privacy-and-security/advanced-protection-mode): "Blocked app sideloading").
- Android 17 source: `FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES` makes `AccessibilityManagerService` set the global user restriction `DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE`. It permits only packages that are **system or `isAccessibilityTool`, and contain no non-tool service**, and shuts down non-tool services that are already enabled ([AccessibilityManagerService.java, android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java), `getPermittedServicesStrictApm`). The press quotes the toggle text as "Restricted by Advanced Protection" **(unverified)**.
- Detect it with `AdvancedProtectionManager.isAdvancedProtectionEnabled()` and `registerAdvancedProtectionCallback()`, which need the `QUERY_ADVANCED_PROTECTION_MODE` permission (API 36, [reference](https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager)).
- Android 17 has no other accessibility or ECM behaviour changes, apart from CJKV text-change types ([behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17)).

### 1.3 `android:isAccessibilityTool`
- `R.attr.isAccessibilityTool` (API 31, default false): *"If this flag is false, system will show a notification after a duration to inform the user about the privacy implications of the service"* ([R.attr](https://developer.android.com/reference/android/R.attr#isAccessibilityTool)).
- Play allows it only for apps whose **primary purpose** is disability support (screen readers, switch, voice, Braille). Play names *"automation tools, assistants, … launchers"* as non-tools ([Play Help 10964491](https://support.google.com/googleplay/android-developer/answer/10964491)). **DiscoBar must not set it.**

### 1.4 Google Play AccessibilityService policy
Sources: [Play Help 10964491](https://support.google.com/googleplay/android-developer/answer/10964491) and [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170).
- **Forbidden uses:**
  - changing settings without permission, or blocking disable or uninstall
  - *"work around Android built-in platform security controls, privacy controls and notifications"*
  - changing the UI *"in a way that is deceptive"*
  - remote call-audio recording
  - autonomous agents (deterministic "if X then Y" automation is allowed)
- The use must be documented in the listing. Apps *"must use more narrowly scoped APIs … when possible"*. DiscoBar's case is that `TYPE_APPLICATION_OVERLAY` sits *"below critical system windows like the status bar"* ([LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)).
- **Prominent disclosure** (non-tools only):
  - inside the app, shown in normal use and not buried in settings
  - describes the data accessed and how it is used or shared
  - requires affirmative consent
  - not only in the privacy policy or ToS
  - not bundled with other disclosures
  - never replaced by the service `description`
- **Declaration form** (Play Console > App content):
  - Why the API is needed (e.g. "App functionality").
  - Whether data is collected or shared through it; if yes, which types.
  - A **video**: app opens → full disclosure → consent and grant → decline and re-trigger → core feature.
  - Resubmit whenever usage changes.

### 1.5 Disclosure copy ([Play Help 11150561](https://support.google.com/googleplay/android-developer/answer/11150561))
- Show it right before sending the user to Settings.
- Offer two options, consent and "Not now", and degrade gracefully if the user declines.
- Say "Agree", not "Allow access" or "Got it". Don't make it look like system UI.
- Order the content **Why → What → How**. Clarity beats brevity; aim for the reading level of a 13-year-old. Mind consent fatigue.
- DiscoBar's substance: it reads the status-bar layout and window list only to place its own items; it doesn't read or store app content; nothing leaves the device. Also tell users about the reminder in §1.7.

### 1.6 Overlay z-order and window APIs
- `TYPE_ACCESSIBILITY_OVERLAY` (2032) is *"overlaid only by a connected AccessibilityService … without changing the windows an accessibility service can introspect"* ([LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)).
- **Z-order**, bottom to top (`getWindowLayerFromTypeLw`, [WindowManagerPolicy.java android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/policy/WindowManagerPolicy.java)):

  | Window type | Layer |
  |---|---|
  | `APPLICATION_OVERLAY` | 11 |
  | IME | 13 |
  | `STATUS_BAR` | 15 |
  | `NOTIFICATION_SHADE` | 17 |
  | `VOLUME_OVERLAY` | 22 |
  | `NAVIGATION_BAR` | 24 |
  | `SCREENSHOT` | 26 |
  | **`ACCESSIBILITY_OVERLAY`** | **31** |
  | `SECURE_SYSTEM_OVERLAY` | 33 |
  | `POINTER` | 35 |

  DiscoBar's items therefore draw **over the shade, QS and lock screen** unless DiscoBar hides them.
- API 34 adds `attachAccessibilityOverlayToDisplay(displayId, SurfaceControl)` and `…ToWindow` ([AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)).
- `getWindows()` (default display, top-most first) and `getWindowsOnAllDisplays()` (API 30) need `canRetrieveWindowContent` **and** `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`. Without the flag the list is empty and no `TYPE_WINDOWS_CHANGED` arrives ([AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS)).
- Window types include `TYPE_SYSTEM` and `TYPE_WINDOW_CONTROL` (API 36, e.g. desktop captions) ([AccessibilityWindowInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo)).
- `typeAllMask` *"can be resource intensive"* ([guide](https://developer.android.com/guide/topics/ui/accessibility/service)).

### 1.7 "App is using accessibility" reminders
- **With Safety Center on** (on the Googlebook **(probe)**), the framework reminder is disabled (`!isSafetyCenterEnabled()` in AccessibilityManagerService). PermissionController's `AccessibilitySourceService` takes over ([source](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/PermissionController/src/com/android/permissioncontroller/privacysources/AccessibilitySourceService.kt)):
  - A **daily** job (DeviceConfig `sc_accessibility_job_interval_millis`) posts **one notification per enabled non-tool service**: *"Review app with full device access"* / *"<App> can view your screen and perform actions on your device…"*, with a **Remove access** button.
  - These notifications are at least about 0.8 days apart, one at a time.
  - **Disabling and re-enabling re-arms it.**
  - Safety Center also shows an info-level card while the service is enabled.
- **Without Safety Center:** a one-shot notification **24 h after bind**, never repeated once dismissed (`NOTIFIED_NON_ACCESSIBILITY_CATEGORY_SERVICES`) ([PolicyWarningUIController.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/PolicyWarningUIController.java)).

## 2. Live Updates (promoted ongoing notifications, 16+) and Android 17 `MetricStyle`

**Eligibility** ([Live Updates guide](https://developer.android.com/develop/ui/views/notifications/live-update)):
- The style is standard, `BigTextStyle`, `CallStyle`, `ProgressStyle` or `MetricStyle`.
- The app declares `POST_PROMOTED_NOTIFICATIONS`. It is `normal|appops`, added in 36.1, and needed **in addition to** `POST_NOTIFICATIONS` ([Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#POST_PROMOTED_NOTIFICATIONS)).
- The notification calls `setRequestPromotedOngoing(true)` (36.1).
- It is ongoing and has a `contentTitle`.
- It has no custom `RemoteViews`, is not a group summary, and is not colorized.
- Its channel is not `IMPORTANCE_MIN`.
- *"OEMs can enforce additional criteria."*

**Checks:**
- `Notification.hasPromotableCharacteristics()`: false means it will never be promoted; it ignores user settings.
- `FLAG_PROMOTED_ONGOING` is set by the system when promotion actually happens ([Notification](https://developer.android.com/reference/android/app/Notification#hasPromotableCharacteristics())).
- `NotificationManager.canPostPromotedNotifications()` reflects the user toggle ([NotificationManager](https://developer.android.com/reference/android/app/NotificationManager#canPostPromotedNotifications())).
- To send the user to the toggle, use `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`, API 36) with `EXTRA_APP_PACKAGE`. The activity may not exist ([Settings](https://developer.android.com/reference/android/provider/Settings#ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)). The guide's `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` is not in the API reference.

**The status-bar chip:**
- **Content priority:**
  1. `setShortCriticalText()` (API 36; suggested ≤ 7 characters; `""` means icon only).
  2. The `MetricStyle` critical metric.
  3. `when`: a positive chronometer, or the time remaining (a `when` at least 2 min ahead shows as "5min").

  Sources: [Notification.Builder](https://developer.android.com/reference/android/app/Notification.Builder#setShortCriticalText(java.lang.String)) and the guide.
- **Layout:** the chip always shows the small icon and is at most **96 dp** wide. Text under 7 characters shows in full; text that is less than half visible is dropped.
- **Dismissal:** users can demote or dismiss the notification. Don't repost; use `setDeleteIntent`.
- **Desktop:** no desktop or large-screen chip documentation exists **(unverified on Googlebook)**.

**`Notification.MetricStyle` (API 37)** ([MetricStyle](https://developer.android.com/reference/android/app/Notification.MetricStyle), [Metric](https://developer.android.com/reference/android/app/Notification.Metric)):
- Shows up to 3 metrics when expanded. It needs at least one, and it doesn't show the large icon.
- When promoted, the critical metric (`setCriticalMetric(index)`, default first, `METRIC_INDEX_NONE` = −1) *"might be displayed in the status bar chip"*.
- Constructor: `Metric(value, label[, semanticStyle])`. Keep labels to 10 characters or fewer.
- Values:
  - `FixedText(text[, unit])`
  - `FixedInt(int[, unit])`
  - `FixedFloat(float[, unit[, minFrac, maxFrac]])`
  - `FixedDate(LocalDate[, FORMAT_AUTOMATIC/SHORT_DATE/LONG_DATE])`
  - `FixedTime(LocalTime)`, shown as hours:minutes
  - `TimeDifference.forTimer(end, fmt)` and `forStopwatch(start, fmt)`, taking an `Instant` or an elapsedRealtime `long`; plus `forPausedTimer` and `forPausedStopwatch(Duration, fmt)`
  - formats `FORMAT_ADAPTIVE` ("1h 5m") and `FORMAT_CHRONOMETER` ("2:00:00")
- **Semantic styles (37):** `SEMANTIC_STYLE_UNSPECIFIED/INFO/SAFE/CAUTION/DANGER`, meaning blue, green, orange, red. They apply to `Metric`, `ProgressStyle.Point`/`Segment`, and text through `Notification.createSemanticStyleAnnotation()`, but only when the notification is promoted ([17 features](https://developer.android.com/about/versions/17/features)).

**Qualifying use cases** (guide): Live Updates are for **ongoing, user-initiated, time-sensitive** activities such as navigation, calls, rides or deliveries.
- Explicitly not: *"Ads, promotions, chat messages, alerts, upcoming calendar events, and quick access to app features"*. Also not *"ambient information"*. For quick access, use a widget or a **QS tile**.
- The Settings reference says promotion is *"reserved for user initiated ongoing activities like navigation, phone calls, and ride sharing"*.
- **DiscoBar:** use them for user-started timers, stopwatches, focus sessions or transfers, not for permanent meters. No Live-Update-specific Play policy text was found **(unverified)**.

## 3. Android 17 `StatusBarManager` agent-task API (`android.agenticon`)

Sources: [StatusBarManager](https://developer.android.com/reference/android/app/StatusBarManager) and [android.agenticon](https://developer.android.com/reference/android/agenticon/package-summary).
- **Availability.** Added in **"version 37.2"**, the minor SDK that ships with Android 17 QPR2, still in beta ([QPR2 notes](https://developer.android.com/about/versions/17/qpr2/release-notes)). The Googlebook reports **SDK 37.1** **(probe)**, so gate on `Build.VERSION.SDK_INT_FULL`.
- **Who may call it.** `canSetAgentTask()` is true only if all three hold:
  1. the caller holds **`RoleManager.ROLE_ASSISTANT`**;
  2. it has an enabled activity that filters `ACTION_AGENT_TASK_MAIN` (`"android.app.action.AGENT_TASK_MAIN"`), whose icon is the default, non-animated state;
  3. *"the corresponding user setting is enabled"*.
- **Device support.** `isAgentTaskFeatureSupported()` reports device support. `isAgentTaskLaunchSupported()` returning false means click `PendingIntent`s are ignored.
- **`setAgentTask(AgentTaskUpdate, Executor, OutcomeReceiver<AgentTaskOutcome,Throwable>)`**:
  - Best effort; the last request wins; current user only. `null` resets to the default.
  - Pair it with `Notification.Builder.setAgentInteractionFlags(FLAG_AGENT_TASK_INTERACTION_HIDE_STATUS_BAR_ICON | …HIDE_VISUAL_ALERTS)`.
  - `getAgentStateScreenLocation(displayId)` returns the icon's `Rect`. It can be slow, so call it on a worker thread.
- **What the status bar shows.**
  - `AgentTaskState`: **one icon** with optional looping or interrupting animation, a content description, and a click `PendingIntent`.
  - `AgentTaskEvent`: a transient pill of leading icons + text + trailing icons. It may be dropped if events are too frequent, the bar is hidden, or more critical information needs the space.
  - `AgentTaskOutcome` reports `isStateChanged()` and `isEventShown()`.
- **Related API.** `StatusBarManager.showPowerMenu()` (37) needs `SHOW_POWER_MENU`, which is *"granted to the current holder of the ASSISTANT role"* ([Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#SHOW_POWER_MENU)). DiscoBar can open the same menu with `performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)`.
- **Verdict: not usable.** Any app with an exported `ACTION_ASSIST` activity qualifies for ROLE_ASSISTANT ([AssistantRoleBehavior.java, android17-release](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android17-release/PermissionController/role-controller/java/com/android/role/controller/behavior/AssistantRoleBehavior.java)). But the user would have to make DiscoBar the digital assistant, replacing Gemini, to get a single icon.

## 4. Quick Settings tiles

From the [QS tiles guide](https://developer.android.com/develop/ui/views/quicksettings-tiles) and [TileService](https://developer.android.com/reference/android/service/quicksettings/TileService):
- **Active mode (recommended).** Add `<meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="true"/>` (`META_DATA_ACTIVE_TILE`).
  - The service is bound only for `onTileAdded`/`onTileRemoved`, taps, and when the app calls `TileService.requestListeningState(context, component)`.
  - In active mode you can update the tile **exactly once** per `onStartListening` before `onStopListening`, whether or not it is visible.
  - `requestListeningState` does nothing without the meta-data. On 33+ targets it throws if the component isn't your own.
  - In non-active mode the service is re-bound each time QS opens.
  - *"Don't assume your TileService will live outside of onStartListening() and onStopListening()."*
- **Toggleable tiles.** Add `<meta-data android:name="android.service.quicksettings.TOGGLEABLE_TILE" android:value="true"/>` for two-state tiles.
- **States and fields.** States are `STATE_ACTIVE`, `STATE_INACTIVE` and `STATE_UNAVAILABLE`. The fields are label, subtitle, icon, `stateDescription` and `contentDescription`; call `updateTile()` after changing them. Tiles can also be categorised with `android.service.quicksettings.TILE_CATEGORY`.
- **Guide advice.**
  - Use at most **two tiles per app**.
  - Don't make tiles that only display information; use a notification or widget instead.
  - Long-press opens App info, unless an activity handles `ACTION_QS_TILE_PREFERENCES`.
- **`startActivityAndCollapse(PendingIntent)`** (API 34) is the one to use. The `Intent` overload is deprecated and *"throws UnsupportedOperationException"* on 34+ targets ([behavior-changes-14](https://developer.android.com/about/versions/14/behavior-changes-14)). Since API 28 the intent must carry `FLAG_ACTIVITY_NEW_TASK`.
- **Locked devices.** `showDialog()` is invisible while `isLocked()`. Use `unlockAndRun()` for unsafe actions and check `isSecure()`.
- **`StatusBarManager.requestAddTileService(component, label, Icon, executor, callback)`** (API 33) shows an in-context "add tile" prompt.
  - The caller must be foreground (`IMPORTANCE_FOREGROUND`) and the TileService exported.
  - Results: `TILE_ADD_REQUEST_RESULT_TILE_ADDED`, `_NOT_ADDED`, `_ALREADY_ADDED`, plus `TILE_ADD_REQUEST_ERROR_*` codes (`APP_NOT_IN_FOREGROUND`, `BAD_COMPONENT`, `REQUEST_IN_PROGRESS`, `NO_STATUS_BAR_SERVICE`, …).
  - The system may auto-deny after repeated refusals ([StatusBarManager](https://developer.android.com/reference/android/app/StatusBarManager#requestAddTileService(android.content.ComponentName,%20java.lang.CharSequence,%20android.graphics.drawable.Icon,%20java.util.concurrent.Executor,%20java.util.function.Consumer%3Cjava.lang.Integer%3E))).
- **Large screens and desktop.** No developer page describes tile behaviour specific to desktop or large screens **(unverified)**. Pixel uses tile categories; *"OEMs can either use or disregard"* them.
- **Accessibility-service tie-ins** ([AccessibilityManagerService.java, android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java)).
  - An enabled service that targets above Q and sets `FLAG_REQUEST_ACCESSIBILITY_BUTTON` is **auto-assigned to the floating accessibility button**. Only **tools** that declare a `tileService` are assigned to QS instead.
  - So DiscoBar should **not** request the accessibility button.

## 5. Special app access a user can grant in Settings (no adb)

| Access | How to request / check | ECM "restricted"? |
|---|---|---|
| **WRITE_SETTINGS** (`Settings.System`, e.g. brightness, screen timeout) | Declare `WRITE_SETTINGS`. Send the user to `Settings.ACTION_MANAGE_WRITE_SETTINGS` (`"android.settings.action.MANAGE_WRITE_SETTINGS"`, API 23) with data `package:<pkg>`. Check with `Settings.System.canWrite()`. | No |
| **Do Not Disturb / Modes access** | `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` (API 23; *"Managed profiles cannot grant"*). Check with `NotificationManager.isNotificationPolicyAccessGranted()`. | No |
| **Notification listener** | `ACTION_NOTIFICATION_LISTENER_SETTINGS` for the list, or `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` (API 30) + `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` for the per-app page. Check with `isNotificationListenerAccessGranted(cn)`. | **Yes** (`OPSTR_ACCESS_NOTIFICATIONS`). One "Allow restricted settings" unlock is **per package**, so it also covers the accessibility service (`clearRestriction` sets the whole app NOT_GUARDED). |
| **SCHEDULE_EXACT_ALARM** | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (API 31) with `package:` data. The result is `RESULT_OK` if granted. Check with `AlarmManager.canScheduleExactAlarms()`. Since Android 14 it is *"denied by default"* for new installs targeting 33+ ([14 changes](https://developer.android.com/about/versions/14/behavior-changes-all)). DiscoBar doesn't need it; a clock UI can use `ACTION_TIME_TICK`/`Handler`. | No |
| **READ_CALENDAR** | Ordinary runtime permission (`requestPermissions`). | No |
| **`MediaSessionManager.getActiveSessions(cn)`** | Requires `MEDIA_CONTENT_CONTROL` (system only) **or** being an enabled notification listener and passing its `ComponentName` ([MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName))). Pair it with `addOnActiveSessionsChangedListener`. | Via the listener: yes |
| **`AudioManager.dispatchMediaKeyEvent(KeyEvent)`** | No permission. Send DOWN then UP; the event goes to the current media-button consumer ([AudioManager](https://developer.android.com/reference/android/media/AudioManager#dispatchMediaKeyEvent(android.view.KeyEvent))). | No |
| Accessibility service | `Settings.ACTION_ACCESSIBILITY_SETTINGS` is the public API. A per-service `"android.settings.ACCESSIBILITY_DETAILS_SETTINGS"` + `Intent.EXTRA_COMPONENT_NAME` is used by the system itself ([PolicyWarningUIController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/PolicyWarningUIController.java)) but isn't in the public reference **(unverified for apps)**. | **Yes** |
| Promoted notifications | `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` + `EXTRA_APP_PACKAGE` (§2). | No |

All intent docs: [Settings](https://developer.android.com/reference/android/provider/Settings). Every one says *"a matching Activity may not exist"*, so wrap each in `try/catch ActivityNotFoundException`. ECM's list also includes SYSTEM_ALERT_WINDOW and usage access (§1.1).

**Android 15 DND change** ([behavior-changes-15](https://developer.android.com/about/versions/15/behavior-changes-15)): apps targeting 35+ *"can no longer change the global state or policy of Do Not Disturb"*.
- Calls to `setInterruptionFilter`/`setNotificationPolicy` now *"result in the creation or update of an implicit AutomaticZenRule"*. The system merges it under most-restrictive-wins.
- Consequence: a DiscoBar "DND" toggle can turn *its own* mode on and off, but `INTERRUPTION_FILTER_ALL` cannot switch off DND that the user or another app started.

**Android 17 background audio hardening** ([bg-audio](https://developer.android.com/about/versions/17/changes/bg-audio)): this matters for volume and mute items.
- `setStreamVolume`, `adjustStreamVolume`, `adjustVolume`, `adjustSuggestedStreamVolume`, `setStreamMute` and `setRingerMode` are **silently ignored**. This applies to all apps unless they have a visible activity or a non-`SHORT_SERVICE` FGS. Apps targeting 37 also need a **while-in-use** FGS when in the background.
- Enforcement uses app-ops `CONTROL_AUDIO_PARTIAL` and `CONTROL_AUDIO`, both `MODE_FOREGROUND`. The latter needs `PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL` ([HardeningEnforcer.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/audio/HardeningEnforcer.java), [AppOpsUidStateTrackerImpl.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/appop/AppOpsUidStateTrackerImpl.java)).
- The system binds accessibility services with `BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_INCLUDE_CAPABILITIES` (§6), so volume calls **probably work while the screen is on (unverified)**.
- Test with `adb shell cmd audio set-enable-hardening throw`, and look for `AudioHardening` in `dumpsys audio`.
- Android 15 also redacts OTP notifications for untrusted notification listeners ([15 all-apps](https://developer.android.com/about/versions/15/behavior-changes-all)).

## 6. Background execution for an accessibility-service app (14–17)

- **No foreground service is needed.** While the service is enabled, system_server binds it with `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS | BIND_INCLUDE_CAPABILITIES` and calls `setAllowAppSwitches` for its uid ([AccessibilityServiceConnection.java, android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java)).
  - **Priority:** while awake it has foreground-service priority and inherits system_server's capabilities; while asleep it is a normal bound service. The exact oom_adj is **(unverified)**.
  - **Activity launches:** it can start activities from the background. The [BAL page](https://developer.android.com/guide/components/activities/background-starts) exempts apps *"bound by a service that has been granted permission to start background activities"* and apps with a visible window.
- **Death.** A crashed service is flagged `crashed` and skipped on rebinds ([AccessibilityManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java)). Recovery UX is **(unverified)**.
- **Android 17 kill reasons:** memory-limit kills (`REASON_OTHER`, `"MemoryLimiter:AnonSwap"`, [17 all-apps](https://developer.android.com/about/versions/17/behavior-changes-all)) and kills for *"abnormal and excessive CPU usage"* (`TRIGGER_TYPE_KILL_EXCESSIVE_CPU_USAGE`, [17 features](https://developer.android.com/about/versions/17/features)).
- **Standby buckets and Doze:** no page says whether they exempt accessibility-bound processes **(unverified)**. Keep `AlarmManager` and `JobScheduler` out of the UI path.
- **Ticks:**
  - Use `Handler.postAtTime()` aligned to the minute or second boundary, or `ACTION_TIME_TICK` (runtime-registered) + `TIME_CHANGED` / `TIMEZONE_CHANGED`.
  - Avoid `Choreographer.postFrameCallback` loops: each one requests a vsync frame.
  - Invalidate only the views that changed; each overlay redraw costs a composition.
  - The only official "always-visible UI" numbers are for Wear watch faces: animations at about 15 fps, and ≥ 90 s of CPU per hour counts as excessive ([Excessive battery usage](https://developer.android.com/topic/performance/vitals/excessive-battery-usage)).
- **Power state** ([PowerManager](https://developer.android.com/reference/android/os/PowerManager)):
  - `isInteractive()` is false when *"dozing or asleep"*. `ACTION_SCREEN_ON`/`OFF` actually track that interactive state. *"Services may use the non-interactive state as a hint to conserve power"*, so **stop polling** then.
  - `isPowerSaveMode()` means *"applications should reduce their functionality"*. Watch `ACTION_POWER_SAVE_MODE_CHANGED` and stretch meter intervals.

## 7. Desktop windowing (16–17): status bar, taskbar, input conventions

- **No developer or AOSP page documents the desktop status bar** (layout, clock position, notification indicator, chips). What exists covers only these:
  - The **taskbar** at the bottom: pinned and running apps; right-click an icon for pin, new window, close and app shortcuts.
  - The **header bar** on freeform windows (minimize, maximize, close; customisable insets via `APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND` and `WindowInsets.isCaptionBarVisible`).
  - Sources: [Desktop system bars](https://developer.android.com/design/ui/desktop/guides/system/system-bars), [Support desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing).
  - The only documented status-bar chips are Live Update chips (§2) and the **media-projection chip** (15 QPR1+): *"A new, prominent status bar chip makes users aware of any ongoing screen projection. Users can tap the chip to stop"* ([15 features](https://developer.android.com/about/versions/15/features)).
  - Treat Googlebook's status-bar layout as OEM SystemUI that can change with any update **(unverified in docs)**, and discover it at runtime from the accessibility tree.
- **Desktop-first vs touch-first** is a per-display mode ([AOSP Desktop windowing](https://source.android.com/docs/core/display/desktop-windowing)):
  - A display is desktop-first when a keyboard **and** a touchpad or mouse are connected. External displays usually default to desktop-first.
  - Convertibles can switch on posture ("keyboard flipped back" means touch-first).
  - Desktop-first always uses the **Desktop Taskbar**; touch-first uses the transient taskbar.
  - **DiscoBar must re-layout when the mode, display or posture changes.** Each connected display can host its own bars; use `getWindowsOnAllDisplays()` and `attachAccessibilityOverlayToDisplay()`.
- **Keyboard shortcuts:** `Meta+Ctrl+Down` enters desktop windowing and `Meta+H` exits (support guide); `Alt+Tab` switches windows ([Multitasking](https://developer.android.com/design/ui/desktop/guides/system/multi-task)). Apps should support Tab and arrow navigation, **Esc to dismiss menus and popovers**, and list their shortcuts in the Keyboard Shortcuts Helper ([Keyboard interaction](https://developer.android.com/design/ui/desktop/guides/interaction/keyboard)).
  - The helper is fed through `Activity.onProvideKeyboardShortcuts`, so a service-only overlay can't use it.
  - Global hotkeys need the accessibility key filter: `FLAG_REQUEST_FILTER_KEY_EVENTS` + `canRequestFilterKeyEvents`, handled in `onKeyEvent` ([AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo)). Expect Play scrutiny.
- **Pointer conventions** ([Pointer interactions](https://developer.android.com/design/ui/desktop/guides/interaction/pointer-interactions), [Input compatibility](https://developer.android.com/guide/topics/large-screens/input-compatibility-large-screens)):
  - Every journey must work with primary clicks alone.
  - Secondary click opens context menus: `View.setOnContextClickListener` (API 23) also catches two-finger taps.
  - Give hover a visual state and show a tooltip. `View.setTooltipText` (API 26) shows *"on hover, after a brief delay"* and on long-press ([View](https://developer.android.com/reference/android/view/View#setTooltipText(java.lang.CharSequence))).
  - Pointer targets may be smaller than 48 dp.
  - Show the **hand cursor** on clickable items: `View.setPointerIcon(PointerIcon.getSystemIcon(ctx, PointerIcon.TYPE_HAND))` (API 24; mouse sources only) ([PointerIcon](https://developer.android.com/reference/android/view/PointerIcon), [Cursors guide](https://developer.android.com/design/ui/desktop/guides/interaction/cursors)).
- **Related 17 items** ([17 release notes](https://developer.android.com/about/versions/17/release-notes)):
  - Desktop interactive PiP (`USE_PINNED_WINDOWING_LAYER`).
  - A bubble bar in the taskbar.
  - `CONFIG_UI_MODE` changes to or from `UI_MODE_TYPE_DESK` no longer restart activities unless the app opts in with `android:recreateOnConfigChanges`.

## 8. Public data sources for status items and their limits

| Item | API and verified limits |
|---|---|
| **Network throughput** | `TrafficStats.getTotalRxBytes()`/`getTotalTxBytes()` are **device-wide**: *"Counts packets across all network interfaces"*, monotonic since boot, reset on reboot. `getUidRxBytes` covers **only the calling UID** since N and returns `UNSUPPORTED` for others ([TrafficStats](https://developer.android.com/reference/android/net/TrafficStats)). Take deltas each second, and only while the screen is on. |
| **CPU usage** | **`/proc/stat` is not readable.** SELinux `neverallow all_untrusted_apps { proc_stat proc_loadavg proc_uptime proc_vmstat … }`. The policy comments *"These have been disallowed since Android O"* ([app_neverallows.te](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/android17-release/private/app_neverallows.te), [untrusted_app_all.te](https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/android17-release/private/untrusted_app_all.te)). The best public proxy is **`SystemHealthManager.getCpuHeadroom(params)`** (API 36): *"estimate of available CPU capacity headroom of the device"*, 0–100 (0 means no capacity left), `NaN` if unavailable, **`UnsupportedOperationException` if unsupported**. Each call is at least one binder call of over 1 ms; respect `getCpuHeadroomMinIntervalMillis()` ([SystemHealthManager](https://developer.android.com/reference/android/os/health/SystemHealthManager)). Show it as "CPU load ≈ 100 − headroom" and label it an estimate. |
| **Memory** | `ActivityManager.getMemoryInfo(MemoryInfo)` gives `availMem` (*"should not be considered absolute"*), `totalMem`, `threshold`, `lowMemory`, and `advertisedMem` (retail RAM size) ([MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo)). |
| **Battery** | `BatteryManager.getIntProperty/getLongProperty` ([BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)).<br>• `CURRENT_NOW` and `CURRENT_AVERAGE`: **microamperes**, positive while charging, negative while discharging.<br>• `CAPACITY`: %. `CHARGE_COUNTER`: µAh. `ENERGY_COUNTER`: nWh. `STATUS` (26).<br>• Unsupported properties return `Integer.MIN_VALUE` on target P and later.<br>• `computeChargeTimeRemaining()` (API 28): ms, or −1 while discharging or without enough data.<br>• `ACTION_BATTERY_CHANGED` sticky extras include `EXTRA_CYCLE_COUNT` and `EXTRA_CHARGING_STATUS` (both API 34). |
| **Thermal** | `PowerManager.getCurrentThermalStatus()` (29) returns `THERMAL_STATUS_*`; there is also `addThermalStatusListener`. `getThermalHeadroom(forecastSeconds)` (30): 1.0 means severe throttling. Calling it more than about once a second *"may result in the function returning NaN"* ([PowerManager](https://developer.android.com/reference/android/os/PowerManager)). |
| **Disk** | `StatFs.getAvailableBytes()`/`getTotalBytes()` (18). For display, `StorageStatsManager.getFreeBytes(UUID_DEFAULT)` and `getTotalBytes(…)` are *"best suited for visual display to end users"*. They need no permission and can take seconds, so call them off the main thread ([StorageStatsManager](https://developer.android.com/reference/android/app/usage/StorageStatsManager)). |
| **Next meeting** | `READ_CALENDAR` + `CalendarContract.Instances`. You *"need to specify a range time for the query in the URI"* (`Instances.CONTENT_URI` + begin/end, or `Instances.query(cr, proj, begin, end)`). The table is read-only ([Instances](https://developer.android.com/reference/android/provider/CalendarContract.Instances), [Calendar provider guide](https://developer.android.com/identity/providers/calendar-provider)). Re-query on `PROVIDER_CHANGED` or a `ContentObserver`. |
| **Next alarm** | `AlarmManager.getNextAlarmClock()` returns the next alarm from *any* app's `setAlarmClock()`, or null. It needs no permission. Watch `ACTION_NEXT_ALARM_CLOCK_CHANGED` ([AlarmManager](https://developer.android.com/reference/android/app/AlarmManager#getNextAlarmClock())). |
| **Wi-Fi strength** | Since API 31 `WifiManager.getConnectionInfo()` is deprecated. Read `WifiInfo` from `NetworkCapabilities.getTransportInfo()` via `NetworkCallback.onCapabilitiesChanged` (needs `ACCESS_NETWORK_STATE`). **Without** location permission and `FLAG_INCLUDE_LOCATION_INFO`, only location-sensitive fields are redacted: SSID becomes `UNKNOWN_SSID` and BSSID `02:00:00:00:00:00`. **`getRssi()` (dBm) stays available**; `WifiManager.calculateSignalLevel(rssi)` (API 30) maps it to bars ([WifiInfo](https://developer.android.com/reference/android/net/wifi/WifiInfo), [NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback#FLAG_INCLUDE_LOCATION_INFO), [WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager)). Showing the SSID needs location permission. |
| **Text colour** | `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` (27) can return **null** (colours still processing, live wallpaper). It is IPC, so don't call it on the UI thread. `WallpaperColors.getColorHints() & HINT_SUPPORTS_DARK_TEXT` (31) means *"dark text is preferred"* ([WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager), [WallpaperColors](https://developer.android.com/reference/android/app/WallpaperColors)). Listen with `addOnColorsChangedListener`. This only helps when the bar sits over the wallpaper; over maximized app windows SystemUI changes its tint, and DiscoBar must follow it (e.g. by sampling a status-bar screenshot) **(unverified approach)**. |

## Gotchas for DiscoBar

1. **Onboarding takes 6 steps.** GitHub APKs installed through Chrome or Files are ECM-guarded. The user must: tap the switch → see "Restricted setting" → App info → ⋮ → **Allow restricted settings** → PIN → enable. The ⋮ item only appears after the dialog. Guide each step and re-check state in `onResume`. One unlock covers the notification listener too.
2. **Advanced Protection kills the product.** It blocks sideloading, and on 17 it blocks non-tool services. Detect it and fall back to tiles and notifications. **Never set `isAccessibilityTool`.**
3. **Overlays sit above everything** (layer 31, above the status bar, shade/QS, lock screen, volume dialog and taskbar). Hide DiscoBar's items whenever the shade, QS, keyguard, a fullscreen app or the capture UI is active. Drive this from `TYPE_WINDOWS_CHANGED`.
4. **Don't cover or imitate system UI.** Keep clear of the notification indicator, privacy dots, the screen-share chip and Live Update chips. Play bans using the API to *"work around … privacy controls and notifications"* or to deceive.
5. **The desktop status bar is undocumented OEM UI.**
   - Derive free space at runtime from `getWindowsOnAllDisplays()` and the SystemUI node tree.
   - Re-layout when windows, config, locale or IME, or chips change; on desktop-first ↔ touch-first switches; and per external display.
6. **Users will see "Review app with full device access"** about a day after enabling, and again after each re-enable, with a **Remove access** button. Pre-warn them.
7. **Keep the service lean and crash-proof.** A crash marks it `crashed`. Android 17 kills apps over memory limits (`MemoryLimiter:AnonSwap`) and for excessive CPU. Keep IPC-heavy calls (calendar, storage stats, wallpaper colours) off the main thread.
8. **Volume and mute calls may silently no-op on 17.** Test them with `cmd audio set-enable-hardening throw`. On 15+ a DND toggle drives only DiscoBar's own implicit mode.
9. **There is no true device CPU %.** `/proc/stat` is blocked. `getCpuHeadroom()` is an estimate, may be unsupported, and is rate-limited.
10. **Live Updates aren't a permanent slot.** They must be user-initiated and time-sensitive, the chip is ≤ 96 dp and about 7 characters, and users can demote them.
11. **The agent-task icon and `showPowerMenu` need ROLE_ASSISTANT** (and the agent API is 37.2, while the device reports 37.1). Use `performGlobalAction(GLOBAL_ACTION_POWER_DIALOG / _NOTIFICATIONS / _QUICK_SETTINGS)` instead.
12. **Config hygiene.**
    - No `flagRequestAccessibilityButton`, which auto-adds the floating button.
    - No `typeAllMask`.
    - Set `canRetrieveWindowContent` plus `flagRetrieveInteractiveWindows`, or `getWindows()` is empty.
13. **Battery.**
    - Stop all tickers when `!isInteractive()` or when the overlay is hidden; slow them under `isPowerSaveMode()`.
    - Use `TIME_TICK` or aligned `postAtTime` for the clock, never Choreographer loops.
    - Call thermal headroom no more than once per second.
14. **Play readiness.**
    - Accessibility declaration with a video (disclosure, accept and decline paths, the feature).
    - A standalone "Agree / Not now" disclosure.
    - Use documented in the listing.
    - Be ready to argue that no narrower API works.
15. **Wrap every Settings intent in try/catch.** The reference warns *"a matching Activity may not exist"*. Use the reference names; the Live Update guide's intent name is wrong.

