# Quickstart: Palma Launcher v1

**Branch**: `001-palma-launcher`

## Prerequisites

- Android Studio (Hedgehog or newer)
- Android SDK with API 30+ (Android 11)
- A Boox Palma device (or Android emulator for initial development)
- USB cable for sideloading

## Project Setup

1. Create a new Android project in Android Studio:
   - Template: Empty Compose Activity
   - Package name: `com.palma.launcher`
   - Minimum SDK: API 30 (Android 11)
   - Build configuration language: Kotlin DSL

2. Add dependencies to `app/build.gradle.kts`:

   ```kotlin
   dependencies {
       implementation("androidx.activity:activity-compose:1.8.2")
       implementation("androidx.compose.ui:ui:1.6.1")
       implementation("androidx.compose.material3:material3:1.2.0")
       implementation("com.google.android.gms:play-services-location:21.1.0")
   }
   ```

3. Add permissions to `AndroidManifest.xml`:

   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
   ```

4. Configure the launcher Activity in `AndroidManifest.xml` with the
   HOME + DEFAULT intent filter (see contracts/android-system-apis.md).

## Build & Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install via ADB (if device is connected via USB)
adb install app/build/outputs/apk/debug/app-debug.apk

# Or transfer the APK file manually and install on device
```

## First Run

1. Install the APK on the Boox Palma.
2. Press the Home button — Android will ask which launcher to use.
3. Select "Palma Launcher" and choose "Always".
4. The home screen appears with the header, widget placeholder, and
   app list.
5. Tap the widget placeholder to configure the NeoReader Library widget.
6. Grant location permission when prompted (for weather).

## Reverting to Default Launcher

If something goes wrong:

1. Open Android Settings → Apps → Default Apps → Home app.
2. Select the Boox default launcher.
3. Alternatively, if the Palma Launcher crashes, Android automatically
   falls back to the Boox launcher.

## Key Files

```
app/src/main/
├── AndroidManifest.xml          # Launcher registration
├── java/com/palma/launcher/
│   ├── LauncherActivity.kt      # Main Activity (Compose entry point)
│   ├── ui/
│   │   ├── theme/
│   │   │   └── Theme.kt         # E-ink black/white theme
│   │   ├── HomeScreen.kt        # Main home screen composable
│   │   ├── HeaderSection.kt     # Clock, date, battery, weather
│   │   ├── WidgetSection.kt     # AppWidgetHost integration
│   │   └── AppListSection.kt    # Scrollable app list
│   ├── data/
│   │   ├── PreferencesManager.kt # SharedPreferences wrapper
│   │   └── WeatherRepository.kt  # Open-Meteo API + caching
│   └── widget/
│       └── WidgetHostManager.kt  # AppWidgetHost lifecycle
└── res/
    └── values/
        └── themes.xml            # Base theme (white background)
```

## Development Tips

- Test on the Boox Palma frequently — e-ink behavior differs from
  emulators.
- Use `@Preview` annotations for quick Compose iteration in Android
  Studio.
- The emulator can validate layout and logic, but not e-ink rendering
  quality.
- Weather API calls require internet — use a mock response during
  offline testing.
