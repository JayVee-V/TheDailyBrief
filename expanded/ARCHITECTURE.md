# Architecture

## Setup path

ZIP → Open-Meteo geocoding → city/county/state/coordinates → Google News local RSS → candidate publishers → direct RSS/Atom autodiscovery → user selects sources.

The Google News discovery dependency is intentionally outside the normal daily path whenever direct publisher feeds exist.

## Daily path

Selected publisher feeds → generic RSS/Atom parser → filtering/deduplication/interleaving → cached story set

Coordinates → Open-Meteo forecast → cached weather

Stories + weather → HTML/CSS edition → local WebView

## Reliability rules

1. One failed feed cannot stop the edition.
2. Last good stories are carried forward on a thin/failed morning (up to 96 hours).
3. Last good weather is displayed as cached if Open-Meteo is unavailable.
4. A direct feed is re-discovered after three consecutive failures.
5. If repair fails, that publisher can switch to a source-filtered Google News fallback.
6. Manual RSS entry means a user never has to wait for an app update to add a source.
7. Background work uses Android JobScheduler; foreground opening also repairs stale editions.

## Maintenance philosophy

Routine publisher URL changes should be repaired on-device. APK updates should be reserved for actual app bugs or features rather than changes to a hand-maintained national source list.
