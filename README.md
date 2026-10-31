# The Daily Brief — Android

A stand-alone Android local-news brief that favors nonprofit and public-service journalism, builds a compact edition on-device, and requires no Daily Brief account or hosted backend.

## Download

For testers and normal users, use the **Releases** page rather than downloading source code. APK releases will be attached there.

> Android may warn that the APK came from outside Google Play. That is expected for a directly distributed build.

## Current version: v0.4.1

- Replaced the fixed 10-story edition with an **editorial quality threshold**.
- Added a 25-story emergency cap only as a safety valve.
- Added **near-duplicate clustering** so multiple outlets covering the same event do not waste slots. Same-publisher stories are only deduplicated when their title or URL actually matches.
- Strongly favors preferred nonprofit/community sources, then other INN sources, then useful direct local reporting, with Google/commercial coverage used as gap-fillers.
- Adds diminishing-return penalties when one publisher or topic starts dominating the issue; these penalties affect ordering, not whether an otherwise qualified story is included.
- Scores stories for locality, freshness, source quality, and public-interest value.
- Organizes the edition as **Top Stories / Local / Michigan / More to Know**.
- Adds a real **build progress bar** and status messages while sources are checked and stories are filtered.
- Improves dark-mode Settings contrast with clearer panels, headings, controls, and selected states.
- Keeps **Appearance: System / Light / Dark**.
- Existing installs migrate automatically; users do not have to wipe setup data or re-enter their ZIP code.

## How source selection works

### During setup

1. Resolve ZIP → city/state/county/coordinates/time zone.
2. Add known preferred public-service feeds for the area.
3. Query the Institute for Nonprofit News / Find Your News directory by place.
4. Use Google News local/community/nonprofit queries to discover additional publishers.
5. Probe publisher sites for direct RSS/Atom feeds.
6. Let the user enable/disable sources or add a feed manually.

### Every morning

The app gathers a larger candidate pool, filters unwanted categories, clusters duplicate/same-event coverage, scores each story for intrinsic editorial quality, keeps stories that clear that quality bar, and then uses source/topic repetition penalties only to order the qualified stories more diversely.

Preferred nonprofit/community reporting gets the strongest source bonus. Commercial and Google-sourced stories are not guaranteed slots; they are used when they add genuinely useful coverage.

Sports, obituaries, opinion/letters, and recipes are excluded by default and can be enabled in Settings.

## Privacy

ZIP code, source choices, appearance, cached stories, and cached weather are stored locally on the device.

Network requests go directly to:

- Open-Meteo for ZIP lookup and weather
- FindYourNews.org during nonprofit-source discovery
- Google News during source discovery or fallback
- selected publisher feeds

No Daily Brief server is required.

## Build

### Android Studio

Open the project and build the debug APK.

Output:

`app/build/outputs/apk/debug/app-debug.apk`

### CachyOS / terminal

The included Fish script builds and installs locally:

`fish build-and-install.fish`

If ADB sees a connected phone, the script installs the resulting APK. Otherwise it prints the APK path.

### GitHub Actions

`.github/workflows/build-apk.yml` builds the newest source bundle under `source/` and uploads a debug APK as a workflow artifact.

**Distribution releases should use the same persistent signing identity across versions** so Android can install later releases as updates.

## Build target

- minSdk 26 (Android 8)
- targetSdk 35
- compileSdk 35
- Java 17
- no AndroidX/runtime third-party libraries
