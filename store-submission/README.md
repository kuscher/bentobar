# Google Play submission kit

Everything the Play Console asks for when publishing BentoBar, ready to copy or upload (made on 30 September 2026,
following Summa's kit). The app exists in the Play Console as a draft: io.github.kuscher.bentobar, app id 4972860541590198696, developer
account Fika Labs (7424304467248438473).

## What's here

| Play Console field | File | Limit / spec |
|---|---|---|
| App name | [listing/en-US/title.txt](listing/en-US/title.txt) | 30 characters |
| Short description | [listing/en-US/short-description.txt](listing/en-US/short-description.txt) | 80 characters |
| Full description | [listing/en-US/full-description.txt](listing/en-US/full-description.txt) | 4,000 characters |
| Release notes ("What's new") | [listing/en-US/release-notes.txt](listing/en-US/release-notes.txt) | 500 characters |
| App icon | [graphics/icon-512.png](graphics/icon-512.png) | 512 × 512 PNG, full square (Play rounds the corners), drawn from the launcher icon's layers |
| Feature graphic | [graphics/feature-graphic.png](graphics/feature-graphic.png) | 1024 × 500, 24-bit PNG |
| Screenshots | [graphics/large-screen/](graphics/large-screen) (4) | 1920 × 1080 (16:9), 24-bit PNG. Used for phone, 7-inch, 10-inch and Chromebook |
| Store settings, contact, category | [forms/store-settings.md](forms/store-settings.md) | |
| Privacy policy | https://googlebook.studio/privacy/bentobar | public, outside googlebook.studio's invite gate |
| Data safety | [forms/data-safety.md](forms/data-safety.md) | "No data collected" up to 0.8; from 0.9 three data types are collected (none shared), see the file |
| Content rating (IARC) | [forms/content-rating.md](forms/content-rating.md) | expected: Everyone / PEGI 3 |
| Other App content declarations | [forms/app-content.md](forms/app-content.md) | |

The listing text avoids what Play's metadata policy rules out: rankings or superlatives, promotional words, testimonials,
emoji, calls to action and other companies' app names. The screenshots are BentoBar's own README images with a caption,
made with `scripts/play/graphics.mjs` in kuscher/googlebook-tech.

## Steps

1. **App signing (decide once, it can't be undone).** Recommended, as for Summa: *Use existing app signing key* and upload
   `~/.config/bentobar/keystore.jks` with Google's PEPK tool, so the Play build and the APKs on GitHub have the same signature and people can
   move between them without uninstalling. The same key is the upload key.
2. **Build the bundle** (Play only takes .aab files): pushing a release tag does it (see [docs/RELEASING.md](../docs/RELEASING.md)); GitHub Actions
   builds the bundle and signs it with the same key as the GitHub APK. By hand, on a machine that has the key:
   `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`. Each upload needs a higher version code than the last
   (`versionCode` in `app/build.gradle.kts` (6 for 0.5)).
3. **Closed test first.** The developer account is a personal one: before production, a closed test with at least 12
   testers opted in for 14 days in a row.
4. **Store listing, store settings and App content:** filled in from these files on 30 September 2026.
5. **Release:** the release tag puts the bundle on the closed testing track as a draft, with `release-notes.txt` as "What's new"
   (update that file before tagging; 500 characters at most). Then press "Send for review" in the Play Console: nothing is
   sent for review automatically.
