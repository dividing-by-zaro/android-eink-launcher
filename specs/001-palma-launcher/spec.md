# Feature Specification: Palma Launcher v1

**Feature Branch**: `001-palma-launcher`
**Created**: 2026-02-20
**Status**: Draft
**Input**: User description: "Palma Launcher v1 — minimal e-ink optimized Android launcher for the Boox Palma"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Home Screen (Priority: P1)

As a Boox Palma user, I open my device and see a single, clean home
screen with the time displayed large at the top, the date and battery
and weather in a compact row beneath it, the NeoReader library widget,
and a list of my installed apps — all rendered in high-contrast black
on white for optimal e-ink readability.

**Why this priority**: This is the foundational experience. Without a
functional home screen, the launcher has no value. Every other feature
builds on top of this surface.

**Independent Test**: Install the APK, set it as default launcher, and
confirm the home screen displays the three zones (header, library
widget placeholder, app list) with correct typography and layout.

**Acceptance Scenarios**:

1. **Given** the launcher is set as default, **When** the user presses
   the home button, **Then** a single vertically scrollable screen
   appears with a header (large clock, date, battery, weather), a
   library widget area, and an alphabetically sorted app list.
2. **Given** the home screen is displayed, **When** the user scrolls
   down, **Then** the header and widget remain pinned at the top
   while the app list scrolls.
3. **Given** the home screen is displayed, **When** the user looks at
   any element, **Then** all text and dividers are pure black on pure
   white with no animations, gradients, or gray tones.

---

### User Story 2 - Launch Apps (Priority: P1)

As a Boox Palma user, I tap an app name in the list to launch it, and
I long-press an app name to access a context menu with rename, hide,
unhide, and app info options.

**Why this priority**: Launching apps is the core utility of any
launcher. Without it the launcher is non-functional.

**Independent Test**: Tap any app name and confirm the app opens.
Long-press an app name and confirm the context menu appears with
Rename, Hide, and App Info options.

**Acceptance Scenarios**:

1. **Given** the app list is visible, **When** the user taps an app
   name, **Then** that app launches immediately.
2. **Given** the app list is visible, **When** the user long-presses
   an app name, **Then** a context menu appears with "Rename", "Hide
   from home screen", "Show hidden apps", and "App info" options.
3. **Given** the context menu is open, **When** the user taps "App
   info", **Then** the system app info screen opens for that app.

---

### User Story 3 - Clock, Date, Battery, and Weather Header (Priority: P2)

As a Boox Palma user, I glance at the top of my home screen and see the
current time displayed large, with the date, battery percentage, and
weather shown in a compact, elegant row beneath it.

**Why this priority**: The header provides at-a-glance utility that
makes the launcher more than just an app list. However, the launcher is
still usable without it.

**Independent Test**: Verify the clock updates every minute. Verify
the date, battery percentage, and weather (temperature + condition)
are displayed in a single row beneath the time.

**Acceptance Scenarios**:

1. **Given** the home screen is displayed, **When** the user looks at
   the top of the screen, **Then** the current time is shown in large
   type, prominently.
2. **Given** the home screen is displayed, **When** the user looks
   beneath the time, **Then** the current date, battery percentage,
   and weather (temperature + condition) are displayed in a single
   compact row.
3. **Given** the device has no network, **When** the home screen loads,
   **Then** the last cached weather is shown, or the weather portion is
   blank.
4. **Given** the battery is charging, **When** the home screen is
   displayed, **Then** the battery percentage still displays accurately.

---

### User Story 4 - Library Widget Hosting (Priority: P2)

As a Boox Palma user, I see the Boox NeoReader Library widget embedded
in my home screen so I can browse and open books directly from the
launcher.

**Why this priority**: The reading-centric principle demands the library
widget be prominent. However, the launcher is still functional as an app
list without the widget, making this P2.

**Independent Test**: On first launch, configure the library widget
via the widget picker. Verify the widget renders between the header
and app list. Verify tapping books within the widget opens them.
Verify the widget persists across device restarts.

**Acceptance Scenarios**:

1. **Given** no widget is configured, **When** the launcher displays
   the widget zone, **Then** a "Tap to configure" placeholder is shown.
2. **Given** the placeholder is visible, **When** the user taps it,
   **Then** the widget selection and binding flow begins.
3. **Given** a widget is configured, **When** the home screen loads,
   **Then** the library widget renders at full screen width between the
   header and app list, sized as a 2x4 widget.
4. **Given** a widget is configured, **When** the device restarts,
   **Then** the widget reappears without requiring reconfiguration.
5. **Given** a widget is configured, **When** the user taps a book in
   the widget, **Then** that book opens in the reader app.

---

### User Story 5 - Hide and Unhide Apps (Priority: P3)

As a Boox Palma user, I hide apps I never use so my app list stays
short and focused. I can restore hidden apps via the long-press context
menu on any app.

**Why this priority**: Hiding apps is a quality-of-life feature that
supports the calm interface principle but is not essential for basic
launcher functionality.

**Independent Test**: Long-press an app, tap "Hide from home screen",
and confirm it disappears from the list. Long-press any remaining app,
tap "Show hidden apps", and restore an app from the list.

**Acceptance Scenarios**:

1. **Given** the app list is visible, **When** the user long-presses
   an app and selects "Hide from home screen", **Then** the app
   disappears from the list immediately.
2. **Given** apps have been hidden, **When** the user long-presses any
   app and selects "Show hidden apps", **Then** a list of all hidden
   apps is shown with a way to restore each one.
3. **Given** the hidden apps list is shown, **When** the user taps an
   app to restore it, **Then** the app reappears in the home screen
   list in its correct alphabetical position.
4. **Given** a fresh install, **When** the home screen loads for the
   first time, **Then** the launcher itself and non-useful system apps
   are hidden by default.

---

### User Story 6 - Rename Apps (Priority: P3)

As a Boox Palma user, I rename apps to shorter or more meaningful
names (e.g., "Google Chrome" → "Browser") and see the custom name
everywhere in the launcher.

**Why this priority**: Renaming is a personalization feature. The
launcher works fully without it.

**Independent Test**: Long-press an app, tap "Rename", enter a new
name, and confirm the app list shows the new name in the correct
sorted position. Long-press again and "Reset name" to revert.

**Acceptance Scenarios**:

1. **Given** the context menu is open, **When** the user taps "Rename"
   and enters a new name, **Then** the app list displays the custom
   name instead of the system name.
2. **Given** an app has been renamed, **When** the list sorts
   alphabetically, **Then** the app sorts by its custom name.
3. **Given** an app has been renamed, **When** the user long-presses
   it and selects "Reset name", **Then** the original system name is
   restored.

---

### Edge Cases

- What happens when zero apps are installed (besides the launcher)?
  The app list area displays an empty state message.
- What happens when the Boox Library widget is uninstalled or
  unavailable? The widget zone shows the "Tap to configure" placeholder.
- What happens when the weather API returns an error? The weather area
  displays the last cached value or remains blank.
- What happens when the user hides all apps? The app list area is empty.
  Hidden apps remain accessible via the long-press context menu on
  empty space.
- What happens when a renamed app is uninstalled and reinstalled? The
  custom name is retained since it is keyed by package name.
- What happens when an extremely long custom name is entered? The name
  is truncated with an ellipsis in the app list display.
- What happens when location services are unavailable? Weather uses the
  last known location, or falls back to the hardcoded default (Las
  Vegas).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a single vertically scrollable home
  screen with three zones: header (pinned), library widget (pinned),
  and app list (scrollable).
- **FR-002**: System MUST render all UI elements in pure black on pure
  white with no animations, gradients, or gray tones.
- **FR-003**: System MUST display the current time prominently in large
  type at the top of the header, updated every 60 seconds.
- **FR-004**: System MUST display the current date, battery percentage,
  and weather (temperature + condition) in a single compact row beneath
  the time.
- **FR-005**: System MUST determine weather location from the device's
  current location automatically, with no user configuration required.
- **FR-006**: System MUST refresh weather every 2 hours or on launcher
  resume, and display the last cached value or nothing when the network
  is unavailable.
- **FR-007**: System MUST host an Android widget (the Boox NeoReader
  Library widget) between the header and app list, sized as a 2x4
  widget with full-width rendering and tap-through interaction.
- **FR-008**: System MUST persist the hosted widget's identity across
  device restarts without requiring reconfiguration.
- **FR-009**: System MUST display all non-hidden installed apps as a
  vertical list of plain-text names, sorted alphabetically, with no
  icons.
- **FR-010**: System MUST launch the corresponding app when the user
  taps an app name.
- **FR-011**: System MUST display a context menu with Rename, Hide,
  Show hidden apps, and App Info options when the user long-presses an
  app name.
- **FR-012**: System MUST allow users to hide apps from the home screen
  and restore them via the "Show hidden apps" option in the long-press
  context menu.
- **FR-013**: System MUST hide the launcher itself and non-useful system
  apps by default on first install.
- **FR-014**: System MUST allow users to assign custom display names to
  apps, with the custom name used for display and sorting.
- **FR-015**: System MUST register as an Android home screen launcher
  (HOME + DEFAULT intent categories).
- **FR-016**: System MUST handle the back button by doing nothing when
  on the home screen.

### Key Entities

- **App Entry**: Represents an installed app. Key attributes: package
  name (unique identifier), system display name, optional custom display
  name, hidden flag.
- **Weather Data**: Cached weather information. Key attributes: current
  temperature, condition description, last-fetched timestamp,
  coordinates.
- **Widget Binding**: Persisted widget configuration. Key attributes:
  app widget ID, host ID.
- **User Preferences**: All user settings. Key attributes: hidden app
  set, renamed app map.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can launch any visible app within 2 taps from the
  home screen (one tap if already visible, scroll + tap otherwise).
- **SC-002**: The home screen renders completely within 1 second of
  pressing the home button.
- **SC-003**: All text on the home screen is readable on an e-ink
  display without squinting — minimum 14sp font size, pure black on
  white contrast.
- **SC-004**: The app list displays correctly with up to 100 installed
  apps without performance degradation.
- **SC-005**: Weather information is never more than 2 hours stale when
  the device has network connectivity.
- **SC-006**: The library widget persists across 10 consecutive device
  restarts without requiring reconfiguration.
- **SC-007**: Hidden and renamed app preferences persist across launcher
  restarts and device reboots.
- **SC-008**: The launcher registers as a selectable home screen and the
  device's default launcher remains available as a fallback.

### Assumptions

- The target device is the Boox Palma running Android 11 or later.
- The Boox NeoReader Library widget is available on the device as a
  standard Android AppWidget.
- The device has intermittent internet access for weather data (not
  always-on).
- Weather location is determined from the device's location services;
  fallback is Las Vegas (36.17, -115.14).
- Default temperature unit is Fahrenheit (°F).
- The Open-Meteo API remains freely available without an API key.
