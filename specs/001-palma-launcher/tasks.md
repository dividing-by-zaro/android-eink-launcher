# Tasks: Palma Launcher v1

**Input**: Design documents from `/specs/001-palma-launcher/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Not requested in the feature specification. Test tasks are omitted.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Mobile (Android)**: `app/src/main/java/com/palma/launcher/`
- **Manifest**: `app/src/main/AndroidManifest.xml`
- **Build**: `app/build.gradle.kts`
- **Resources**: `app/src/main/res/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization — create Android project, configure dependencies, register as launcher

- [x] T001 Create Android project directory structure with `app/src/main/java/com/palma/launcher/` and subdirectories `ui/`, `ui/theme/`, `data/`, `widget/` per plan.md
- [x] T002 Configure `app/build.gradle.kts` with Kotlin, Compose BOM 2024.02, Material3, play-services-location dependencies, minSdk 30, targetSdk 34
- [x] T003 Configure `app/src/main/AndroidManifest.xml` with launcher Activity registration (HOME + DEFAULT intent filter, singleTask, stateNotNeeded, clearTaskOnLaunch, excludeFromRecents, exported=true) and permissions (INTERNET, ACCESS_COARSE_LOCATION)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [x] T004 Create e-ink theme in `app/src/main/java/com/palma/launcher/ui/theme/Theme.kt` — pure black/white `lightColorScheme`, all color slots set to `Color.Black` or `Color.White`, no grays, no alpha, e-ink optimized typography (monospace for clock, sans-serif for body, minimum 14sp)
- [x] T005 [P] Create `PreferencesManager` in `app/src/main/java/com/palma/launcher/data/PreferencesManager.kt` — SharedPreferences wrapper with read/write methods for all keys defined in data-model.md: `hidden_apps` (Set), `renamed_apps` (JSON string), `widget_id` (Int), `weather_temp` (Float), `weather_code` (Int), `weather_timestamp` (Long), `weather_lat` (Float), `weather_lon` (Float)
- [x] T006 [P] Create `AppEntry` data class in `app/src/main/java/com/palma/launcher/data/AppEntry.kt` — fields: packageName, systemName, customName (nullable), isHidden; derived properties: displayName (customName ?? systemName), sortKey (displayName.lowercase()); implement Comparable for alphabetical sorting
- [x] T007 Create `LauncherActivity` in `app/src/main/java/com/palma/launcher/LauncherActivity.kt` — ComponentActivity with `setContent` using e-ink theme, BackHandler that consumes back press on home screen, `onResume`/`onStop` lifecycle hooks for BroadcastReceivers and AppWidgetHost

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 — View Home Screen (Priority: P1) MVP

**Goal**: Display the three-zone home screen with header placeholder, widget placeholder, and alphabetically sorted app list

**Independent Test**: Install APK, set as default launcher, confirm three zones visible with correct e-ink styling

### Implementation for User Story 1

- [x] T008 [US1] Create `HomeScreen` composable in `app/src/main/java/com/palma/launcher/ui/HomeScreen.kt` — Column layout with three zones: HeaderSection (pinned), WidgetSection (pinned), and AppListSection (scrollable via LazyColumn). Header and widget pinned at top, app list scrolls beneath. Disable overscroll via `LocalOverscrollConfiguration provides null`. Use 16dp horizontal padding and 1dp black dividers between zones.
- [x] T009 [P] [US1] Create `HeaderSection` composable in `app/src/main/java/com/palma/launcher/ui/HeaderSection.kt` — static placeholder showing "12:00" in large type with "Date · Battery · Weather" row beneath. Will be replaced with live data in US3.
- [x] T010 [P] [US1] Create `WidgetSection` composable in `app/src/main/java/com/palma/launcher/ui/WidgetSection.kt` — static "Tap to configure widget" placeholder. Full width, centered text. Will be replaced with AppWidgetHost integration in US4.
- [x] T011 [US1] Create `AppListSection` composable in `app/src/main/java/com/palma/launcher/ui/AppListSection.kt` — query installed apps via `PackageManager.queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)`, map to `AppEntry` list, sort alphabetically by `displayName`, display in `LazyColumn` with stable keys (`key = { it.packageName }`). Each item: plain-text app name in 18sp, 48dp min height, 16dp vertical padding. No icons.
- [x] T012 [US1] Wire `HomeScreen` into `LauncherActivity` — pass app list state to HomeScreen, refresh app list on resume. Verify the three-zone layout renders correctly.

**Checkpoint**: Home screen displays with placeholder header, placeholder widget, and real alphabetically sorted app list. Installable as default launcher.

---

## Phase 4: User Story 2 — Launch Apps (Priority: P1) MVP

**Goal**: Tap an app name to launch it. Long-press for context menu with Rename, Hide, Show hidden apps, App info.

**Independent Test**: Tap any app → it launches. Long-press any app → context menu appears with all 4 options. Tap App info → system app info opens.

### Implementation for User Story 2

- [x] T013 [US2] Add tap-to-launch to `AppListSection` in `app/src/main/java/com/palma/launcher/ui/AppListSection.kt` — on tap, create launch intent via `packageManager.getLaunchIntentForPackage(packageName)` and call `startActivity`. Handle null intent gracefully (app cannot be launched).
- [x] T014 [US2] Add long-press context menu to `AppListSection` in `app/src/main/java/com/palma/launcher/ui/AppListSection.kt` — on long-press, show a dropdown or dialog with four options: "Rename", "Hide from home screen", "Show hidden apps", "App info". Use `DropdownMenu` composable styled in pure black/white.
- [x] T015 [US2] Implement "App info" action in `AppListSection` — launch `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with the app's package URI.

**Checkpoint**: Full MVP — launcher displays home screen, launches apps, shows context menu. Rename and Hide actions wired but non-functional until US5/US6.

---

## Phase 5: User Story 3 — Clock, Date, Battery, and Weather Header (Priority: P2)

**Goal**: Header displays large clock, with date + battery + weather in a compact row beneath

**Independent Test**: Clock updates every minute. Date is correct. Battery percentage updates. Weather shows temperature + condition (or is blank if no network).

### Implementation for User Story 3

- [x] T016 [P] [US3] Implement `rememberCurrentTime()` composable helper in `app/src/main/java/com/palma/launcher/ui/HeaderSection.kt` — register BroadcastReceiver for ACTION_TIME_TICK, ACTION_TIME_CHANGED, ACTION_TIMEZONE_CHANGED via DisposableEffect. Return `State<Long>` of current time millis. Set initial value immediately on registration.
- [x] T017 [P] [US3] Implement `rememberBatteryState()` composable helper in `app/src/main/java/com/palma/launcher/ui/HeaderSection.kt` — register BroadcastReceiver for ACTION_BATTERY_CHANGED via DisposableEffect. Return `State<BatteryInfo>` with percentage (Int) and isCharging (Boolean). Calculate percentage as `(EXTRA_LEVEL * 100) / EXTRA_SCALE`.
- [x] T018 [P] [US3] Create `WeatherRepository` in `app/src/main/java/com/palma/launcher/data/WeatherRepository.kt` — fetch weather from Open-Meteo API (`/v1/forecast?current=temperature_2m,weather_code&temperature_unit=fahrenheit&timezone=auto`) via `HttpURLConnection` on `Dispatchers.IO`. Parse JSON response to extract `temperature_2m` and `weather_code`. Map WMO codes to one-word conditions per contracts/open-meteo-api.md lookup table. Cache all fields in PreferencesManager. Return cached data if network fails. Include `isStale()` check (>2 hours).
- [x] T019 [US3] Implement location fetching in `WeatherRepository` — use `FusedLocationProviderClient.getLastLocation()`, fallback to `getCurrentLocation(PRIORITY_LOW_POWER)`, final fallback to hardcoded Las Vegas (36.17, -115.14). Request `ACCESS_COARSE_LOCATION` permission at runtime via `ActivityResultContracts.RequestPermission`. Cache coordinates in PreferencesManager.
- [x] T020 [US3] Update `HeaderSection` in `app/src/main/java/com/palma/launcher/ui/HeaderSection.kt` — replace placeholder with live data: large time display (format "h:mm" 12-hour, large prominent type), row beneath with date (format "EEE, MMM d"), battery percentage ("XX%"), and weather ("68°F Clear"). Use `rememberCurrentTime()`, `rememberBatteryState()`, and weather state from `WeatherRepository`. Trigger weather refresh on resume if stale.

**Checkpoint**: Header shows live clock (updates every minute), current date, battery %, and weather from Open-Meteo API.

---

## Phase 6: User Story 4 — Library Widget Hosting (Priority: P2)

**Goal**: Embed the NeoReader Library widget between header and app list, persisting across restarts

**Independent Test**: Tap placeholder → widget picker appears → select widget → widget renders. Restart device → widget still there.

### Implementation for User Story 4

- [x] T021 [US4] Create `WidgetHostManager` in `app/src/main/java/com/palma/launcher/widget/WidgetHostManager.kt` — wrapper around `AppWidgetHost` (hostId = 1024) and `AppWidgetManager`. Methods: `allocateWidgetId()`, `bindWidget(appWidgetId, componentName)`, `createWidgetView(context, appWidgetId)`, `startListening()`, `stopListening()`. Persist `appWidgetId` via PreferencesManager. Check for `INVALID_APPWIDGET_ID` to determine configured state.
- [x] T022 [US4] Implement widget picker flow in `WidgetHostManager` — allocate ID, attempt `bindAppWidgetIdIfAllowed()`, fallback to `ACTION_APPWIDGET_BIND` intent. If `providerInfo.configure` is non-null, launch configure activity. Handle result via `ActivityResultContracts`. Save bound `appWidgetId` to PreferencesManager on success.
- [x] T023 [US4] Update `WidgetSection` in `app/src/main/java/com/palma/launcher/ui/WidgetSection.kt` — if `appWidgetId == INVALID_APPWIDGET_ID`, show "Tap to configure" placeholder (on tap, trigger widget picker). If configured, embed `AppWidgetHostView` via `AndroidView` composable. Set width to `MATCH_PARENT`, height calculated from widget provider info or default 250dp (4 rows). Pass through tap events to widget.
- [x] T024 [US4] Wire `WidgetHostManager` lifecycle into `LauncherActivity` — call `startListening()` in `onResume()`, `stopListening()` in `onStop()`. On launch, check saved `widget_id` in PreferencesManager: if valid, recreate widget view; if invalid, show placeholder.

**Checkpoint**: NeoReader Library widget embeds in home screen, persists across restarts, taps pass through to widget.

---

## Phase 7: User Story 5 — Hide and Unhide Apps (Priority: P3)

**Goal**: Users can hide apps from the list and restore them via the context menu

**Independent Test**: Long-press → Hide → app disappears. Long-press another → Show hidden apps → restore → app reappears in correct position.

### Implementation for User Story 5

- [x] T025 [US5] Implement "Hide from home screen" action in `AppListSection` — on context menu selection, add the app's packageName to the `hidden_apps` set in PreferencesManager. Update the app list state to filter out hidden apps immediately.
- [x] T026 [US5] Implement "Show hidden apps" dialog in `app/src/main/java/com/palma/launcher/ui/AppListSection.kt` — on context menu selection, show a dialog listing all hidden app names (from `hidden_apps` set, resolved to display names via PackageManager). On tap of a hidden app, remove from `hidden_apps` set and dismiss dialog. App reappears in correct alphabetical position.
- [x] T027 [US5] Implement default hidden apps on first install in `app/src/main/java/com/palma/launcher/data/PreferencesManager.kt` — on first launch (check for a `first_run` boolean), populate `hidden_apps` with the launcher's own package name and a curated set of non-useful system apps (`com.android.htmlviewer`, `com.android.printspooler`, etc.). Set `first_run` to false after initialization.
- [x] T028 [US5] Filter hidden apps from the app list in `AppListSection` — when building the displayed app list, exclude any app whose packageName is in the `hidden_apps` set. Ensure the list updates reactively when the set changes.

**Checkpoint**: Apps can be hidden and restored. Default apps are hidden on first install.

---

## Phase 8: User Story 6 — Rename Apps (Priority: P3)

**Goal**: Users can assign custom names to apps, with custom names used for display and sorting

**Independent Test**: Long-press → Rename → enter name → app shows new name in correct sorted position. Long-press → Reset name → reverts.

### Implementation for User Story 6

- [x] T029 [US6] Implement "Rename" dialog in `app/src/main/java/com/palma/launcher/ui/AppListSection.kt` — on context menu selection, show a text input dialog pre-filled with the current display name. On confirm, save the mapping (packageName → customName) to the `renamed_apps` JSON in PreferencesManager. Update the app list state.
- [x] T030 [US6] Implement "Reset name" action in `AppListSection` — add "Reset name" to the context menu (shown only when an app has a custom name). On selection, remove the packageName entry from `renamed_apps` JSON in PreferencesManager. Revert display to system name.
- [x] T031 [US6] Update app list display and sorting to use custom names — when building the `AppEntry` list, check `renamed_apps` for each packageName. If a custom name exists, set `customName` on the `AppEntry`. Sort by `displayName` (which prefers customName). Truncate long names with ellipsis via `Text(maxLines = 1, overflow = TextOverflow.Ellipsis)`.

**Checkpoint**: Apps can be renamed and reset. Custom names display and sort correctly.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Edge cases, cleanup, and final validation

- [x] T032 Handle empty app list edge case in `AppListSection` — if zero visible apps, display a centered message ("No apps to display").
- [x] T033 [P] Handle widget unavailable edge case in `WidgetSection` — if the widget provider is uninstalled, catch the error from `createView()` and fall back to the "Tap to configure" placeholder.
- [x] T034 [P] Handle network/weather edge cases in `WeatherRepository` — ensure `IOException`, `JSONException`, and null location are all caught gracefully. Weather area displays cached data or nothing (never crashes).
- [x] T035 Verify all e-ink constitution compliance — audit all composables for: no animations, no grays, no gradients, no overscroll, no ripple effects using opacity. Replace any Material3 default ripple with inverted black/white indication.
- [x] T036 Build debug APK via `./gradlew assembleDebug` and validate on-device per quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — first implementable story
- **US2 (Phase 4)**: Depends on US1 (needs app list to add tap/long-press)
- **US3 (Phase 5)**: Depends on Phase 2 — independent of US1/US2 (updates HeaderSection)
- **US4 (Phase 6)**: Depends on Phase 2 — independent of US1/US2 (updates WidgetSection)
- **US5 (Phase 7)**: Depends on US2 (uses context menu infrastructure)
- **US6 (Phase 8)**: Depends on US2 (uses context menu infrastructure)
- **Polish (Phase 9)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: After Foundational — no other story dependencies
- **US2 (P1)**: After US1 — needs the app list UI to attach interactions
- **US3 (P2)**: After Foundational — independent, updates HeaderSection
- **US4 (P2)**: After Foundational — independent, updates WidgetSection
- **US5 (P3)**: After US2 — extends the context menu
- **US6 (P3)**: After US2 — extends the context menu

### Parallel Opportunities

- T005, T006 can run in parallel (different files, Phase 2)
- T009, T010 can run in parallel (different files, US1)
- T016, T017, T018 can run in parallel (different files, US3)
- US3 and US4 can run in parallel after Phase 2 (different sections)
- US5 and US6 can run in parallel after US2 (different features)

---

## Parallel Example: User Story 3

```bash
# Launch all independent US3 tasks together:
Task: "Implement rememberCurrentTime() in HeaderSection.kt"
Task: "Implement rememberBatteryState() in HeaderSection.kt"
Task: "Create WeatherRepository in WeatherRepository.kt"

# Then sequentially:
Task: "Implement location fetching in WeatherRepository"
Task: "Update HeaderSection with live data"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (home screen with app list)
4. Complete Phase 4: User Story 2 (launch + context menu)
5. **STOP and VALIDATE**: Install on Boox Palma, verify launcher works

### Incremental Delivery

1. Setup + Foundational → skeleton app
2. US1 + US2 → MVP launcher (list + launch) — sideload and test
3. US3 → Live header (clock, date, battery, weather) — sideload and test
4. US4 → Library widget embedded — sideload and test
5. US5 + US6 → Hide/rename personalization — sideload and test
6. Polish → Edge cases, compliance audit, final build

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All file paths are relative to repository root
- No test tasks generated (not requested in spec)
