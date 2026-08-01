# Architecture

## Data flow

1. `WeatherViewModel` exposes settings and a single `WeatherLoadState`.
2. `WeatherRepository` obtains a precise location when permitted, otherwise the
   HKO headquarters fallback, then asks `HkoClient` for a snapshot.
3. `HkoClient` requests independent HKO datasets concurrently. Open-Meteo fills
   only local fields unavailable from HKO and never replaces an available HKO
   observation.
4. A successful raw response bundle is committed through Android `AtomicFile`.
   A network failure returns that cache with a visible stale indicator.
5. Compose renders immutable domain models. UI settings are isolated in
   `SharedPreferences` and exposed as a `StateFlow`.

## Location model

Android's fused provider requests a fresh high-accuracy reading. The platform
geocoder resolves a Hong Kong street or feature label and district. The app then
normalises English/Chinese district output and calculates the nearest supported
HKO observation and tide stations with great-circle distance. Location is not
uploaded, logged, or included in FCM subscriptions.

## Alert truth and notification delivery

The app derives active alert tiles from HKO `warnsum`; `warningInfo` supplies the
long text and `swt` supplies special tips. Rows marked `CANCEL` are excluded.

Apps Script performs the same active-state normalisation every five minutes.
IDs are warning-code based (tips use a content digest), while fingerprints also
contain update time and text. A script lock prevents overlapping executions.
The first run is a silent baseline. Later issue/update/cancel differences are
grouped by Android notification channel and sent to the `hko_alerts` FCM topic
using short-lived OAuth 2.0 credentials and the FCM HTTP v1 endpoint.

## Security boundaries

- `google-services.json` identifies the Firebase Android client; it is not a
  server credential and cannot authorise FCM sends.
- A Firebase service-account private key exists only as an Apps Script Property.
- Release signing material exists only in a local environment or GitHub Secrets.
- Backups are disabled to avoid exporting the cached location/weather bundle.
- Network calls are HTTPS and no app WebView or JavaScript bridge remains.

## Build and delivery

The project uses AGP 9, the built-in Kotlin toolchain, JDK 17 and the Compose
compiler plugin. GitHub Actions validates the Gradle wrapper, runs tests/lint,
and uploads an APK. A separate manual/tag workflow requires signing secrets and
also produces an Android App Bundle and checksums.
