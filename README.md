# NewPaper

**The newspaper, reinvented.**

NewPaper (formerly The Morning Brief / The Daily Brief) is a customizable Android newspaper with finite daily editions rather than a continuously refreshing feed. No account or hosted NewPaper backend is required.

## Download

Get the signed APK from [GitHub Releases](../../releases). Existing users can install it over The Morning Brief: the Android application ID and signing identity are unchanged.

## The daily paper

- **Local:** local and Michigan reporting, a lead story, weather with feels-like temperatures, selectable news sources and editorial filtering.
- **National:** US-focused English-language reporting, selectable sources and grouped related coverage.
- **Features:** a persistent reading shelf for selected feature feeds and saved news. Read opens an article; Close removes it.
- **Leisure:** selectable comics, optional memes and individually selectable daily puzzles. Comic images are embedded when available; the in-app reader provides a direct return to Leisure.

The app checks for a new edition each morning (scheduled for 4:45 a.m., subject to Android scheduling), with at most one automatic refresh attempt per section per calendar day. Manual refresh remains available in section-specific settings.

## Privacy

Location settings, source choices, appearance, reading history, saved articles and cached editions are kept on the device. The app contacts weather services, news discovery services, publisher feeds and selected comic and puzzle providers directly.

## Build

The current Android source is in `expanded/`. With Java 17, Gradle 8.9 and Android SDK 35:

```sh
gradle -p expanded :app:assembleDebug
```

The ordinary CI build produces an Actions artifact for development. Installable phone releases are separately built and signed with the persistent signing key and checked against the known-compatible v0.6.0-test.2 release certificate before publication. Use those signed releases for in-place updates.

- minSdk 26 (Android 8)
- targetSdk / compileSdk 35
- Java 17
- No AndroidX/runtime third-party libraries

The existing package ID `org.morningbrief.app` and stored-data identifiers intentionally retain their historical names for upgrade compatibility.
