# BarBook: Android platform research (official docs)

Research date: 2026-09-28. Target: Googlebook OS (Android 17 / API 37, desktop mode), app delivered as a normal sideloaded APK (GitHub; maybe Play later), no adb grants, no root.

Sources are official Google pages (developer.android.com, android-developers.googleblog.com, support.google.com, source.android.com, cs.android.com / android.googlesource.com). Anything not confirmed on an official page is marked **(unverified)**.

Status: in progress (sections are appended as each topic is finished).

## 1. Accessibility services for sideloaded apps (Android 13–17)

### 1.1 Restricted settings (Android 13–14) and Enhanced Confirmation Mode (ECM, Android 15+)
- **What the user sees:** for a guarded app, the switch for its accessibility service (or notification listener) is greyed out. Tapping it opens a dialog titled **"Restricted setting"** with the text *"For your security, this setting is currently unavailable."* Strings: `enhanced_confirmation_dialog_title` / `_desc` in PermissionController ([strings.xml, android16-release](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/PermissionController/res/values/strings.xml)).
- **How to unlock:** Settings > Apps > (app) > ⋮ **More** > **Allow restricted settings**, then follow the prompts (Android 13+) ([Android Help 12623953](https://support.google.com/android/answer/12623953)). Google's warning there: allowing restricted settings gives the app access to sensitive info, so only do it for developers you trust.
- **Order matters:** the ⋮ menu item appears **only after** the user has tried to turn the setting on and seen the dialog. Showing the dialog calls `setClearRestrictionAllowed()` ([EnhancedConfirmationDialogActivity.kt](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/PermissionController/src/com/android/permissioncontroller/ecm/EnhancedConfirmationDialogActivity.kt)). Settings shows the item only when `isClearRestrictionAllowed()` is true (on 13–14: app-op `ACCESS_RESTRICTED_SETTINGS` == `MODE_IGNORED`). Tapping it requires the lock-screen credential (`showLockScreen`) and then shows a toast ([AppInfoDashboardFragment.java, android16-release](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android16-release/src/com/android/settings/applications/appinfo/AppInfoDashboardFragment.java)). **Onboarding must be: try to enable → dismiss dialog → App info → ⋮ → Allow restricted settings → PIN → enable again.**
- **Which installs are guarded** (`isPackageEcmGuarded`, [EnhancedConfirmationService.java android15/16/17-release](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android17-release/service/java/com/android/ecm/EnhancedConfirmationService.java)):
  - Never guarded: preinstalled/system apps, and packages or installers allow-listed by certificate in `/system/etc/sysconfig/enhanced-confirmation.xml`.
  - Always guarded: `InstallSourceInfo.getPackageSource()` is `PACKAGE_SOURCE_LOCAL_FILE` or `PACKAGE_SOURCE_DOWNLOADED_FILE`. That is the normal "APK from browser or file manager" path. The constants are documented in [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller) (API 33): *"comes from a file that was downloaded to the device by the user"* and *"a local file on the device"*.
  - Otherwise, guarded unless the immediate installer is preinstalled or allow-listed. If the XML lists **no** trusted installers, every installer counts as trusted. So third-party stores (Obtainium, F-Droid) are guarded on Android 15+ only when the device ships a trusted-installer list. `adb install` goes through shell, a system package, so it is not guarded.
  - ECM is off on TV (`FEATURE_LEANBACK`) and Automotive. Android 17 adds OEM config `config_enhancedConfirmationModeExemptSettings`, which can exempt individual settings, or all of them with `"*"` (android17-release source above).
- **Settings ECM protects per package** (15, 16 and 17 lists are identical): SMS permissions, `BIND_DEVICE_ADMIN`, app-ops `BIND_ACCESSIBILITY_SERVICE`, `ACCESS_NOTIFICATIONS` (notification listener), **`SYSTEM_ALERT_WINDOW`**, `GET_USAGE_STATS`, `LOADER_USAGE_STATS`, and the dialer/SMS roles (same source). Whether each Settings screen actually checks ECM for SAW or usage access is **(unverified)** for Googlebook.
- **Scam-call guard (16+, flag-gated):** during an ongoing call from an untrusted number, turning on a **non-tool** accessibility service is blocked with *"Can't complete action during call… Scammers may try to take control of your device by asking you to allow accessibility access for an app."* (`UNTRUSTED_CALL_RESTRICTED_SETTINGS`, same source + strings.xml). This has little effect on a laptop.

### 1.2 Android 17 changes: Advanced Protection Mode (AAPM) vs non-tool services
- User-facing: Advanced Protection *"Restricts accessibility services to verified accessibility tools"*, and *"will block the installation of apps from unknown sources and will block updates for apps originally installed from unknown sources"* ([Android Help 16339980](https://support.google.com/android/answer/16339980)). Dev page: AAPM launched in Android 16 and includes "Blocked app sideloading" ([Advanced Protection Mode](https://developer.android.com/privacy-and-security/advanced-protection-mode)).
- Mechanism (Android 17 source): when AAPM feature `FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES` is on, `AccessibilityManagerService` sets the global user restriction `DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICE`. It permits only packages that are **system or `isAccessibilityTool`, and that contain no non-tool service**, and shuts down already-enabled non-tool services ([AccessibilityManagerService.java, android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java), `handleAdvancedProtectionModeStateChanged`, `getPermittedServicesStrictApm`). Press reports quote the blocked-toggle text as "Restricted by Advanced Protection" **(unverified wording)**.
- **Result for BarBook:** with AAPM on, BarBook can't be sideloaded at all. Even a Play install cannot enable its service. Detect this with `AdvancedProtectionManager.isAdvancedProtectionEnabled()` plus `registerAdvancedProtectionCallback()`, which need `<uses-permission android:name="android.permission.QUERY_ADVANCED_PROTECTION_MODE"/>` (API 36, [dev page](https://developer.android.com/privacy-and-security/advanced-protection-mode), [reference](https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager)). Degrade to Quick Settings tiles and notifications only.
- There are no other Android 17 accessibility or ECM behaviour changes on the 17 pages, apart from text-change types for CJKV IMEs ([behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17)).

### 1.3 `android:isAccessibilityTool`
- `R.attr.isAccessibilityTool` (API 31): *"whether the accessibility service is used to assist users with disabilities. This criteria might be defined by the installer. The default is false. Note: If this flag is false, system will show a notification after a duration to inform the user about the privacy implications of the service."* ([R.attr](https://developer.android.com/reference/android/R.attr#isAccessibilityTool)).
- **BarBook must NOT set it.** Play allows it only for apps whose primary purpose is supporting people with disabilities (screen readers, switch, voice or Braille access). It explicitly lists *"automation tools, assistants, … launchers"* as not accessibility tools ([Play: Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491)).

### 1.4 Google Play AccessibilityService policy (for a later Play release)
Sources: [Play Help 10964491](https://support.google.com/googleplay/android-developer/answer/10964491) and [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170).
- **The API may not be used to:** change user settings without permission, or stop users from disabling or uninstalling apps; *"work around Android built-in platform security controls, privacy controls and notifications"*; *"change or leverage the user interface in a way that is deceptive"*. It also may not be used for remote call-audio recording or for apps that *"autonomously initiate, plan, and execute actions"*. Deterministic rule-based automation is allowed.
- The Play listing must document the use. Apps *"must use more narrowly scoped APIs and permissions in lieu of the Accessibility API when possible."* BarBook's argument: nothing else can draw in the status-bar band, because `TYPE_APPLICATION_OVERLAY` sits *"below critical system windows like the status bar"* ([WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)).
- **Prominent disclosure** (required for non-tools). It must be:
  - in the app itself, and shown in normal use rather than hidden in settings
  - describe the data accessed through the API and how it is used or shared
  - require affirmative consent (tap to accept or tick a box)
  - not only in a privacy policy or ToS
  - not bundled with other disclosures
  - never replaced by the service's `android:description`/`htmlDescription`.
- **Declaration form** (Play Console > App content): why the app needs the API (e.g. "App functionality"); whether personal or sensitive data is collected or shared via the API (Yes/No, then data types); a **video link** showing app open → disclosure (all text readable) → consent and grant → decline and re-trigger → a core feature that uses the API. The form must be resubmitted whenever usage changes.

### 1.5 Disclosure copy best practices ([Play Help 11150561](https://support.google.com/googleplay/android-developer/answer/11150561))
- Show it right before sending the user to Settings, at the point where you explain the grant steps.
- Give two choices: consent, and decline with a way to grant later ("Not now"). Degrade gracefully if the user declines.
- Use "Agree", not "Allow access" or "Got it". The prompt must not look like Android system UI; use app colours.
- Content: **Why** comes first, then **What** data and **How** it is used. Clarity beats brevity; write at the reading level of a 13-year-old. Mind consent fatigue.
- Suggested BarBook substance: it reads the layout of the system status bar and window list only to position its own items; it does not read or store other apps' content; nothing leaves the device.

### 1.6 Overlay z-order and window APIs
- `TYPE_ACCESSIBILITY_OVERLAY` (2032) is *"overlaid only by a connected AccessibilityService for interception of user interactions without changing the windows an accessibility service can introspect"* ([LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)). A touchable overlay therefore does not hide the windows beneath it from `getWindows()`.
- **Z-order** (`getWindowLayerFromTypeLw`, [WindowManagerPolicy.java android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/policy/WindowManagerPolicy.java)), bottom to top:

  | Window type | Layer |
  |---|---|
  | `APPLICATION_OVERLAY` | 11 |
  | IME | 13 |
  | `STATUS_BAR` | 15 |
  | `NOTIFICATION_SHADE` | 17 |
  | `VOLUME_OVERLAY` | 22 |
  | `NAVIGATION_BAR` | 24 |
  | `SCREENSHOT` | 26 |
  | `DRAG` | 30 |
  | **`ACCESSIBILITY_OVERLAY`** | **31** |
  | `SECURE_SYSTEM_OVERLAY` | 33 |
  | `POINTER` | 35 |

  So BarBook items draw **above the status bar and above the notification shade, Quick Settings and the lock screen**. BarBook must hide them itself when those surfaces open.
- API 34 adds `attachAccessibilityOverlayToDisplay(displayId, SurfaceControl)` (via `SurfaceControlViewHost`, ordered with `Transaction.setLayer`) and `attachAccessibilityOverlayToWindow(...)` ([AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)). Use them for multi-display, or to tie an item to a window.
- `getWindows()` returns only interactive windows on the default display, top-most first. `getWindowsOnAllDisplays()` (API 30) covers every display. Both need `canRetrieveWindowContent="true"` **and** `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`; without the flag the list is empty, `TYPE_WINDOWS_CHANGED` is not delivered and `AccessibilityNodeInfo.getWindow()` returns null ([AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS)).
- `AccessibilityWindowInfo` types: `TYPE_SYSTEM` (status bar and taskbar will appear as this), `TYPE_ACCESSIBILITY_OVERLAY`, and `TYPE_WINDOW_CONTROL` (API 36, a system window that controls another window, e.g. desktop captions) ([AccessibilityWindowInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo)).
- The dev guide warns that `typeAllMask` *"can be resource intensive"*. Subscribe to the minimum event types ([Create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service)).

### 1.7 "App is using accessibility" reminders
- **With Safety Center enabled** (the Googlebook has it, per the earlier device probe), the framework's own reminder is switched off: `sendNotification = !isSafetyCenterEnabled()` ([AccessibilityManagerService.java, android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java)). PermissionController's `AccessibilitySourceService` takes over ([source, android16-release](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-release/PermissionController/src/com/android/permissioncontroller/privacysources/AccessibilitySourceService.kt)):
  - It runs a periodic job every **1 day** by default (DeviceConfig `sc_accessibility_job_interval_millis`, flex 10%).
  - For enabled **non-tool** services it posts **one notification per service**, titled *"Review app with full device access"* with the text *"<App> can view your screen and perform actions on your device. Accessibility apps need this type of access to function as intended."* The notification has a **Remove access** action.
  - Notifications are at least about 0.8 days apart, and only one shows at a time.
  - The service is then marked "notified". If it is **disabled and re-enabled**, it drops off that list and gets reminded again.
  - Safety Center also keeps an **information-level issue card** for each enabled non-tool service.
- **Without Safety Center:** `PolicyWarningUIController` sets a one-shot alarm **24 h after binding** (`SEND_NOTIFICATION_DELAY_HOURS = 24`). Once the notification is dismissed or tapped, the service is remembered in `Settings.Secure.NOTIFIED_NON_ACCESSIBILITY_CATEGORY_SERVICES` and not notified again ([PolicyWarningUIController.java, android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/PolicyWarningUIController.java)).
- Onboarding copy should warn the user about this one-time reminder so it doesn't alarm them.

## 2. Live Updates (promoted ongoing notifications, 16+) and Android 17 `MetricStyle`

**Eligibility.** All requirements below are from the [Live Updates guide](https://developer.android.com/develop/ui/views/notifications/live-update):
- The style is standard, `BigTextStyle`, `CallStyle`, `ProgressStyle` or `MetricStyle`.
- The manifest declares `POST_PROMOTED_NOTIFICATIONS`. Its protection level is `normal|appops`, it was added in 36.1, and it is needed *in addition to* `POST_NOTIFICATIONS` ([Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#POST_PROMOTED_NOTIFICATIONS)).
- The notification requests promotion with `setRequestPromotedOngoing(true)` / `EXTRA_REQUEST_PROMOTED_ONGOING` (36.1).
- It is ongoing, with `FLAG_ONGOING_EVENT` set.
- It has a `contentTitle`.
- It has no custom `RemoteViews`, is not a group summary, and is not `setColorized(true)`.
- Its channel is not `IMPORTANCE_MIN`.
- *"OEMs can enforce additional criteria."*

**Checks.**
- `Notification.hasPromotableCharacteristics()`: if it returns false, the notification is never promoted; if true, promotion is still not guaranteed, and user settings are ignored ([Notification](https://developer.android.com/reference/android/app/Notification#hasPromotableCharacteristics())).
- `FLAG_PROMOTED_ONGOING` is set by the system when the notification is actually promoted.
- `NotificationManager.canPostPromotedNotifications()` reflects the user toggle ([NotificationManager](https://developer.android.com/reference/android/app/NotificationManager#canPostPromotedNotifications())).
- To send the user to the toggle, use `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`, API 36) with `EXTRA_APP_PACKAGE`. The reference warns *"a matching Activity may not exist"* ([Settings](https://developer.android.com/reference/android/provider/Settings#ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)). The guide's name `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` is not in the API reference; use the reference name.

**What the chip shows** ([`setShortCriticalText`](https://developer.android.com/reference/android/app/Notification.Builder#setShortCriticalText(java.lang.String)), API 36, plus the guide):
- **Priority 1:** `shortCriticalText`. Suggested max 7 characters. `""` forces an icon-only chip.
- **Priority 2:** the `MetricStyle` critical metric.
- **Priority 3:** `when`. The chronometer shows if `setUsesChronometer` is true and the value is positive. With `setShowWhen`, the time remaining until `when` shows if positive; a `when` at least 2 min ahead renders as "5min".
- The chip always has the small icon. It is at most **96 dp** wide.
- Text under 7 characters shows in full. If less than half the text fits, the chip is icon-only; otherwise it shows as much text as fits.
- Users can demote or dismiss the notification. Don't repost after the user dismisses it; use `setDeleteIntent`.
- There is no desktop or large-screen chip documentation. How Googlebook's desktop status bar renders chips is **(unverified)**.

**`Notification.MetricStyle` (API 37)** ([MetricStyle](https://developer.android.com/reference/android/app/Notification.MetricStyle), [Metric](https://developer.android.com/reference/android/app/Notification.Metric)):
- Shows *"up to 3 metrics when expanded"*. It needs at least one `Metric`, or `build()` rejects it. It doesn't show the large icon.
- *"If … promoted ongoing, then one of its metrics might be displayed in the status bar chip."* `setCriticalMetric(index)` picks that metric. The default is the first metric; `METRIC_INDEX_NONE` = -1 picks none.
- Constructor: `Metric(MetricValue value, CharSequence label[, int semanticStyle])`. Keep labels to 10 characters or fewer.
- `MetricValue` subclasses:

  | Class | Form | Notes |
  |---|---|---|
  | `FixedText` | `(text[, unit])` | |
  | `FixedInt` | `(int[, unit])` | |
  | `FixedFloat` | `(float[, unit[, minFrac, maxFrac]])` | 0–2 fraction digits by default |
  | `FixedDate` | `(LocalDate[, FORMAT_AUTOMATIC / SHORT_DATE / LONG_DATE])` | |
  | `FixedTime` | `(LocalTime)` | hours:minutes in the user's 12/24 h format |
  | `TimeDifference` | `forTimer(endTime, fmt)`, `forStopwatch(startTime, fmt)` | `Instant` or `elapsedRealtime` long; also `forPausedTimer` / `forPausedStopwatch(Duration, fmt)`; formats `FORMAT_ADAPTIVE` ("1h 5m") and `FORMAT_CHRONOMETER` ("2:00:00") |

- **Semantic colours (API 37):**
  - `Notification.SEMANTIC_STYLE_UNSPECIFIED/INFO/SAFE/CAUTION/DANGER` apply to `Metric`, `ProgressStyle.Point` and `ProgressStyle.Segment`.
  - For text, use spans from `Notification.createSemanticStyleAnnotation(style)`.
  - The colours map to green = safe, orange = caution, red = danger, blue = info ([17 features](https://developer.android.com/about/versions/17/features)).
  - They apply *"when the notification is promoted"*.

**Which use cases qualify** (guide). Use Live Updates for activities that are **ongoing, user-initiated and time-sensitive**, e.g. navigation, calls, rideshare, delivery.
- Explicitly **inappropriate:** *"Ads, promotions, chat messages, alerts, upcoming calendar events, and quick access to app features"*.
- Also: *"Don't show ambient information, such as … the user's environment, interests, or upcoming events"*. For quick access, the guide says to use a widget or a **custom Quick Settings tile**.
- The Settings reference repeats that promotion is *"reserved for user initiated ongoing activities like navigation, phone calls, and ride sharing"*.
- **For BarBook:** a permanent CPU, network or battery meter as a Live Update goes against the guidance. Good fits are user-started timers, stopwatches, focus sessions and running transfers. No Play policy text specific to Live Updates was found **(unverified)**.

## 3. Android 17 `StatusBarManager` agent-task API (`android.agenticon`)

All from [StatusBarManager](https://developer.android.com/reference/android/app/StatusBarManager) and the [android.agenticon package](https://developer.android.com/reference/android/agenticon/package-summary).
- **Added in "version 37.2".** That is the Android 17 minor SDK that ships with QPR2, still in beta as of 2026-09 ([QPR2 release notes](https://developer.android.com/about/versions/17/qpr2/release-notes): *"Android 17 QPR2 includes a minor SDK release"*). The earlier device probe reported **SDK 37.1** on the Googlebook, so these APIs are probably **absent on the device today**. Gate on `Build.VERSION.SDK_INT_FULL` ([Build.VERSION](https://developer.android.com/reference/android/os/Build.VERSION#SDK_INT_FULL)).
- **Who may call it.** `canSetAgentTask()` returns true only if **all** of these hold:
  1. the caller holds **`RoleManager.ROLE_ASSISTANT`**;
  2. it has an enabled activity that filters `ACTION_AGENT_TASK_MAIN` (`"android.app.action.AGENT_TASK_MAIN"`). That activity's icon becomes the default, non-animated state; `setAgentTask(null, …)` returns to it;
  3. *"the corresponding user setting is enabled"*.
- **Device support.** `isAgentTaskFeatureSupported()` reports device support; devices with the PersonalContextManager service must support it. `isAgentTaskLaunchSupported()`: when false, click `PendingIntent`s are ignored.
- **Calls.**
  - `setAgentTask(AgentTaskUpdate, Executor, OutcomeReceiver<AgentTaskOutcome, Throwable>)` is best-effort; the last request wins; current user only. To pair it with a notification, set `Notification.Builder.setAgentInteractionFlags(FLAG_AGENT_TASK_INTERACTION_HIDE_STATUS_BAR_ICON | …_HIDE_VISUAL_ALERTS)`.
  - `getAgentStateScreenLocation(displayId)` returns the icon's on-screen `Rect`. It can take seconds, so call it off the main thread.
- **What the status bar shows.**
  - **`AgentTaskState`:** a status-bar **icon** with an optional loop or interrupting animation, a content description, and a click `PendingIntent` that opens the agent app.
  - **`AgentTaskEvent`:** a short-lived pill of leading icons + text + trailing icons, with a background and a click action. It may cover the state icon. It may be dropped when events are too frequent, the status bar is hidden, or more critical information needs the space.
  - **`AgentTaskUpdate`:** carries the state and/or the event.
  - **`AgentTaskOutcome`:** reports `isStateChanged()` and `isEventShown()`.
- **Related 37 API, `StatusBarManager.showPowerMenu(Executor, OutcomeReceiver<Integer,Throwable>)`.** It needs `SHOW_POWER_MENU`, which is *"granted to the current holder of the ASSISTANT role"*, or `SHOW_POWER_MENU_PRIVILEGED` ([Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#SHOW_POWER_MENU)). BarBook can reach the same dialog with its accessibility service instead: `performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)`.
- **Can BarBook use the agent-task API?** Only by becoming the default digital assistant. ROLE_ASSISTANT qualifies any app with an **exported `ACTION_ASSIST` activity** or a qualifying `VoiceInteractionService` ([AssistantRoleBehavior.java, android17-release](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android17-release/PermissionController/role-controller/java/com/android/role/controller/behavior/AssistantRoleBehavior.java)). The user would have to pick BarBook as "Digital assistant app", displacing Gemini, and it would get **one** icon. That is a poor trade and against the API's intent. Treat the API as not applicable.

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
  - So BarBook should **not** request the accessibility button.

## 5. Special app access a user can grant in Settings (no adb)

| Access | How to request / check | ECM "restricted"? |
|---|---|---|
| **WRITE_SETTINGS** (`Settings.System`, e.g. brightness, screen timeout) | Declare `WRITE_SETTINGS`. Send the user to `Settings.ACTION_MANAGE_WRITE_SETTINGS` (`"android.settings.action.MANAGE_WRITE_SETTINGS"`, API 23) with data `package:<pkg>`. Check with `Settings.System.canWrite()`. | No |
| **Do Not Disturb / Modes access** | `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` (API 23; *"Managed profiles cannot grant"*). Check with `NotificationManager.isNotificationPolicyAccessGranted()`. | No |
| **Notification listener** | `ACTION_NOTIFICATION_LISTENER_SETTINGS` for the list, or `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` (API 30) + `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` for the per-app page. Check with `isNotificationListenerAccessGranted(cn)`. | **Yes** (`OPSTR_ACCESS_NOTIFICATIONS`). One "Allow restricted settings" unlock is **per package**, so it also covers the accessibility service (`clearRestriction` sets the whole app NOT_GUARDED). |
| **SCHEDULE_EXACT_ALARM** | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (API 31) with `package:` data. The result is `RESULT_OK` if granted. Check with `AlarmManager.canScheduleExactAlarms()`. Since Android 14 it is *"denied by default"* for new installs targeting 33+ ([14 changes](https://developer.android.com/about/versions/14/behavior-changes-all)). BarBook doesn't need it; a clock UI can use `ACTION_TIME_TICK`/`Handler`. | No |
| **READ_CALENDAR** | Ordinary runtime permission (`requestPermissions`). | No |
| **`MediaSessionManager.getActiveSessions(cn)`** | Requires `MEDIA_CONTENT_CONTROL` (system only) **or** being an enabled notification listener and passing its `ComponentName` ([MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName))). Pair it with `addOnActiveSessionsChangedListener`. | Via the listener: yes |
| **`AudioManager.dispatchMediaKeyEvent(KeyEvent)`** | No permission. Send DOWN then UP; the event goes to the current media-button consumer ([AudioManager](https://developer.android.com/reference/android/media/AudioManager#dispatchMediaKeyEvent(android.view.KeyEvent))). | No |
| Accessibility service | `Settings.ACTION_ACCESSIBILITY_SETTINGS` is the public API. A per-service `"android.settings.ACCESSIBILITY_DETAILS_SETTINGS"` + `Intent.EXTRA_COMPONENT_NAME` is used by the system itself ([PolicyWarningUIController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/PolicyWarningUIController.java)) but isn't in the public reference **(unverified for apps)**. | **Yes** |
| Promoted notifications | `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` + `EXTRA_APP_PACKAGE` (§2). | No |

All intent docs: [Settings](https://developer.android.com/reference/android/provider/Settings). Every one says *"a matching Activity may not exist"*, so wrap each in `try/catch ActivityNotFoundException`. ECM's list also includes SYSTEM_ALERT_WINDOW and usage access (§1.1).

**Android 15 DND change** ([behavior-changes-15](https://developer.android.com/about/versions/15/behavior-changes-15)): apps targeting 35+ *"can no longer change the global state or policy of Do Not Disturb"*.
- Calls to `setInterruptionFilter`/`setNotificationPolicy` now *"result in the creation or update of an implicit AutomaticZenRule"*. The system merges it under most-restrictive-wins.
- Consequence: a BarBook "DND" toggle can turn *its own* mode on and off, but `INTERRUPTION_FILTER_ALL` cannot switch off DND that the user or another app started.

**Android 17 background audio hardening** ([bg-audio](https://developer.android.com/about/versions/17/changes/bg-audio)): this matters for volume and mute items.
- `setStreamVolume`, `adjustStreamVolume`, `adjustVolume`, `adjustSuggestedStreamVolume`, `setStreamMute` and `setRingerMode` are **silently ignored**. This applies to all apps unless they have a visible activity or a non-`SHORT_SERVICE` FGS. Apps targeting 37 also need a **while-in-use** FGS when in the background.
- Enforcement uses app-ops `CONTROL_AUDIO_PARTIAL` and `CONTROL_AUDIO`, both `MODE_FOREGROUND`. The latter needs `PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL` ([HardeningEnforcer.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/audio/HardeningEnforcer.java), [AppOpsUidStateTrackerImpl.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/appop/AppOpsUidStateTrackerImpl.java)).
- The system binds accessibility services with `BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_INCLUDE_CAPABILITIES` (§6), so volume calls **probably work while the screen is on (unverified)**.
- Test with `adb shell cmd audio set-enable-hardening throw`, and look for `AudioHardening` in `dumpsys audio`.
- Android 15 also redacts OTP notifications for untrusted notification listeners ([15 all-apps](https://developer.android.com/about/versions/15/behavior-changes-all)).

## 6. Background execution for an accessibility-service app (14–17)

**No foreground service is needed.** While the user keeps the service enabled, the system itself binds it with `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS | BIND_INCLUDE_CAPABILITIES`. It also calls `setAllowAppSwitches` for the service's uid ([AccessibilityServiceConnection.java, android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java)). In practice:
- **Priority:** while the device is awake, the process is treated like a bound foreground service and inherits system_server's capabilities. When the device is asleep it drops to a normal bound service. The exact oom_adj value is **(unverified)**.
- **Activity launches:** it may start activities from the background (popovers, settings). The [BAL page](https://developer.android.com/guide/components/activities/background-starts) lists *"The app is bound by a service that has been granted permission to start background activities"* and *"has a visible window"* as exceptions.
- **Process death:** if the process dies, the connection is marked `crashed` and `mCrashedServices` is skipped on rebinds ([AccessibilityManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java)). **A crash can leave BarBook dead until re-enabled or updated, so keep the service process crash-proof** (the exact UX is **(unverified)**).
- **Android 17 limits:** memory limits apply to all apps. A kill shows as `REASON_OTHER` with description `"MemoryLimiter:AnonSwap"` ([17 all-apps](https://developer.android.com/about/versions/17/behavior-changes-all)). Android 17 also kills apps for *"abnormal and excessive CPU usage"*; `ProfilingManager` `TRIGGER_TYPE_KILL_EXCESSIVE_CPU_USAGE` reports it ([17 features](https://developer.android.com/about/versions/17/features)).
- **Standby buckets and Doze:** no developer page says whether they spare an accessibility-bound process **(unverified)**. Avoid `AlarmManager`/`JobScheduler` in the tick path.

**Ticks and battery.** There is no accessibility-specific guidance, so this follows from the platform docs:
- **Don't** drive a clock with `Choreographer.postFrameCallback`; every callback asks for a vsync frame. Use `Handler.postAtTime()` aligned to the next minute (or second) boundary. Better, use broadcasts: `ACTION_TIME_TICK` (per minute, runtime-registered only), `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`.
- Each redraw of an overlay window triggers composition. Batch updates, and invalidate only the views whose text changed.
- The closest official numbers are for always-visible surfaces: Wear watch faces should use about **15 fps** for animations and treat **≥90 s CPU/hour** as excessive ([Excessive battery usage](https://developer.android.com/topic/performance/vitals/excessive-battery-usage)).
- `PowerManager` ([reference](https://developer.android.com/reference/android/os/PowerManager)):
  - `isInteractive()` is false when *"dozing or asleep"*, and each change is announced by `ACTION_SCREEN_ON`/`OFF`, which *"refer to … the overall interactive state"*. *"Services may use the non-interactive state as a hint to conserve power."* **Stop all polling when non-interactive.**
  - `isPowerSaveMode()`: *"applications should reduce their functionality"*. Watch `ACTION_POWER_SAVE_MODE_CHANGED` and slow meters, e.g. network or CPU from 1 s to 5 s.
- Also pause when BarBook's overlay is hidden (fullscreen app, shade open, lock screen), and when the status-bar window is absent from `getWindows()`.

## 7. Desktop windowing (16–17): status bar, taskbar, input conventions

- **No developer or AOSP page documents the desktop status bar** (layout, clock position, notification indicator, chips). What exists covers only these:
  - The **taskbar** at the bottom: pinned and running apps; right-click an icon for pin, new window, close and app shortcuts.
  - The **header bar** on freeform windows (minimize, maximize, close; customisable insets via `APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND` and `WindowInsets.isCaptionBarVisible`).
  - Sources: [Desktop system bars](https://developer.android.com/design/ui/desktop/guides/system/system-bars), [Support desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing).
  - Treat Googlebook's status-bar layout as OEM SystemUI that can change with any update **(unverified in docs)**, and discover it at runtime from the accessibility tree.
- **Desktop-first vs touch-first** is a per-display mode ([AOSP Desktop windowing](https://source.android.com/docs/core/display/desktop-windowing)):
  - A display is desktop-first when a keyboard **and** a touchpad or mouse are connected. External displays usually default to desktop-first.
  - Convertibles can switch on posture ("keyboard flipped back" means touch-first).
  - Desktop-first always uses the **Desktop Taskbar**; touch-first uses the transient taskbar.
  - **BarBook must re-layout when the mode, display or posture changes.** Each connected display can host its own bars; use `getWindowsOnAllDisplays()` and `attachAccessibilityOverlayToDisplay()`.
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

