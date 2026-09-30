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
| Permissions | Accessibility service (declaration needed, below), notifications, calendar (read), alarms and reminders (`SCHEDULE_EXACT_ALARM`, user-granted; the restricted one is `USE_EXACT_ALARM`, which BentoBar doesn't use), network state, launcher-app queries (`<queries>` for MAIN/LAUNCHER, not `QUERY_ALL_PACKAGES`), Advanced Protection status. |

## Declarations that need more than a tick

- **Accessibility API** (Policy › App content › Sensitive permissions and APIs, appears after the first bundle is uploaded). BentoBar is not an accessibility tool (`isAccessibilityTool` false). Core feature: it draws the user's own items on the status bar, which Android allows only through an accessibility service, and runs system actions (screenshot, lock, overview, all apps, power) from its Tools menu. It observes only window changes (not content) and the status bar's own layout, and doesn't collect or share any data. Needs: a short **video** (YouTube, unlisted is fine) showing Setup › Turn on with BentoBar's explanation, the consent in Accessibility settings, and the items appearing in the status bar. Google also requires the in-app disclosure before the user turns it on (Setup already explains it; check it names the uses in the words above).
