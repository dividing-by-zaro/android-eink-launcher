# Implementation Plan: Palma Launcher v1

**Branch**: `001-palma-launcher` | **Date**: 2026-02-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-palma-launcher/spec.md`

## Summary

Build a minimal, e-ink optimized Android launcher for the Boox Palma.
The launcher displays a single scrollable home screen with three zones:
a header (large clock, date, battery, weather), the NeoReader Library
widget (2x4, hosted via AppWidgetHost), and a plain-text alphabetical
app list. Users can launch, hide, unhide, and rename apps via a
long-press context menu. Weather fetches from the Open-Meteo API using
the device's coarse location. All preferences persist in
SharedPreferences. Built with Kotlin + Jetpack Compose, targeting
Android 11+.

## Technical Context

**Language/Version**: Kotlin 1.9+ / JVM target 17 / Compose BOM 2024.02
**Primary Dependencies**: Jetpack Compose (UI), Material3 (theme),
Google Play Services Location (coarse location), `org.json` (built-in
JSON parsing)
**Storage**: SharedPreferences (hidden apps, renamed apps, widget ID,
cached weather)
**Testing**: Android instrumentation tests via `./gradlew connectedCheck`,
manual on-device testing on Boox Palma
**Target Platform**: Android 11+ (API 30+), Boox Palma hardware
**Project Type**: Mobile (single Android app)
**Performance Goals**: Home screen renders in <1 second; app list
handles 100+ apps without lag; no animations (e-ink constraint)
**Constraints**: Pure black/white only; no animations; no grays;
WiFi-only networking; minimize full-screen refreshes
**Scale/Scope**: Single user, ~1 screen, ~10 source files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. E-Ink First | PASS | All UI is pure black on white. No animations used anywhere. Theme uses `Color.Black` and `Color.White` exclusively. Overscroll effects disabled. |
| II. Single Screen | PASS | One vertically scrollable home screen with three fixed zones: header (pinned), widget (pinned), app list (scrollable). No app drawer, no swipe-to-page. |
| III. Reading-Centric | PASS | NeoReader Library widget is zone 2, directly below the header and above the app list. Books are launchable with a single tap in the widget. |
| IV. Calm Interface | PASS | No notification badges. No attention-grabbing elements. Generous padding (48dp tap targets, 16dp margins). Weather refreshes at most every 2 hours. |

**Technical Constraints check**:

| Constraint | Status | Evidence |
|------------|--------|----------|
| Platform: Android 11+ | PASS | minSdk = 30 |
| Language: Kotlin + Compose | PASS | All UI in Compose, no XML layouts |
| Widget: AppWidgetHost | PASS | Standard API, embedded via AndroidView |
| Storage: SharedPreferences | PASS | All persistence via SharedPreferences |
| Network: Open-Meteo only | PASS | Single API, no key required |
| Scope boundary | PASS | No badges, gestures, folders, search, wallpaper, pages, icons, dock |
| Crash recovery | PASS | Boox default launcher remains installed as fallback |

**Post-Phase 1 re-check**: All gates still pass. No design decisions
introduced violations.

## Project Structure

### Documentation (this feature)

```text
specs/001-palma-launcher/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── open-meteo-api.md
│   └── android-system-apis.md
├── checklists/
│   └── requirements.md
└── tasks.md             (created by /speckit.tasks)
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── java/com/palma/launcher/
        ├── LauncherActivity.kt
        ├── ui/
        │   ├── theme/
        │   │   └── Theme.kt
        │   ├── HomeScreen.kt
        │   ├── HeaderSection.kt
        │   ├── WidgetSection.kt
        │   └── AppListSection.kt
        ├── data/
        │   ├── PreferencesManager.kt
        │   └── WeatherRepository.kt
        └── widget/
            └── WidgetHostManager.kt
```

**Structure Decision**: Standard single-module Android app. All source
code under `app/src/main/java/com/palma/launcher/`. The app is small
enough (~10 files) that a single module with package-based organization
(ui, data, widget) is sufficient. No multi-module, no build variants
beyond debug.

## Complexity Tracking

> No violations — all constitution gates pass. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |
