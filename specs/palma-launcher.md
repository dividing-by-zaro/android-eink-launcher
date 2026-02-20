*A minimal, e-ink optimized Android launcher for the Boox Palma.*

**Status**: Spec
**Platform**: Android 11+ (Boox Palma)
**Language**: Kotlin + Jetpack Compose

---

## Design Principles

1. **E-ink first** — Every design choice optimizes for e-ink displays. No animations, no gradients, no grays. Pure black on white. Minimize full-screen refreshes.
2. **Single screen** — Everything lives on one vertically scrollable home screen. No swipe-to-page, no app drawer. What you see is what you get.
3. **Reading-centric** — The Palma is a reading device. The launcher should reflect that by giving the library top billing.
4. **Calm** — No badges, no notifications, no attention-grabbing elements. A quiet home screen.

---

## Screen Layout

The home screen is a single vertically scrollable view with three zones:

```
┌─────────────────────────┐
│  12:47         68°F  ☁  │  ← Status bar
├─────────────────────────┤
│                         │
│  ┌───────────────────┐  │
│  │                   │  │
│  │   Boox Library    │  │  ← Library widget
│  │     Widget        │  │
│  │                   │  │
│  └───────────────────┘  │
│                         │
├─────────────────────────┤
│                         │
│  Kindle                 │
│  KOReader               │  ← App list
│  Firefox                │
│  Mail                   │
│  Obsidian               │
│  Spotify                │
│  Settings               │
│                         │
└─────────────────────────┘
```

### Zone 1: Status Bar

A minimal top bar showing:

- **Clock** — Time in large-ish monospace type, left-aligned. 12-hour format, no seconds. Updates once per minute.
- **Weather** — Current temperature and a simple text condition (e.g., "68°F Sunny"), right-aligned.

Weather source: Open-Meteo free API (no key required). Location hardcoded to Las Vegas or configurable in settings. Refreshes every 2 hours to minimize network calls.

No date display — the Palma's system status bar already shows it.

### Zone 2: Library Widget

Hosts the Boox **NeoReader Library widget** via Android's `AppWidgetHost` API. This is the actual system widget, not a custom recreation.

- Occupies the full width of the screen
- Height is determined by the widget's own sizing
- Tap interactions pass through to the widget normally (opening books, browsing library)
- If the widget is unavailable or not configured, show a tap-to-configure placeholder

**Implementation note**: `AppWidgetHost` and `AppWidgetHostView` are well-documented but have quirks. The launcher must:
- Allocate a unique host ID
- Handle the widget bind/configure flow via `AppWidgetManager`
- Persist the widget's `appWidgetId` across restarts
- Resize the widget host view to match the allocated width

### Zone 3: App List

A vertical list of app names in plain text. No icons. Each entry is simply the app's display name in a readable font size, with generous vertical padding for easy tap targets.

- Sorted alphabetically by display name (or custom name if renamed)
- Tap to launch
- Long-press to open the app context menu (rename, hide, app info)
- No grouping, no folders, no categories — just a flat list

---

## Features

### App Hiding

Users can hide any app from the home screen. Hidden apps are not deleted — they're just filtered from the list.

- **Hide**: Long-press an app → "Hide from home screen"
- **Unhide**: Settings → Hidden Apps → tap to restore
- **Default hidden**: Launcher itself is auto-hidden. System apps that aren't useful standalone (e.g., "com.android.htmlviewer") are hidden by default but can be unhidden.

Storage: Hidden app package names stored in `SharedPreferences` as a `Set<String>`.

### App Renaming

Users can assign custom display names to any app. The custom name is shown everywhere in the launcher.

- **Rename**: Long-press an app → "Rename" → text input dialog
- **Reset**: Long-press → "Reset name" (reverts to system name)
- Renamed apps sort by their custom name

Use cases:
- "Google Chrome" → "Browser"
- "Neo Reader" → "Reader"
- "K-9 Mail" → "Mail"

Storage: `Map<String, String>` of package name → custom name in `SharedPreferences`.

### Clock

- Displayed in the top-left of the status bar
- Large, high-contrast monospace font
- Updates every 60 seconds via a `BroadcastReceiver` on `ACTION_TIME_TICK`
- Tapping the clock opens the system clock/alarm app

### Weather

- Displayed in the top-right of the status bar
- Shows: temperature (°F) and one-word condition
- Source: Open-Meteo API (`open-meteo.com/en/docs`)
- Refresh interval: every 2 hours, or on launcher resume
- Coordinates: hardcoded to Las Vegas (36.17, -115.14), changeable in settings
- Fallback: if network unavailable, show last cached weather or nothing
- Tapping the weather area opens a simple detail toast or popup: high/low, humidity, wind

### Settings Screen

Accessed via long-pressing empty space on the home screen, or from a small gear icon in the bottom corner.

- **Hidden Apps** — list of hidden apps with toggle switches
- **Renamed Apps** — list of renamed apps with edit/reset
- **Weather Location** — text field for coordinates or city name
- **Temperature Unit** — °F / °C toggle
- **Library Widget** — configure/reconfigure the widget
- **About** — version info

---

## Typography & Visual Design

| Element | Font | Size | Weight |
|---|---|---|---|
| Clock | System monospace | 20sp | Medium |
| Weather | System sans-serif | 14sp | Regular |
| App names | System serif or sans-serif | 18sp | Regular |
| Section dividers | — | 1dp | Black line |

- **Colors**: Pure black (`#000000`) on pure white (`#FFFFFF`). No grays, no accent colors.
- **Tap targets**: Minimum 48dp height per app list item, with 16dp vertical padding.
- **Dividers**: Thin 1dp black lines between the three zones. No dividers between app list items — whitespace only.
- **Margins**: 16dp horizontal padding throughout.
- **Scrolling**: The app list scrolls if it exceeds the screen. The status bar and widget are pinned at the top (not scrollable).

---

## Technical Notes

### Launcher Registration

```xml
<activity android:name=".LauncherActivity"
    android:launchMode="singleTask"
    android:stateNotNeeded="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### Widget Hosting

The launcher acts as an `AppWidgetHost`. Key classes:

- `AppWidgetHost` — manages widget lifecycle
- `AppWidgetHostView` — the view that renders the widget
- `AppWidgetManager` — system service for binding widgets

The user selects the Boox Library widget during first-run setup. The launcher stores the bound `appWidgetId` and re-inflates the widget on each launch.

### Back Button / Home Button

- **Home button**: Returns to launcher (standard Android behavior when set as default)
- **Back button**: If in settings, return to home screen. If on home screen, do nothing.

### Crash Recovery

If the launcher crashes, Android falls back to the previously set launcher. The Palma's default Boox launcher remains installed as a fallback. User can switch back in Settings → Apps → Default Apps.

### Build & Install

- Build with Android Studio or Gradle CLI
- Generate debug APK: `./gradlew assembleDebug`
- Transfer to Palma via USB or file sharing
- Install and select as default launcher when prompted

---

## Out of Scope (v1)

These are explicitly **not** included in the first version:

- Notification badges or counts
- Gesture navigation (swipe actions)
- Folders or app grouping
- Search bar
- Wallpaper or background images
- Multiple home screen pages
- Icon packs (there are no icons)
- Dock or favorites row
