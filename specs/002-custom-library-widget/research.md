# Research: Custom Library Widget

**Feature**: 002-custom-library-widget
**Date**: 2026-02-20

## R1: How to Access Recent Book Data on Boox Palma

**Decision**: Query the Onyx Metadata ContentProvider directly.

**Rationale**: The Boox system exposes a fully queryable ContentProvider at `content://com.onyx.content.database.ContentProvider/Metadata` that returns all book metadata without requiring root. This was confirmed by running `adb shell content query` against the connected Palma device. The KOReader `onyxbooxsync` plugin also proves this approach works from third-party apps.

**Alternatives considered**:
- **Scan book files by modification date** (using `File.lastModified()`): Rejected because file modification time doesn't reflect when a book was last *read* — only when it was last copied/downloaded. The ContentProvider provides `lastAccess` which is the actual last-read timestamp.
- **Android MediaStore**: Rejected for the same reason — tracks file metadata, not reading activity.
- **OnyxSdk `onyxsdk-data` artifact**: Rejected — last released in 2021, Onyx officially stated third-party data access is "not supported any more." The ContentProvider still works regardless.

**Device-confirmed schema** (key columns from `content://com.onyx.content.database.ContentProvider/Metadata`):

| Column              | Type    | Description                                              |
| ------------------- | ------- | -------------------------------------------------------- |
| `title`             | String  | Book title (parsed from EPUB metadata)                   |
| `authors`           | String  | Author(s)                                                |
| `nativeAbsolutePath`| String  | Full file path, e.g. `/storage/emulated/0/Books/foo.epub`|
| `progress`          | String  | Format: `"currentPage/totalPages"` (e.g. `"1/48"`)      |
| `lastAccess`        | Long    | Unix timestamp in ms of last read. NULL = never opened   |
| `readingStatus`     | Integer | `0`=unread, `1`=reading, `2`=finished                   |
| `type`              | String  | File type: `epub`, `pdf`, etc.                           |
| `coverUrl`          | String  | Always NULL on this device — not populated by system     |
| `hashTag`           | String  | MD5 hash of the book file                                |
| `size`              | Long    | File size in bytes                                       |
| `name`              | String  | Original filename                                        |

**Query strategy**: `SELECT * FROM Metadata WHERE lastAccess IS NOT NULL ORDER BY lastAccess DESC LIMIT 2` — translates to ContentResolver query with selection `"lastAccess IS NOT NULL"`, sort order `"lastAccess DESC"`, and manually taking the first 2 results.

**Risk**: This is an undocumented API that could change with firmware updates. Mitigation: fall back gracefully to an empty widget state if the query fails or returns no results.

---

## R2: How to Get Book Cover Images

**Decision**: Extract cover images directly from EPUB/PDF files using Android's built-in APIs (no external libraries).

**Rationale**: The `coverUrl` column in the Metadata ContentProvider is NULL for all books on the device — the Boox system does not pre-cache cover URLs in a publicly accessible location. No cover thumbnail cache was found on shared storage. Extracting covers from the actual book files is the only reliable approach.

**Alternatives considered**:
- **Boox system cover cache** (e.g., `/data/data/com.onyx.kreader/cache/`): Rejected — requires root access, which is not available.
- **External libraries (epublib, Readium)**: Rejected — adds unnecessary dependency weight. EPUB cover extraction is ~80 lines of code using `ZipFile` + `XmlPullParser` (both built into Android).

**Implementation approach**:
- **EPUB**: Open as `ZipFile` → parse `META-INF/container.xml` to find OPF path → parse OPF to find cover image (EPUB 2: `<meta name="cover">` → manifest lookup; EPUB 3: `<item properties="cover-image">`; fallback: heuristic match on "cover" in item id/href) → extract and decode the image with `BitmapFactory` using `inSampleSize` subsampling.
- **PDF**: Use `android.graphics.pdf.PdfRenderer` (built-in since API 21) to render page 0 as a bitmap thumbnail.
- **Caching**: Cache extracted cover bitmaps as JPEG files in the app's cache directory, keyed by file path + lastModified hash. Only 2 covers need to be in memory at any time.
- **Performance**: EPUB cover extraction: 10–50ms; PDF render: 50–150ms. Two books comfortably under the 1-second target.
- **Threading**: Run extraction on `Dispatchers.IO` via `LaunchedEffect` in Compose.

---

## R3: How to Calculate Reading Progress Percentage

**Decision**: Parse the `progress` column from the Metadata ContentProvider and calculate `(currentPage / totalPages) * 100`.

**Rationale**: The `progress` field is formatted as `"currentPage/totalPages"` (e.g., `"27/800"` = 3%, `"1/48"` = 2%). This is the same data the stock widget uses for its progress banner.

**Edge cases**:
- `progress` is NULL → book never opened, hide progress banner
- `totalPages` is 0 → avoid division by zero, hide progress banner
- Parsing failure → hide progress banner

---

## R4: How to Launch the Full Library View ("All >" Button)

**Decision**: Launch `com.onyx/.common.library.ui.LibraryActivity` via explicit ComponentName intent.

**Rationale**: Device dump confirmed this is the only library-related activity in the `com.onyx` package. This is the same library UI the stock widget's "All >" links to.

**Intent construction**:
```kotlin
Intent(Intent.ACTION_MAIN).apply {
    component = ComponentName("com.onyx", "com.onyx.common.library.ui.LibraryActivity")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```

**Fallback**: If the activity is unavailable (unlikely on a stock Boox device), catch `ActivityNotFoundException` and silently do nothing.

---

## R5: How to Open a Specific Book (Tap on Cover)

**Decision**: Use `ACTION_VIEW` intent with the book's file URI and MIME type, targeting NeoReader.

**Rationale**: NeoReader registers as a handler for EPUB/PDF files. Sending an `ACTION_VIEW` intent opens the book at the user's last-read position (NeoReader persists reading position internally).

**Intent construction**:
```kotlin
Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(Uri.fromFile(File(bookPath)), mimeTypeForExtension(bookType))
    setPackage("com.onyx.kreader")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```

**Fallback**: If NeoReader can't handle the intent, fall back to a generic `ACTION_VIEW` without specifying a package, letting Android's chooser pick a reader.

---

## R6: Replacing the Stock Widget Infrastructure

**Decision**: Replace the `WidgetSection` composable with a new `LibrarySection` composable. Remove the `AppWidgetHost` dependency entirely.

**Rationale**: The custom library section is a native Compose composable — no `AppWidgetHost`, no `AppWidgetHostView`, no widget picker. The `WidgetHostManager` class, `WidgetSection.kt`, and related widget state in `LauncherActivity` and `PreferencesManager` become dead code for this feature.

**Approach**: Keep the same architectural position in `HomeScreen` (between HeaderSection and AppListSection). The new `LibrarySection` composable receives data and callbacks as parameters, consistent with the existing stateless-composable pattern.
