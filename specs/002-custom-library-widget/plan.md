# Implementation Plan: Custom Library Widget

**Branch**: `002-custom-library-widget` | **Date**: 2026-02-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-custom-library-widget/spec.md`

## Summary

Replace the stock Boox Library AppWidget with a custom native Compose section that displays the 2 most recently read books as cover thumbnails with progress percentage banners. Book data comes from the Onyx Metadata ContentProvider (`content://com.onyx.content.database.ContentProvider/Metadata`), covers are extracted directly from EPUB/PDF files using Android's built-in APIs, and the section integrates seamlessly into the existing three-zone home screen layout.

## Technical Context

**Language/Version**: Kotlin (matching existing project, targeting Android 11+ / API 30+)
**Primary Dependencies**: Jetpack Compose, Material3, Material Icons Extended (all existing). No new dependencies added.
**Storage**: SharedPreferences (existing) for preferences. App cache directory for cover image cache. Onyx ContentProvider for book data (read-only).
**Testing**: On-device manual testing via sideloaded APK (existing workflow)
**Target Platform**: Boox Palma (Android 11, e-ink display, 824x1648px)
**Project Type**: Mobile (single-activity Android app)
**Performance Goals**: Library section loads and displays within 1 second of home screen appearing
**Constraints**: Pure black/white rendering, no animations, no external dependencies, no grays/alpha/gradients. Cover images are the one exception — displayed as-is since e-ink controller handles photographic dithering.
**Scale/Scope**: 2 book covers displayed, ~40 books on device

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Research Check

| Principle | Status | Notes |
| --------- | ------ | ----- |
| **I. E-Ink First** | PASS | UI chrome is pure black/white. No animations. Cover images displayed as-is (e-ink controller dithers). Minimizes recomposition — data refreshed only on resume. |
| **II. Single Screen** | PASS | Library section replaces the existing widget zone in the same position. No new screens, drawers, or navigation surfaces. |
| **III. Reading-Centric** | PASS | Library widget remains the most prominent position below the header. Book covers are tappable to resume reading. "All >" provides quick access to full library. |
| **IV. Calm Interface** | PASS | No badges, no attention-grabbing elements. Clean cover thumbnails with small progress text. Generous whitespace. |

### Post-Design Check

| Principle | Status | Notes |
| --------- | ------ | ----- |
| **I. E-Ink First** | PASS | All new composables use Color.Black/Color.White only. Cover extraction runs on IO thread — no UI blocking. Bitmap caching prevents redundant recomposition. |
| **II. Single Screen** | PASS | `LibrarySection` replaces `WidgetSection` in `HomeScreen` Column. Same three-zone layout preserved. |
| **III. Reading-Centric** | PASS | Covers are larger and more prominent than the stock widget (2 books instead of 3, no wasted space on titles/format badges). Direct tap-to-read. |
| **IV. Calm Interface** | PASS | Borderless section. Only essential information shown (cover + progress%). Font sizes ≥ 14sp. |

## Project Structure

### Documentation (this feature)

```text
specs/002-custom-library-widget/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: research findings
├── data-model.md        # Phase 1: entity definitions
├── quickstart.md        # Phase 1: build & deploy guide
├── contracts/           # Phase 1: ContentProvider contract
│   └── content-provider-contract.md
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/palma/launcher/
├── LauncherActivity.kt              # MODIFY: add book loading, remove widget host lifecycle
├── data/
│   ├── AppEntry.kt                  # existing, unchanged
│   ├── PreferencesManager.kt        # MODIFY: remove widget ID persistence
│   ├── WeatherRepository.kt         # existing, unchanged
│   ├── RecentBook.kt                # NEW: data class for book metadata + cover
│   ├── BookRepository.kt            # NEW: queries Onyx ContentProvider
│   └── CoverExtractor.kt            # NEW: extracts covers from EPUB/PDF, caches
├── ui/
│   ├── HomeScreen.kt                # MODIFY: replace WidgetSection → LibrarySection
│   ├── HeaderSection.kt             # existing, unchanged
│   ├── LibrarySection.kt            # NEW: header row + 2 book covers + progress banners
│   ├── AppListSection.kt            # existing, unchanged
│   ├── Dialogs.kt                   # existing, unchanged
│   └── theme/
│       └── Theme.kt                 # existing, unchanged
└── widget/
    └── WidgetHostManager.kt         # DELETE: no longer needed
```

**Structure Decision**: Follows the existing single-activity Android architecture exactly. New files are placed in the same `data/` and `ui/` packages. No new packages, modules, or architectural patterns introduced.

## Complexity Tracking

No constitution violations. No complexity justifications needed.
