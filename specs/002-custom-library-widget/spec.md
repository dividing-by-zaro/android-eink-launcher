# Feature Specification: Custom Library Widget

**Feature Branch**: `002-custom-library-widget`
**Created**: 2026-02-20
**Status**: Draft
**Input**: User description: "I'd like to create my own custom widget for the Library portion of the launcher rather than relying on the one provided by the default software. I like the overall idea here of showing the most recently used books. However, I'd like to show only the most recent 2, and just the book covers themselves + the banner that says the % complete (no title, no file format). We should keep the ability to click on the 'All >' and have the title of the widget 'Library' with the file icon, that looks nice, but remove the border around the widget."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Recently Read Books at a Glance (Priority: P1)

The user unlocks their Boox Palma and sees their two most recently read books displayed as cover thumbnails directly on the home screen, each with a small banner showing reading progress as a percentage. This replaces the stock Boox Library widget with a cleaner, borderless presentation that fits the launcher's e-ink aesthetic.

**Why this priority**: This is the core value of the feature — surfacing reading progress without launching any app. It replaces the stock widget's three-book layout (with titles, file format badges, and a border) with a minimal two-book layout showing only covers and progress.

**Independent Test**: Can be fully tested by opening books in NeoReader, returning to the home screen, and verifying that the correct two most recent books appear with accurate cover images and progress percentages.

**Acceptance Scenarios**:

1. **Given** the user has read 2+ books on the device, **When** they view the home screen, **Then** they see exactly 2 book cover thumbnails with a progress percentage banner on each
2. **Given** the user finishes a reading session and returns to the home screen, **When** the launcher resumes, **Then** the book list updates to reflect the most recently read books in correct order (most recent first)
3. **Given** a book has no cover image available, **When** it appears in the widget, **Then** a recognizable placeholder is shown instead of a broken or blank image

---

### User Story 2 - Navigate to Full Library (Priority: P1)

The user taps "All >" in the widget header to open the Boox system's full library view, providing quick access to their complete book collection.

**Why this priority**: This is equally critical — the custom widget shows only 2 books, so easy access to the full library is essential for the feature to be usable.

**Independent Test**: Can be tested by tapping the "All >" button and verifying the Boox Library/NeoReader app opens to its library view.

**Acceptance Scenarios**:

1. **Given** the user is on the home screen, **When** they tap "All >", **Then** the Boox NeoReader library view opens
2. **Given** NeoReader is not installed or is disabled, **When** the user taps "All >", **Then** nothing happens (no crash) and the tap is silently ignored

---

### User Story 3 - Tap a Book Cover to Resume Reading (Priority: P2)

The user taps one of the two book cover thumbnails to open that book directly in the reader, resuming where they left off.

**Why this priority**: A natural interaction — tapping a visible book should open it. Less critical than display and navigation but highly expected.

**Independent Test**: Can be tested by tapping a book cover and verifying the correct book opens in NeoReader at the last-read position.

**Acceptance Scenarios**:

1. **Given** a book cover is displayed in the widget, **When** the user taps it, **Then** the book opens in NeoReader (or the associated reader app) at the user's last-read position
2. **Given** the book file has been deleted since the widget last loaded, **When** the user taps its cover, **Then** the tap is handled gracefully (no crash)

---

### Edge Cases

- What happens when the user has read only 1 book? Display that single book; the second slot is empty/hidden.
- What happens when the user has read 0 books (fresh device)? Show the "Library" header and "All >" link, but the cover area is blank or shows a brief message like "No recent books."
- What happens when a book's cover image is corrupted or missing? Show a placeholder (solid black-on-white rectangle with a book icon or the book's title text as fallback).
- What happens when reading progress data is unavailable for a book? Hide the progress banner for that book rather than showing "0%."
- What happens when the launcher resumes and the book database has changed (book deleted, new book added)? The widget refreshes its data on every launcher resume, consistent with how the app list currently works.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The launcher MUST replace the stock Boox Library AppWidget with a custom native section built into the home screen
- **FR-002**: The custom library section MUST display exactly the 2 most recently read books, determined by last-opened timestamp
- **FR-003**: Each book MUST be displayed as its cover thumbnail image only — no title text below or on top of the cover, no file format badge
- **FR-004**: Each book cover MUST show a small banner overlay indicating reading progress as a whole-number percentage (e.g., "55%")
- **FR-005**: The section header MUST display a file/document icon followed by the text "Library" on the left side
- **FR-006**: The section header MUST display a tappable "All >" link on the right side that opens the Boox system's full library view
- **FR-007**: The library section MUST NOT have a visible border or outline around it
- **FR-008**: The library section MUST render in pure black and white only, consistent with the launcher's e-ink design constraints (no grays, no gradients, no alpha)
- **FR-009**: The library section MUST refresh its book data on every launcher resume (consistent with existing app list refresh pattern)
- **FR-010**: Tapping a book cover MUST open that book in its associated reader application
- **FR-011**: The library section MUST handle gracefully: 0 books (empty state), 1 book (single cover), missing covers (placeholder), missing progress data (hidden banner), and deleted book files (no crash)
- **FR-012**: The library section MUST NOT use any animations or transitions

### Key Entities

- **Recent Book**: A book that has been recently read on the device. Key attributes: cover image, reading progress percentage, file path or identifier for launching, last-opened timestamp.
- **Library Section**: The custom home screen zone that replaces the stock widget. Occupies the same position (between header and app list) with the same structural role.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The two most recently read books are correctly identified and displayed on every home screen view, matching the recency order shown in the Boox system's own library
- **SC-002**: Reading progress percentages displayed in the widget match the actual progress reported by the reading application within 1 percentage point
- **SC-003**: Tapping "All >" successfully opens the full library view 100% of the time when NeoReader is available
- **SC-004**: The library section renders with no gray tones, no borders, no animations — fully compliant with the e-ink constitution
- **SC-005**: The library section loads and displays book data within 1 second of the home screen appearing
- **SC-006**: All edge cases (0 books, 1 book, missing covers, deleted files) are handled without crashes or visual glitches

## Assumptions

- The Boox Palma stores recent book data in a location accessible to other apps on the device (ContentProvider, shared database, or file system). The exact data source will be determined during planning/implementation.
- Book cover images are stored on the device file system or can be extracted from book files (EPUB, PDF, etc.).
- Reading progress (percentage) is tracked by the Boox system or NeoReader and is accessible to other apps.
- The "All >" action will launch NeoReader's library activity, discovered via the same app discovery mechanism already used by the launcher.
- The existing `WidgetSection` composable and `WidgetHostManager` infrastructure will be replaced by this custom section for the library zone. The `AppWidgetHost` approach is no longer needed for the library.
