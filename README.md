# The Daily Brief — Android

A stand-alone Android version of **The Morning Brief**: local weather and a compact local-news edition, built directly on the phone with no Morning Brief account or hosted backend required.

## Download

For testers and normal users, use the **Releases** page rather than downloading source code. APK releases will be attached there.

> Android may warn that the APK came from outside Google Play. That is expected for a directly distributed build.

## Current version: v0.3.0

- Added **Appearance: System / Light / Dark**.
- Reworked edition selection to be **nonprofit/public-service first**.
- Preferred sources can contribute up to four stories each before commercial/direct or Google fallbacks are used.
- INN / Find Your News sources get the next-highest priority.
- Google News is a **fill-the-gaps fallback**.
- West Michigan installs automatically get direct-feed fast paths for:
  - Bridge Michigan
  - The Rapidian
  - Ottawa News Network
- Existing installs migrate automatically; users do not have to wipe setup data or re-enter their ZIP code.
- Preferred direct feeds self-repair; if a publisher feed fails repeatedly, the app falls back to a source-filtered Google News feed for **that same publisher**.
- Editions are capped at **10 stories** and balanced within each source tier.
- Story cache remembers source type so quiet mornings retain the nonprofit preference.

## How source selection works

### During setup

1. Resolve ZIP → city/state/county/coordinates/time zone.
2. Add known preferred public-service feeds for the area.
3. Query the Institute for Nonprofit News / Find Your News directory by place.
4. Use Google News local/community/nonprofit queries to discover additional publishers.
5. Probe publisher sites for direct RSS/Atom feeds.
6. Let the user enable/disable sources or add a feed manually.

### Every morning

The app gathers recent stories from enabled sources, filters unwanted categories, deduplicates them, then fills the edition in this order:

1. Preferred nonprofit/community sources
2. Other INN nonprofit sources
3. Other direct publisher feeds
4. Google News / commercial fallback

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

Open the repository as an Android Studio project and build the debug APK.

Output:

`app/build/outputs/apk/debug/app-debug.apk`

### CachyOS / terminal

The included Fish script uses an installed or cached Gradle distribution:

`fish build-and-install.fish`

If ADB sees a connected phone, the script installs the resulting APK. Otherwise it prints the APK path.

### GitHub Actions

`.github/workflows/build-apk.yml` builds a debug APK as a workflow artifact for development/testing.

**Distribution releases should use the same persistent signing identity across versions** so Android can install later releases as updates.

## Build target

- minSdk 26 (Android 8)
- targetSdk 35
- compileSdk 35
- Java 17
- no AndroidX/runtime third-party libraries
