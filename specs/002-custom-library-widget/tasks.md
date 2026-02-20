# Tasks: Custom Library Widget

**Input**: Design documents from `/specs/002-custom-library-widget/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: No new project initialization needed — this is an existing Android project. Setup phase handles only the data class foundation.

- [x] T001 Create RecentBook data class in `app/src/main/java/com/palma/launcher/data/RecentBook.kt` with fields: title (String), filePath (String), fileType (String), progressPercent (Int?), lastAccess (Long), coverBitmap (Bitmap?). Include a companion helper `parseProgress(raw: String?): Int?` that splits "currentPage/totalPages" and returns `(currentPage * 100) / totalPages`, returning null for null/empty/zero-denominator/non-numeric inputs.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Data layer that MUST be complete before any UI story can be implemented. Two independent modules: book metadata retrieval and cover image extraction.

- [x] T002 [P] Implement BookRepository in `app/src/main/java/com/palma/launcher/data/BookRepository.kt`. Single public method `getRecentBooks(context: Context, limit: Int = 2): List<RecentBook>` that queries `content://com.onyx.content.database.ContentProvider/Metadata` via ContentResolver with projection `[title, nativeAbsolutePath, progress, lastAccess, type]`, selection `"lastAccess IS NOT NULL"`, sortOrder `"lastAccess DESC"`. Take first `limit` cursor rows. Map each row to RecentBook (coverBitmap left null — filled later). Wrap entire query in try-catch returning empty list on failure (ContentProvider may be unavailable).

- [x] T003 [P] Implement CoverExtractor in `app/src/main/java/com/palma/launcher/data/CoverExtractor.kt` with three public methods:
  1. `getOrExtractCover(context: Context, filePath: String, fileType: String): Bitmap?` — checks disk cache first, then extracts.
  2. `extractEpubCover(file: File, maxWidth: Int = 300, maxHeight: Int = 450): Bitmap?` — opens EPUB as ZipFile, parses `META-INF/container.xml` to find OPF path, parses OPF to find cover image (EPUB 2: `<meta name="cover">` → manifest lookup; EPUB 3: `<item properties="cover-image">`; fallback: heuristic "cover" in item id/href), extracts and decodes with BitmapFactory using inSampleSize subsampling and RGB_565 config.
  3. `extractPdfCover(file: File, maxWidth: Int = 300, maxHeight: Int = 450): Bitmap?` — uses PdfRenderer to render page 0, scales to fit within max dimensions, calls `bitmap.eraseColor(Color.WHITE)` before rendering.

  Disk cache: JPEG files in `context.cacheDir/covers/`, keyed by hash of `filePath + file.lastModified()`. Use `decodeSampledBitmap` helper with two-pass BitmapFactory decode (bounds-only pass → calculate inSampleSize → decode pass).

**Checkpoint**: Data layer ready — BookRepository returns metadata, CoverExtractor provides cover bitmaps. UI stories can now begin.

---

## Phase 3: User Story 1 — View Recently Read Books at a Glance (Priority: P1)

**Goal**: Display the 2 most recently read books as cover thumbnails with progress percentage banners on the home screen, with a "Library" header row.

**Independent Test**: Open 2+ books in NeoReader, return to launcher. Verify correct books appear with covers and progress percentages. Verify 0-book and 1-book states display gracefully.

### Implementation for User Story 1

- [x] T004 [US1] Create LibrarySection composable in `app/src/main/java/com/palma/launcher/ui/LibrarySection.kt`. Parameters: `recentBooks: List<RecentBook>`, `onAllBooksClick: () -> Unit`, `onBookClick: (RecentBook) -> Unit`, `modifier: Modifier`. Layout:
  - Header row: file/document icon (Material Icons `Description`) + "Library" text on left, clickable "All >" text on right. Both in black, minimum 14sp.
  - Book covers row: `Row` with 2 book cover slots. Each slot is a `Box` containing:
    - `Image(bitmap.asImageBitmap())` with `ContentScale.Fit` if cover exists, or a placeholder Box (black border, white fill, book title text centered) if null.
    - Progress banner: small `Box` overlaid at bottom-left of cover with black background, white text showing "${progressPercent}%". Only shown when `progressPercent != null`.
  - Edge cases: 0 books → show header + "No recent books" text. 1 book → show single cover, second slot empty.
  - No border, no outline, no padding around the outer container (edge-to-edge like existing WidgetSection).
  - All colors: Color.Black and Color.White only. No animations.

- [x] T005 [US1] Modify HomeScreen in `app/src/main/java/com/palma/launcher/ui/HomeScreen.kt`:
  - Replace `WidgetSection` import and composable call with `LibrarySection`.
  - Update HomeScreen parameters: remove `widgetView: AppWidgetHostView?` and `onWidgetConfigureClick`, add `recentBooks: List<RecentBook>`, `onAllBooksClick: () -> Unit`, `onBookClick: (RecentBook) -> Unit`.
  - Pass new parameters through to LibrarySection in the same position (between HeaderSection and AppListSection).

- [x] T006 [US1] Modify LauncherActivity in `app/src/main/java/com/palma/launcher/LauncherActivity.kt`:
  - Add state: `val recentBooksState = mutableStateOf<List<RecentBook>>(emptyList())`.
  - Add `refreshRecentBooks()` method: calls `BookRepository.getRecentBooks(this)` on `Dispatchers.IO`, then for each book calls `CoverExtractor.getOrExtractCover()`, updates each book's coverBitmap, and sets `recentBooksState.value` on the main thread.
  - Call `refreshRecentBooks()` in `onResume()` (alongside existing `refreshAppList()`).
  - Remove widget-related state: `widgetViewState`, `pendingWidgetId`, `widgetHostManager` field, `widgetPickerLauncher`, `widgetConfigureLauncher`.
  - Remove `onResume()` call to `widgetHostManager.startListening()` and `onStop()` call to `widgetHostManager.stopListening()`.
  - Remove `startWidgetPicker()`, `loadWidget()`, and related helper methods.
  - Update `setContent` block: pass `recentBooksState.value` to HomeScreen instead of `widgetView` and `onWidgetConfigureClick`. Wire `onAllBooksClick` and `onBookClick` as empty lambdas for now (wired in US2/US3).

**Checkpoint**: Home screen shows 2 recent book covers with progress banners. "All >" visible but not yet clickable. Covers tap-able but no action yet. Edge cases (0, 1 book, missing covers) handled visually.

---

## Phase 4: User Story 2 — Navigate to Full Library (Priority: P1)

**Goal**: Tapping "All >" opens the Boox system's full library view.

**Independent Test**: Tap "All >" on the home screen. Boox Library activity opens showing the full book collection.

### Implementation for User Story 2

- [x] T007 [US2] Add `openLibrary()` method to LauncherActivity in `app/src/main/java/com/palma/launcher/LauncherActivity.kt`: creates an Intent with `ACTION_MAIN`, `ComponentName("com.onyx", "com.onyx.common.library.ui.LibraryActivity")`, `FLAG_ACTIVITY_NEW_TASK`. Wraps `startActivity()` in try-catch for `ActivityNotFoundException` (silently ignores if library unavailable). Wire this method as the `onAllBooksClick` callback passed to HomeScreen.

**Checkpoint**: "All >" opens Boox Library. Combined with US1, the library section is now fully visual and navigable.

---

## Phase 5: User Story 3 — Tap a Book Cover to Resume Reading (Priority: P2)

**Goal**: Tapping a book cover opens that book in NeoReader at the user's last-read position.

**Independent Test**: Tap a book cover in the library section. NeoReader opens to the correct book at the last-read page.

### Implementation for User Story 3

- [x] T008 [US3] Add `openBook(book: RecentBook)` method to LauncherActivity in `app/src/main/java/com/palma/launcher/LauncherActivity.kt`: creates an `ACTION_VIEW` intent with `setDataAndType(Uri.fromFile(File(book.filePath)), mimeType)` where mimeType is mapped from `book.fileType` (epub→`application/epub+zip`, pdf→`application/pdf`, mobi→`application/x-mobipocket-ebook`, default→`application/octet-stream`). Sets `setPackage("com.onyx.kreader")`. Wraps in try-catch: on `ActivityNotFoundException`, retries without `setPackage` to let Android chooser handle it; on any other exception, silently ignores. Also handles deleted files: check `File(book.filePath).exists()` before launching, silently ignore if missing. Wire this method as the `onBookClick` callback passed to HomeScreen.

**Checkpoint**: All three user stories are now fully functional. Book covers display with progress, "All >" opens library, tapping a cover opens the book.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Remove dead code, clean up preferences, verify edge cases on device.

- [x] T009 [P] Delete `app/src/main/java/com/palma/launcher/widget/WidgetHostManager.kt` and `app/src/main/java/com/palma/launcher/ui/WidgetSection.kt`. Remove the `widget/` directory if empty after deletion.

- [x] T010 [P] Clean up PreferencesManager in `app/src/main/java/com/palma/launcher/data/PreferencesManager.kt`: remove `getWidgetId()`, `setWidgetId()`, and the `widget_id` key constant. Remove any widget-related imports.

- [x] T011 Build and deploy to device: `source ~/.zshrc && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell pm enable com.palma.launcher`. Run quickstart.md validation checklist on device.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on T001 (RecentBook data class). T002 and T003 can run in parallel.
- **Phase 3 (US1)**: Depends on Phase 2 completion. T004 can start once T001 is done (UI only). T005 depends on T004. T006 depends on T002, T003, T004, T005.
- **Phase 4 (US2)**: Depends on T006 (LauncherActivity updated with HomeScreen wiring)
- **Phase 5 (US3)**: Depends on T006 (LauncherActivity updated with HomeScreen wiring). Can run in parallel with US2.
- **Phase 6 (Polish)**: Depends on all user stories being complete. T009 and T010 can run in parallel.

### User Story Dependencies

- **US1 (P1)**: Depends on Foundational phase only. Core MVP.
- **US2 (P1)**: Depends on US1 (LibrarySection and HomeScreen must exist). Adds "All >" navigation.
- **US3 (P2)**: Depends on US1 (LibrarySection and HomeScreen must exist). Can be done in parallel with US2. Adds tap-to-read.

### Parallel Opportunities

- **Phase 2**: T002 (BookRepository) and T003 (CoverExtractor) are independent files — run in parallel.
- **Phase 4 + 5**: T007 (US2) and T008 (US3) modify the same file (LauncherActivity) but different methods — can be combined in a single task execution or done sequentially.
- **Phase 6**: T009 (delete dead files) and T010 (clean PreferencesManager) are independent — run in parallel.

---

## Parallel Example: Phase 2

```text
# These two tasks touch completely different files and can run simultaneously:
Task T002: "Implement BookRepository in data/BookRepository.kt"
Task T003: "Implement CoverExtractor in data/CoverExtractor.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: T001 (RecentBook data class)
2. Complete Phase 2: T002 + T003 in parallel (data layer)
3. Complete Phase 3: T004 → T005 → T006 (LibrarySection UI + integration)
4. **STOP and VALIDATE**: Deploy to Palma, verify covers and progress banners display correctly
5. If good: proceed to US2 + US3

### Incremental Delivery

1. T001 → T002 + T003 → T004 → T005 → T006 → **MVP deployed** (covers display)
2. T007 → **"All >" works** (navigate to full library)
3. T008 → **Tap-to-read works** (open books from launcher)
4. T009 + T010 → **Clean codebase** (dead widget code removed)
5. T011 → **Final on-device validation**

---

## Notes

- No test tasks generated — spec does not request automated tests. Validation is on-device manual testing per quickstart.md.
- T006 is the largest task (LauncherActivity modification) — it both adds new wiring and removes old widget infrastructure. Consider splitting the removal into Phase 6 if the diff gets unwieldy.
- The ContentProvider query (T002) is the riskiest technical element — if it fails on a future firmware update, the widget degrades gracefully to the empty state.
- Cover extraction (T003) handles EPUB and PDF only. Other formats (MOBI, DJVU, FB2, CBZ) will show the placeholder. This covers the vast majority of e-books.
