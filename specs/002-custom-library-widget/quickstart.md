# Quickstart: Custom Library Widget

**Feature**: 002-custom-library-widget
**Date**: 2026-02-20

## Prerequisites

- Android Studio or Gradle CLI
- Boox Palma connected via USB with ADB access
- `source ~/.zshrc` to set `JAVA_HOME`, `ANDROID_HOME`, and `adb` in PATH

## Build & Deploy

```bash
source ~/.zshrc
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After install, re-enable if Boox firmware disables the app:
```bash
adb shell pm enable com.palma.launcher
```

## Verify on Device

1. Open at least 2 books in NeoReader, read a few pages each
2. Press Home to return to the Palma Launcher
3. Confirm the Library section shows:
   - "Library" header with file icon on the left, "All >" on the right
   - Two book covers side by side with progress % banners
   - No border around the section
4. Tap a book cover → should open that book in NeoReader
5. Tap "All >" → should open the Boox Library view

## Useful ADB Queries

```bash
# Query recent books from Onyx Metadata ContentProvider
adb shell content query --uri "content://com.onyx.content.database.ContentProvider/Metadata" | head -5

# Check reading statistics
adb shell content query --uri content://com.onyx.kreader.statistics.provider/OnyxStatisticsModel | head -5

# List books on device
adb shell ls /storage/emulated/0/Books/

# Launch NeoReader library directly
adb shell am start -n com.onyx/com.onyx.common.library.ui.LibraryActivity
```

## Key Files to Modify

| File | Change |
| ---- | ------ |
| `ui/LibrarySection.kt` | **NEW** — Custom library composable (header + 2 book covers + progress banners) |
| `data/BookRepository.kt` | **NEW** — Queries Onyx ContentProvider, parses progress, returns `RecentBook` list |
| `data/CoverExtractor.kt` | **NEW** — Extracts covers from EPUB (ZipFile+XML) and PDF (PdfRenderer), caches to disk |
| `data/RecentBook.kt` | **NEW** — Data class for recent book metadata |
| `ui/HomeScreen.kt` | **MODIFY** — Replace `WidgetSection` with `LibrarySection` |
| `LauncherActivity.kt` | **MODIFY** — Add book data loading in `onResume`, remove widget host lifecycle, add intents for book open / library open |
| `data/PreferencesManager.kt` | **MODIFY** — Remove widget ID persistence (optional cleanup) |
| `widget/WidgetHostManager.kt` | **DELETE** — No longer needed |
| `ui/WidgetSection.kt` | **DELETE** — Replaced by LibrarySection |

## Architecture Notes

- **No new dependencies**: Uses only Android built-in APIs (`ContentResolver`, `ZipFile`, `XmlPullParser`, `PdfRenderer`, `BitmapFactory`)
- **Same patterns as existing code**: All state in `LauncherActivity` as `mutableStateOf`, passed down to composables. Data loaded on `onResume()`. No ViewModel, no Room.
- **E-ink compliance**: Pure black/white UI chrome. Book cover images are displayed as-is (e-ink controller handles grayscale dithering for photographic content).
