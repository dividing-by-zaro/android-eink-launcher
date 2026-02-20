# Data Model: Palma Launcher v1

**Date**: 2026-02-20
**Branch**: `001-palma-launcher`

## Entities

### AppEntry

Represents an installed application visible (or hidden) on the home
screen.

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| packageName | String | `PackageManager` | Primary key (unique) |
| systemName | String | `PackageManager` | The app's default label |
| customName | String? | SharedPreferences | User-assigned name, null if not renamed |
| isHidden | Boolean | SharedPreferences | Whether hidden from home screen |

**Derived fields**:

- `displayName`: returns `customName` if non-null, else `systemName`.
- `sortKey`: `displayName.lowercase()` for alphabetical sorting.

**State transitions**:

- Default → Hidden (user long-presses → "Hide from home screen")
- Hidden → Visible (user selects "Show hidden apps" → taps app to
  restore)
- Default → Renamed (user long-presses → "Rename" → enters name)
- Renamed → Default (user long-presses → "Reset name")

**Notes**:

- AppEntry is not stored as a complete object. The `packageName` and
  `systemName` come from `PackageManager` at runtime. Only `customName`
  and `isHidden` are persisted in SharedPreferences.
- Hidden apps: stored as `Set<String>` of package names.
- Renamed apps: stored as JSON `Map<String, String>` of package name
  to custom name.

---

### WeatherData

Cached weather information fetched from the Open-Meteo API.

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| temperature | Float | Open-Meteo API | Current temp in °F |
| weatherCode | Int | Open-Meteo API | WMO weather code |
| condition | String | Derived | One-word condition from code mapping |
| latitude | Float | Device location | Coordinates used for the fetch |
| longitude | Float | Device location | Coordinates used for the fetch |
| lastFetched | Long | System clock | Unix timestamp of last fetch |

**Derived fields**:

- `condition`: mapped from `weatherCode` via the WMO lookup table
  (see research.md).
- `isStale`: `true` if `System.currentTimeMillis() - lastFetched > 2 hours`.
- `displayText`: formatted as `"68°F Clear"`.

**Persistence**: All fields stored as individual SharedPreferences keys
(`weather_temp`, `weather_code`, `weather_timestamp`, `weather_lat`,
`weather_lon`).

**Refresh rules**:

1. On launcher resume: if `isStale`, fetch new data.
2. On timer: every 2 hours.
3. On failure: retain cached values. If no cached values exist, display
   nothing.

---

### WidgetBinding

Persisted reference to the hosted Android widget.

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| appWidgetId | Int | `AppWidgetHost` | The bound widget's ID |
| hostId | Int | Constant | Hardcoded to `1024` |

**Persistence**: `appWidgetId` stored in SharedPreferences as
`"widget_id"`. `hostId` is a compile-time constant.

**State transitions**:

- Unconfigured: `appWidgetId` is `INVALID_APPWIDGET_ID` (-1). Show
  "Tap to configure" placeholder.
- Configured: `appWidgetId` is a valid positive integer. Render the
  widget via `AppWidgetHost.createView()`.
- Reconfigure: user can re-bind by going through the widget picker
  again (deallocate old ID, allocate new).

---

### UserPreferences

Aggregate of all user-configurable state. Not a separate storage
entity — these are individual SharedPreferences keys grouped here
for documentation.

| Key | Type | Default | Notes |
|-----|------|---------|-------|
| `hidden_apps` | `Set<String>` | Default hidden set | Package names |
| `renamed_apps` | String (JSON) | `"{}"` | Package → custom name map |
| `widget_id` | Int | `-1` | `INVALID_APPWIDGET_ID` |
| `weather_temp` | Float | `0.0` | Last fetched temperature |
| `weather_code` | Int | `-1` | Last fetched WMO code |
| `weather_timestamp` | Long | `0` | Epoch millis of last fetch |
| `weather_lat` | Float | `36.17` | Last known latitude |
| `weather_lon` | Float | `-115.14` | Last known longitude |

**Default hidden set**: On first install, includes the launcher's own
package name and a curated list of non-useful system apps (e.g.,
`com.android.htmlviewer`, `com.android.printspooler`). This set is
written to SharedPreferences on first launch.

---

## Entity Relationships

```text
AppEntry (runtime from PackageManager)
  ├── hidden status ← UserPreferences.hidden_apps
  └── custom name   ← UserPreferences.renamed_apps

WeatherData (cached in SharedPreferences)
  └── coordinates   ← Device location (FusedLocationProviderClient)

WidgetBinding (persisted in SharedPreferences)
  └── appWidgetId   ← AppWidgetHost allocation
```

There are no relational dependencies between entities. Each is
independently readable and writable.
