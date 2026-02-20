# Data Model: Custom Library Widget

**Feature**: 002-custom-library-widget
**Date**: 2026-02-20

## Entities

### RecentBook

Represents a book that has been recently read on the device. Sourced from the Onyx Metadata ContentProvider.

| Field             | Type      | Source                        | Description                                    |
| ----------------- | --------- | ----------------------------- | ---------------------------------------------- |
| `title`           | String    | ContentProvider `title`       | Book title (used for placeholder if no cover)  |
| `filePath`        | String    | ContentProvider `nativeAbsolutePath` | Absolute path to the book file          |
| `fileType`        | String    | ContentProvider `type`        | File extension: `epub`, `pdf`, etc.            |
| `progressPercent` | Int?      | Derived from `progress`       | Reading progress as 0–100, null if unavailable |
| `lastAccess`      | Long      | ContentProvider `lastAccess`  | Timestamp in ms, used for ordering             |
| `coverBitmap`     | Bitmap?   | Extracted from book file      | Cover image, null if extraction fails          |

**Derivation rules**:
- `progressPercent`: Parse `progress` column (`"currentPage/totalPages"`) → `(currentPage * 100) / totalPages`. Null if `progress` is null, empty, or `totalPages` is 0.
- `coverBitmap`: Extracted asynchronously from the book file. Cached to disk after first extraction.

**Ordering**: `lastAccess DESC` — most recently read book first.

**Cardinality**: Exactly 0, 1, or 2 books displayed at any time.

### CoverCache Entry

Represents a cached cover image on disk to avoid re-extracting from book files on every resume.

| Field          | Type   | Description                                        |
| -------------- | ------ | -------------------------------------------------- |
| `cacheKey`     | String | Hash of `filePath + lastModified` for invalidation |
| `cacheFile`    | File   | JPEG file in app cache directory                   |

**Invalidation**: If the book file's `lastModified` timestamp changes (e.g., re-synced), the cache key changes and the cover is re-extracted.

## State Management

All state follows the existing pattern: `mutableStateOf` / `mutableStateListOf` in `LauncherActivity`, passed down to composables as parameters.

| State                     | Type                          | Lifecycle                    |
| ------------------------- | ----------------------------- | ---------------------------- |
| `recentBooks`             | `List<RecentBook>`            | Refreshed on every `onResume()` |
| Cover bitmaps (per book)  | `Bitmap?` within `RecentBook` | Loaded async after query, cached to disk |

## Data Flow

```
onResume()
  → query ContentProvider (IO thread)
    → filter: lastAccess IS NOT NULL
    → sort: lastAccess DESC
    → take: first 2
  → for each book:
    → check cover cache
      → hit: load cached JPEG
      → miss: extract from EPUB/PDF file → cache as JPEG
  → update recentBooks state
  → Compose recomposes LibrarySection
```
