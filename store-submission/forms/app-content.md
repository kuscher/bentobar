# App content declarations (Play Console › Policy › App content)

| Declaration | Answer |
|---|---|
| Privacy policy | https://googlebook.studio/privacy/bentobar |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access (no login) |
| Content rating | See `content-rating.md` |
| Target audience | 13 and over (13–15, 16–17, 18+). Not designed for children, so the Families policy doesn't apply |
| Data safety | See `data-safety.md` |
| News app | No |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | No health features |
| Advertising ID | Not used (no ad or analytics SDKs; `AD_ID` isn't declared) |
| Permissions | Accessibility service (declaration needed, below), notifications, calendar (read), alarms and reminders (`SCHEDULE_EXACT_ALARM`, user-granted; the restricted one is `USE_EXACT_ALARM`, which BentoBar doesn't use), Usage access (`PACKAGE_USAGE_STATS`, optional and off by default: per-app data use and storage for the Network and Storage menus, read on the device only), wake lock (`WAKE_LOCK`, since 0.7: Keep awake while the bar is hidden), internet (since 0.9: Weather asks Open-Meteo and Flight asks AirLabs, only after the user set the item up; see `data-safety.md`), approximate location (`ACCESS_COARSE_LOCATION`, since 1.3, optional: Weather's My location, asked for only when an item chooses it; no precise location and no `ACCESS_BACKGROUND_LOCATION`: on a Googlebook, Android gives the bar's accessibility service the location under the "while using the app" grant, and whether Play asks about background location is decided with the 1.3 submission; see `data-safety.md`), notification access (since 0.9, optional: Now playing's title, artist and artwork; the listener declares that it receives no notifications), network state, launcher-app queries (`<queries>` for MAIN/LAUNCHER, not `QUERY_ALL_PACKAGES`), Advanced Protection status. |

## Declarations that need more than a tick

- **Accessibility API** (Policy › App content › Sensitive permissions and APIs, appears after the first bundle is uploaded). BentoBar is not an accessibility tool (`isAccessibilityTool` false). Core feature: it draws the user's own items on the status bar, which Android allows only through an accessibility service, and runs system actions (screenshot, lock, overview, all apps, notifications, Quick Settings, power) from its Tools menu or a Shortcut item the user adds. It observes only window changes (not content) and the status bar's own layout, and doesn't collect or share any data. Needs: a short **video** (YouTube, unlisted is fine) showing BentoBar's own disclosure with its two buttons (it comes up when the app is first opened, and from Setup › Turn on), Agree, the switch in Accessibility settings, and the items appearing in the status bar. Google requires that disclosure and consent in the app, before the user is sent to turn the service on: a page of explanation with one Turn on button was rejected ("Prominent disclosure: non-compliant design": the consent needs two buttons, agree and decline; a switch or a single button doesn't count, and neither does closing the dialog). `ui/Disclosure.kt` is the screen; a new video is needed whenever it changes.
- **Done 2026-09-30** (saved in Play Console, not sent for review): Accessibility API with App functionality and no data collected. The video is unlisted on YouTube; link in Play Console and in kuscher/googlebook-tech `docs/PICKING-UP.md`.
