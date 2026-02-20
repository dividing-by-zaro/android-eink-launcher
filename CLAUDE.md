# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Run `source ~/.zshrc` before any build/adb commands — the sandbox doesn't auto-source the profile. This sets `JAVA_HOME`, `ANDROID_HOME`, and adds `adb` to `PATH`.

```bash
source ~/.zshrc

# Build debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug

# ADB install (if gradlew installDebug fails)
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires platforms;android-34 and build-tools;34.0.0.

**Boox Palma note:** After installing, run `adb shell pm enable com.palma.launcher` — Onyx firmware actively disables third-party apps. Other user-installed apps (Libby, StoryGraph, etc.) may also need `adb shell pm enable <package>` after install or reboot.

## Architecture

Single-activity Android launcher for the Boox Palma e-ink reader. Kotlin + Jetpack Compose, no XML layouts, no ViewModels, no Room/DataStore.

### Three-zone layout

`LauncherActivity` → `HomeScreen` → three pinned/scrollable zones:
- **HeaderSection**: centered live clock, single info row (full date · battery icon+% · weather icon+temp). Battery drawn via Canvas `BatteryIcon`, weather uses Material Icons Extended (`weatherIcon()` maps WMO codes to ImageVectors). System status bar hidden via `WindowInsetsController`.
- **LibrarySection**: Custom composable showing 2 most recently read book covers with progress percentage banners. "All >" link opens Boox system library. Tapping a cover opens the book in NeoReader via FileProvider content URI.
- **AppListSection**: LazyColumn of right-aligned app names with long-press DropdownMenu (Rename, Hide, Show hidden, App info)

### Data flow

All state lives in `LauncherActivity` as `mutableStateOf`/`mutableStateListOf` — no ViewModel. Callbacks flow down through composable parameters. `PreferencesManager` wraps SharedPreferences for all persistence (hidden apps as StringSet, renamed apps as JSON string, cached weather fields). `WeatherRepository` fetches from Open-Meteo API with 2-hour staleness check, uses FusedLocationProviderClient with fallback to Las Vegas coordinates.

### Book data layer

- `BookRepository` queries `content://com.onyx.content.database.ContentProvider/Metadata` for recently read books (title, path, type, progress as "currentPage/totalPages", lastAccess). Returns `List<RecentBook>`.
- `CoverExtractor` extracts cover images from EPUB (ZipFile + XmlPullParser: container.xml → OPF → cover image) and PDF (PdfRenderer page 0). Disk-cached as JPEG in `cacheDir/covers/`.
- `RecentBook.parseProgress()` converts "currentPage/totalPages" string to percentage Int.
- Requires `READ_EXTERNAL_STORAGE` permission to access book files on shared storage.

## E-ink Constraints (Constitution)

These are non-negotiable for this project:
- **Pure black (#000000) and white (#FFFFFF) only** — no grays, no alpha, no gradients
- **No animations** — no overscroll, no transitions, no animated composables
- **No icons in app list** — text only, minimum 14sp font size
- **Minimize recomposition** — e-ink full refreshes are expensive

The theme in `Theme.kt` sets every Material3 color slot to either Color.Black or Color.White. All composables must follow this.

## Key Conventions

- Context menu actions go through `ContextMenuAction` enum in `AppListSection.kt`, handled by `LauncherActivity.handleContextMenu()`
- App list is rebuilt from PackageManager on every `onResume()` — no caching of app list. Uses three-tier discovery: (1) `CATEGORY_LAUNCHER` query, (2) `getLaunchIntentForPackage` fallback, (3) explicit exported activity lookup via `getPackageInfo(GET_ACTIVITIES)`. Apps found via tier 3 (e.g. NeoReader) are launched by explicit `ComponentName` intent. Requires `QUERY_ALL_PACKAGES` permission for full visibility on Android 11+
- Weather WMO code mapping lives in `WeatherData.mapWmoCode()` in `WeatherRepository.kt`; icon mapping in `weatherIcon()` in `HeaderSection.kt`
- Navigation bar and status bar: nav bar set to white/light in `themes.xml`; status bar hidden via `WindowInsetsController` in `LauncherActivity.onCreate()` (must be after `setContent` — DecorView doesn't exist earlier on Android 11)
- First-run logic in `LauncherActivity.onCreate()` sets default hidden apps (launcher itself + system bloat)

## Specs and Design Docs

Feature specifications and implementation plans live in `specs/`. The constitution at `.specify/memory/constitution.md` defines the four design principles (E-Ink First, Single Screen, Reading-Centric, Calm Interface).

## Key Integrations

- **Onyx ContentProvider** (`content://com.onyx.content.database.ContentProvider/Metadata`): Read-only book metadata. Columns: title, nativeAbsolutePath, progress, lastAccess, type. Undocumented but stable.
- **FileProvider** (`${applicationId}.fileprovider`): Used to generate content:// URIs for opening book files in NeoReader. Configured in AndroidManifest.xml with `res/xml/file_paths.xml` granting access to external storage.
- **Boox Library**: `ComponentName("com.onyx", "com.onyx.common.library.ui.LibraryActivity")` — launched from "All >" link.
- **NeoReader**: `com.onyx.kreader` — launched via `ACTION_VIEW` with MIME type for book files.
