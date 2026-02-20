<!--
Sync Impact Report
===================
Version change: N/A → 1.0.0 (initial ratification)
Modified principles: N/A (initial)
Added sections: Core Principles (4), Technical Constraints, Development Workflow, Governance
Removed sections: N/A
Templates requiring updates:
  - .specify/templates/plan-template.md — ✅ no updates needed (generic gates)
  - .specify/templates/spec-template.md — ✅ no updates needed (generic structure)
  - .specify/templates/tasks-template.md — ✅ no updates needed (generic phases)
  - .specify/templates/agent-file-template.md — ✅ no updates needed (generic)
Follow-up TODOs: None
-->

# Palma Launcher Constitution

## Core Principles

### I. E-Ink First

Every design and implementation decision MUST optimize for e-ink displays.

- All UI rendering MUST use pure black (`#000000`) on pure white (`#FFFFFF`).
  No grays, gradients, or accent colors.
- Animations MUST NOT be used anywhere in the launcher. All transitions
  MUST be immediate.
- Full-screen refreshes MUST be minimized. Prefer partial updates where
  the platform allows.
- Visual feedback (tap states, selection indicators) MUST use high-contrast
  patterns (e.g., inverted black/white) rather than color or opacity changes.

**Rationale**: The Boox Palma is an e-ink device. Designs that work on LCD
displays degrade severely on e-ink — ghosting, slow refresh, unreadable
low-contrast elements. E-ink optimization is not a nice-to-have; it is the
entire reason this launcher exists.

### II. Single Screen

The entire launcher experience MUST live on one vertically scrollable
home screen.

- There MUST be no swipe-to-page, app drawer, or secondary launcher
  surfaces.
- The home screen MUST contain exactly three zones in fixed order:
  status bar (pinned), library widget (pinned), app list (scrollable).
- No folders, grouping, categories, or nested navigation MUST exist on
  the home screen.

**Rationale**: A single-surface design eliminates navigation complexity and
reduces the cognitive load on a device intended for focused reading. It also
minimizes e-ink refreshes caused by page transitions.

### III. Reading-Centric

The launcher MUST treat the Palma as a reading device first.

- The library widget MUST occupy the most prominent position on the home
  screen, directly below the status bar and above the app list.
- Reading-related apps (e.g., NeoReader, Kindle, KOReader) MUST be
  launchable from the home screen with a single tap.
- No launcher feature MUST compete with or distract from the reading
  experience.

**Rationale**: The Boox Palma's primary purpose is reading. The launcher
exists to serve that purpose — not to replicate a general-purpose phone
home screen.

### IV. Calm Interface

The launcher MUST present a quiet, distraction-free home screen.

- Notification badges, counts, or indicators MUST NOT appear anywhere
  in the launcher.
- No attention-grabbing elements (pulsing, blinking, color highlights)
  MUST be used.
- Information density MUST be kept low: generous padding, whitespace
  between elements, and readable font sizes (minimum 14sp).
- The weather display MUST refresh no more frequently than every 2 hours
  to avoid unnecessary visual changes.

**Rationale**: A calm interface respects the user's attention. The launcher
should fade into the background, not demand interaction.

## Technical Constraints

- **Platform**: Android 11+ targeting the Boox Palma hardware exclusively.
- **Language**: Kotlin with Jetpack Compose for all UI. No XML layouts.
- **Widget hosting**: The launcher MUST act as an `AppWidgetHost` to embed
  the Boox NeoReader Library widget. Widget state (`appWidgetId`) MUST
  persist across restarts via `SharedPreferences`.
- **Data persistence**: All user preferences (hidden apps, renamed apps,
  weather location, temperature unit) MUST be stored in `SharedPreferences`.
  No database required for v1.
- **Network**: Only the Open-Meteo API (no API key) for weather. Network
  calls MUST be cached and MUST NOT block the UI thread. If the network
  is unavailable, the launcher MUST display the last cached value or
  show nothing.
- **Scope boundary**: Features listed in the "Out of Scope (v1)" section
  of the specification MUST NOT be implemented. This includes: notification
  badges, gesture navigation, folders, search bar, wallpapers, multiple
  pages, icon packs, and dock/favorites row.
- **Crash recovery**: The launcher MUST NOT interfere with Android's
  default launcher fallback mechanism. The Boox default launcher MUST
  remain installed as a recovery path.

## Development Workflow

- Build with Android Studio or Gradle CLI (`./gradlew assembleDebug`).
- All Compose UI MUST be previewable via `@Preview` annotations where
  practical.
- Each feature increment MUST be testable on-device via sideloaded APK
  before merging.
- Commits MUST be scoped to a single logical change. No mixing unrelated
  changes in one commit.
- NEVER commit or push without explicit direction.

## Governance

This constitution is the authoritative source of design and implementation
rules for the Palma Launcher. All specifications, plans, and task lists
MUST comply with these principles.

- **Amendment process**: Any change to this constitution MUST be documented
  with a version bump, a rationale, and a review of all dependent templates
  for consistency.
- **Versioning**: MAJOR.MINOR.PATCH semantic versioning. MAJOR for
  principle removals or redefinitions; MINOR for new principles or material
  expansions; PATCH for clarifications and wording fixes.
- **Compliance review**: Every plan and spec MUST include a Constitution
  Check section that verifies alignment with these principles before
  implementation begins.

**Version**: 1.0.0 | **Ratified**: 2026-02-20 | **Last Amended**: 2026-02-20
