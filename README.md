A minimal, e-ink-optimized home screen launcher for the Boox Palma.

## Features

- [x] Pure black & white UI — no grays, no animations
- [x] Live clock with full date (e.g. "Friday, February 20")
- [x] Battery icon with percentage (Canvas-drawn)
- [x] Weather with Material Icons (sun, cloud, rain, snow, etc.) via Open-Meteo API
- [x] Single info row: date · battery · weather
- [x] Right-aligned app list (text only, no icons)
- [x] Long-press context menu: rename, hide, show hidden, app info
- [x] Widget hosting (any Android widget)
- [x] System status bar hidden for full-screen experience
- [x] Light navigation bar

## Setup

Requires Android SDK 34. Connect your Boox Palma via USB, then:

```bash
./gradlew installDebug
adb shell pm enable com.palma.launcher
```

Press the home button and select Palma Launcher.
