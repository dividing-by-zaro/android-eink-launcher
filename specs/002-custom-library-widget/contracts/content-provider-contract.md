# Content Provider Contract: Onyx Metadata

**Feature**: 002-custom-library-widget
**Date**: 2026-02-20

## Provider URI

```
content://com.onyx.content.database.ContentProvider/Metadata
```

## Query: Recent Books

**Purpose**: Retrieve the 2 most recently read books for display in the library widget.

### Parameters

| Parameter   | Value                                         |
| ----------- | --------------------------------------------- |
| URI         | `content://com.onyx.content.database.ContentProvider/Metadata` |
| Projection  | `title`, `nativeAbsolutePath`, `progress`, `lastAccess`, `type` |
| Selection   | `lastAccess IS NOT NULL`                      |
| Sort Order  | `lastAccess DESC`                             |
| Limit       | Take first 2 results programmatically         |

### Response Columns

| Column              | Type   | Nullable | Example                                      |
| ------------------- | ------ | -------- | -------------------------------------------- |
| `title`             | String | No       | `"Digital Minimalism"`                       |
| `nativeAbsolutePath`| String | No       | `"/storage/emulated/0/Books/Digital minimalism.epub"` |
| `progress`          | String | Yes      | `"1/48"`, `"27/800"`, `null`                 |
| `lastAccess`        | Long   | No*      | `1771276486672` (filtered non-null)          |
| `type`              | String | No       | `"epub"`, `"pdf"`                            |

### Progress Parsing

```
Input:  "27/800"
Split:  currentPage = 27, totalPages = 800
Output: (27 * 100) / 800 = 3  (integer percentage)
```

Edge cases:
- `null` → no progress available
- `"0/0"` → invalid, treat as no progress
- Non-numeric → parsing failure, treat as no progress

## Intent: Open Library View

```
Action:    Intent.ACTION_MAIN
Component: com.onyx / com.onyx.common.library.ui.LibraryActivity
Flags:     FLAG_ACTIVITY_NEW_TASK
```

## Intent: Open Specific Book

```
Action:    Intent.ACTION_VIEW
Data:      file:///storage/emulated/0/Books/filename.epub
Type:      application/epub+zip (or application/pdf)
Package:   com.onyx.kreader
Flags:     FLAG_ACTIVITY_NEW_TASK
```

MIME type mapping:
| Extension | MIME Type                        |
| --------- | -------------------------------- |
| `epub`    | `application/epub+zip`           |
| `pdf`     | `application/pdf`                |
| `mobi`    | `application/x-mobipocket-ebook` |
| `djvu`    | `image/vnd.djvu`                 |
| `fb2`     | `application/x-fictionbook+xml`  |
| `cbz`     | `application/x-cbz`             |
| Other     | `application/octet-stream`       |
