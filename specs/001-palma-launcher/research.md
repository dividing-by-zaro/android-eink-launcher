# Research: Palma Launcher v1

**Date**: 2026-02-20
**Branch**: `001-palma-launcher`

## 1. AppWidgetHost in Jetpack Compose

**Decision**: Use `AppWidgetHost` / `AppWidgetManager` (View-based APIs)
embedded in Compose via `AndroidView`.

**Rationale**: There is no Compose-native widget hosting API. The standard
Android widget system is entirely View-based. `AndroidView` bridges the
two worlds cleanly.

**Key approach**:

- Create `AppWidgetHost` with a hardcoded stable host ID (e.g., `1024`).
- Allocate an `appWidgetId` via `appWidgetHost.allocateAppWidgetId()`.
- Bind using `AppWidgetManager.bindAppWidgetIdIfAllowed()`, falling back
  to `ACTION_APPWIDGET_BIND` intent if permission is needed.
- Check `AppWidgetProviderInfo.configure` — if non-null, launch the
  configure activity.
- Create the view: `appWidgetHost.createView(context, appWidgetId, info)`.
- Embed in Compose via `AndroidView { hostView }`.
- Persist the `appWidgetId` in SharedPreferences for restart recovery.
- Call `startListening()` in `onResume()` and `stopListening()` in
  `onStop()`.

**Widget sizing (2x4)**: Use `MATCH_PARENT` for width. Height derived
from the provider info's `minHeight` or calculated from the standard
cell formula: `70 * n - 30` dp (4 rows = 250dp).

**Alternatives considered**:

- Recreating the library UI manually: rejected — the NeoReader widget
  provides rich functionality that would be prohibitive to replicate.
- Using a `Fragment` host: rejected — adds unnecessary complexity when
  `AndroidView` in Compose handles it directly.

**Boox-specific notes**: The NeoReader Library widget's `ComponentName`
must be discovered on-device via `AppWidgetManager.getInstalledProviders()`.
Exact package/class names are proprietary.

---

## 2. Launcher Registration

**Decision**: Standard manifest intent filter with `HOME` + `DEFAULT`
categories.

**Rationale**: This is the documented Android mechanism. No alternatives
exist.

**Key manifest attributes**:

- `launchMode="singleTask"` — single instance, Home returns to it.
- `stateNotNeeded="true"` — clean restart on crash.
- `clearTaskOnLaunch="true"` — returns to home screen when re-entered.
- `excludeFromRecents="true"` — keeps launcher out of recents.
- `exported="true"` — required on Android 12+ for activities with intent
  filters (forward-compat).

**Back button**: Suppress via `BackHandler(enabled = isOnHomeScreen) {}`.

---

## 3. Open-Meteo API

**Decision**: Use the Open-Meteo `/v1/forecast` endpoint with the
`current` parameter.

**Rationale**: Free, no API key, simple JSON response, covers all needed
weather data.

**Endpoint**:

```
GET https://api.open-meteo.com/v1/forecast
  ?latitude={lat}&longitude={lon}
  &current=temperature_2m,weather_code
  &temperature_unit=fahrenheit
  &timezone=auto
```

**Response fields**: `current.temperature_2m` (float, °F),
`current.weather_code` (int, WMO code).

**WMO code mapping** (to one-word display text):

| Codes | Display |
|-------|---------|
| 0, 1 | Clear |
| 2 | Cloudy |
| 3 | Overcast |
| 45, 48 | Fog |
| 51–57 | Drizzle |
| 61–67 | Rain |
| 71–77 | Snow |
| 80–82 | Showers |
| 85, 86 | Snow |
| 95–99 | Storms |

**Implementation**: Use `java.net.HttpURLConnection` (no extra
dependency). Call on `Dispatchers.IO`. Cache response in
SharedPreferences.

**Alternatives considered**:

- OpenWeatherMap: rejected — requires API key.
- WeatherAPI: rejected — requires API key.
- `HttpURLConnection` vs OkHttp/Retrofit: `HttpURLConnection` chosen to
  avoid extra dependencies for a single API call.

---

## 4. Location Services

**Decision**: Use `FusedLocationProviderClient.getLastLocation()` with
`ACCESS_COARSE_LOCATION` permission. Fallback to hardcoded Las Vegas
coordinates.

**Rationale**: `getLastLocation()` has zero battery cost — it returns
the most recently cached location from other apps. Coarse location
(city-level) is sufficient for weather. The Palma is WiFi-only, so
location comes from WiFi positioning.

**Key approach**:

1. Request `ACCESS_COARSE_LOCATION` at runtime.
2. Call `fusedClient.lastLocation`. If non-null, use those coordinates.
3. If null, call `getCurrentLocation()` with
   `Priority.PRIORITY_LOW_POWER`.
4. If still null (or permission denied), fall back to Las Vegas
   (36.17, -115.14).
5. Cache coordinates in SharedPreferences. Re-request location only
   every few hours.

**Dependency**: `com.google.android.gms:play-services-location:21.1.0`

**Alternatives considered**:

- `LocationManager` directly: rejected — `FusedLocationProviderClient`
  is higher-level and handles provider selection.
- Fine location: rejected — unnecessary accuracy for weather, higher
  battery cost.
- Hardcoded only: rejected — user wants device location automatically.

---

## 5. E-ink Optimization in Compose

**Decision**: Pure black/white theme, all animations disabled, minimal
recomposition.

**Rationale**: E-ink displays have slow refresh (~250ms partial), no
smooth animation support, and poor grayscale rendering. Every design
choice must account for these constraints.

**Key techniques**:

- **Theme**: `lightColorScheme()` with all slots set to `Color.Black` or
  `Color.White`. No grays, no alpha.
- **Animations**: Disable all Compose animation APIs. Navigation
  transitions set to `EnterTransition.None` / `ExitTransition.None`.
- **Overscroll**: Disable via
  `LocalOverscrollConfiguration provides null`.
- **Recomposition**: Update clock once/minute, weather once/2 hours.
  Use `remember` and stable keys in `LazyColumn`.
- **Text**: Use `FontWeight.Medium` or higher for legibility. Thin
  weights look faint on e-ink.
- **Tap feedback**: Use inverted black/white (e.g., black background
  with white text on press) rather than opacity ripple.

**Boox-specific**: Boox SDK offers refresh mode control (GC16 for
static, DU/A2 for scrolling). This is optional for v1 — stock Android
rendering works acceptably.

---

## 6. Data Persistence

**Decision**: SharedPreferences with `apply()` (async writes).

**Rationale**: The data is a handful of key-value pairs. DataStore adds
a dependency and coroutine complexity that is unjustified for this
simple case. SharedPreferences is built into Android.

**Data layout**:

| Key | Type | Example |
|-----|------|---------|
| `hidden_apps` | `Set<String>` | `{"com.android.htmlviewer"}` |
| `renamed_apps` | String (JSON) | `{"com.google.chrome":"Browser"}` |
| `widget_id` | Int | `42` |
| `weather_temp` | Float | `68.2` |
| `weather_code` | Int | `1` |
| `weather_timestamp` | Long | `1708444800000` |
| `weather_lat` | Float | `36.17` |
| `weather_lon` | Float | `-115.14` |

**Gotcha**: `getStringSet()` returns a reference to the internal set.
Always copy before modifying: `.toMutableSet()`.

**Alternatives considered**:

- DataStore: rejected — overkill for this use case, adds dependency.
- Room: rejected — no relational data, no queries needed.

---

## 7. Time Updates (ACTION_TIME_TICK)

**Decision**: Register a `BroadcastReceiver` for `ACTION_TIME_TICK`,
`ACTION_TIME_CHANGED`, and `ACTION_TIMEZONE_CHANGED` in code.

**Rationale**: `ACTION_TIME_TICK` fires every minute on the system
minute boundary — exactly what a clock display needs. Must be registered
in code (not manifest), which aligns with registering in
`onResume()`/`onPause()`.

**Key approach**:

- Create a Compose-friendly `rememberCurrentTime()` function using
  `DisposableEffect` to register/unregister the receiver.
- Set the initial time immediately on registration (the tick broadcast
  is not sticky).
- Also handle `ACTION_TIME_CHANGED` (manual time change) and
  `ACTION_TIMEZONE_CHANGED`.

**Date display**: Use `SimpleDateFormat` or `java.time.LocalDate` to
format the current date (e.g., "Thu, Feb 20").

---

## 8. Battery Monitoring

**Decision**: Use `ACTION_BATTERY_CHANGED` sticky broadcast for initial
read, then register a `BroadcastReceiver` for ongoing updates.

**Rationale**: The sticky broadcast gives an immediate result (no
waiting). Ongoing registration catches every 1% change.

**Key approach**:

- Initial read: `registerReceiver(null, IntentFilter(ACTION_BATTERY_CHANGED))`
  returns the last broadcast immediately.
- Calculate percentage: `(EXTRA_LEVEL * 100) / EXTRA_SCALE`.
- Detect charging: `EXTRA_STATUS == BATTERY_STATUS_CHARGING || FULL`.
- Wrap in a `rememberBatteryState()` composable using `DisposableEffect`.

**E-ink consideration**: Battery changes fire frequently (every 1%).
Each update recomposes the battery text, causing a tiny e-ink redraw.
This is acceptable for a small text region. If flicker is noticeable,
debounce to 5% increments.

---

## Summary: External Dependencies

| Dependency | Purpose |
|-----------|---------|
| `androidx.activity:activity-compose` | Compose Activity |
| `androidx.compose.ui:ui` | Core Compose |
| `androidx.compose.material3:material3` | Theme, components |
| `com.google.android.gms:play-services-location` | Device location |
| `org.json:json` (built-in) | Parse weather JSON |

No Retrofit, Room, DataStore, Hilt, or other heavy libraries needed.
