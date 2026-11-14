# The Morning Brief — Android v0.4.0

A stand-alone Android version of **The Morning Brief**. It gathers weather and local news on the phone; no PC, LAN server, hosted backend, or Morning Brief account is required.

## What changed in v0.4.0

### A real editorial layer

The app no longer tries to fill an arbitrary ten-story quota. It now gathers a larger candidate pool and builds the edition using a small deterministic editorial engine:

1. Filter excluded content and stale items.
2. Score stories for source quality, freshness, locality, and public-interest value.
3. Cluster near-duplicate headlines covering the same event.
4. Prefer the strongest/nonprofit version of duplicate coverage.
5. Apply diminishing-return penalties when one publisher or one topic starts dominating the edition.
6. Keep stories only while they remain above the editorial quality threshold.

The result can be short on a quiet morning or substantially longer on a busy one. A 25-story hard cap exists only as a circuit breaker.

Preferred nonprofit/community sources get a large scoring advantage, followed by other INN outlets. Commercial/direct and Google News stories can still make the edition when they add useful coverage, but they no longer receive slots simply for variety.

### Newspaper-style sections

The edition is now organized as:

- **Top Stories**
- **Local**
- **Michigan**
- **More to Know**

Stories are grouped by editorial role rather than by publisher.

### Visible refresh progress

Foreground refreshes now show real progress/status information while the app works, including:

- source count processed
- filtering stage
- duplicate comparison
- editorial selection
- final edition build

The progress bar reflects actual stages instead of showing an indefinite spinner.

### Better dark-mode Settings

The Settings page now uses distinct cards/panels, brighter section headings, higher-contrast text, visible controls, stronger checkbox/toggle tinting, and a readable dark-mode appearance picker.

## Source preference

Known West Michigan installs still automatically get preferred direct feeds for:

- Bridge Michigan
- The Rapidian
- Ottawa News Network

The app also discovers other nonprofit publishers through INN / Find Your News and probes publisher sites for direct RSS/Atom feeds. Google News remains a fallback/discovery layer rather than the primary editor.

## Content defaults

Sports, obituaries, opinion/letters, and recipes are excluded by default. Each can be enabled in Settings.

## Privacy

ZIP code, source choices, appearance, cached stories, and cached weather are stored locally on the device.

Network requests go directly to:

- Open-Meteo for ZIP lookup and weather
- FindYourNews.org during nonprofit-source discovery
- Google News during source discovery or fallback
- selected publisher feeds

No Morning Brief server is required.

## Build

### Android Studio

Open this directory as an Android Studio project and build the debug APK.

Output:

`app/build/outputs/apk/debug/app-debug.apk`

### CachyOS / terminal

The included Fish script uses an installed or cached Gradle distribution:

`fish build-and-install.fish`

If ADB sees a connected phone, the script also installs the resulting APK. Otherwise it prints the APK path.

### GitHub Actions

`.github/workflows/build-apk.yml` builds a debug APK and uploads it as a workflow artifact.

## Build target

- minSdk 26 (Android 8)
- targetSdk 35
- compileSdk 35
- Java 17
- no AndroidX/runtime third-party libraries

The editorial system is intentionally rule-based; no on-device language model or embedding library is bundled.
