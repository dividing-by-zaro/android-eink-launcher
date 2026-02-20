# Contract: Android System APIs

**Type**: Platform APIs used by the launcher
**Platform**: Android 11+ (API 30+)

## PackageManager — App Discovery

**Purpose**: Query installed apps for the app list.

### Input

```kotlin
packageManager.queryIntentActivities(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
    0
)
```

### Output

List of `ResolveInfo` objects, each containing:

| Field | Type | Used For |
|-------|------|----------|
| `activityInfo.packageName` | String | Unique app identifier |
| `loadLabel(pm)` | CharSequence | System display name |
| `activityInfo.name` | String | Activity to launch |

### Refresh Triggers

- On launcher resume (apps may have been installed/uninstalled).
- Via `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED` broadcasts
  (optional optimization).

---

## AppWidgetHost — Widget Hosting

**Purpose**: Embed the NeoReader Library widget.

### Lifecycle

| Event | Action |
|-------|--------|
| Activity `onResume()` | `appWidgetHost.startListening()` |
| Activity `onStop()` | `appWidgetHost.stopListening()` |

### Bind Flow

1. `appWidgetHost.allocateAppWidgetId()` → `Int`
2. `AppWidgetManager.bindAppWidgetIdIfAllowed(id, component)` → `Boolean`
3. If `false`: launch `ACTION_APPWIDGET_BIND` intent
4. If `providerInfo.configure != null`: launch configure activity
5. `appWidgetHost.createView(context, id, providerInfo)` → `AppWidgetHostView`

### Persistence

| Key | Value |
|-----|-------|
| `widget_id` | `appWidgetId` (Int) in SharedPreferences |

---

## BroadcastReceivers — System Events

### Time Updates

| Action | When Fired | Registration |
|--------|-----------|--------------|
| `ACTION_TIME_TICK` | Every minute | Code only (not manifest) |
| `ACTION_TIME_CHANGED` | Manual time change | Code only |
| `ACTION_TIMEZONE_CHANGED` | Timezone change | Code only |

Register in `onResume()`, unregister in `onPause()`.

### Battery

| Action | When Fired | Registration |
|--------|-----------|--------------|
| `ACTION_BATTERY_CHANGED` | Every ~1% change | Code (sticky) |

**Sticky broadcast**: `registerReceiver(null, filter)` returns the
last broadcast immediately without waiting.

**Fields extracted**:

| Extra | Type | Purpose |
|-------|------|---------|
| `EXTRA_LEVEL` | Int | Current level |
| `EXTRA_SCALE` | Int | Max level (usually 100) |
| `EXTRA_STATUS` | Int | Charging state |

Percentage = `(level * 100) / scale`

---

## FusedLocationProviderClient — Device Location

**Purpose**: Get coordinates for weather API.

### Permission

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Runtime request required (Android 6+).

### Usage Pattern

1. `fusedClient.lastLocation` → `Location?` (zero battery cost)
2. If null: `fusedClient.getCurrentLocation(PRIORITY_LOW_POWER)` →
   `Location?`
3. If null: fall back to hardcoded coordinates (36.17, -115.14)

### Dependency

```gradle
implementation("com.google.android.gms:play-services-location:21.1.0")
```

---

## Launcher Registration

### Manifest Intent Filter

```xml
<activity
    android:name=".LauncherActivity"
    android:launchMode="singleTask"
    android:stateNotNeeded="true"
    android:clearTaskOnLaunch="true"
    android:excludeFromRecents="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### Back Button Contract

| State | Back Button Behavior |
|-------|---------------------|
| Home screen | Do nothing (consume) |
