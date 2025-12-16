# LapLog Free - Project Context

## Project Overview

**LapLog Free** is an Android stopwatch application with lap tracking and session history features. The name reflects two key features: **Lap** marks and **Log** (history) of sessions.

- **Package**: `com.laplog.app`
- **Current Version**: 0.10.0 (versionCode 33) - IN DEVELOPMENT
- **Stable Version**: 0.9.7 (tagged)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Latest Build**: GitHub Actions builds APK on every push to main

## Architecture

### Design Pattern
- **MVVM** (Model-View-ViewModel) architecture
- **Jetpack Compose** for UI with Material Design 3
- **Room Database** for persistent storage
- **StateFlow** for reactive state management
- **Kotlin Coroutines** for asynchronous operations

### Technology Stack
- **Language**: Kotlin 1.9.20
- **UI Framework**: Jetpack Compose (BOM 2023.10.01)
- **Database**: Room 2.6.1 with KSP 1.9.20-1.0.14
- **Build System**: Gradle with Kotlin DSL
- **Version Control**: Git + GitHub
- **CI/CD**: GitHub Actions (builds debug APK on push)

## Project Structure

```
com.laplog.app/
├── data/
│   ├── PreferencesManager.kt          # SharedPreferences wrapper for app settings
│   └── database/
│       ├── AppDatabase.kt             # Room database singleton
│       ├── dao/
│       │   └── SessionDao.kt          # Data access object for sessions/laps
│       └── entity/
│           ├── SessionEntity.kt       # Database table for stopwatch sessions
│           └── LapEntity.kt           # Database table for lap marks (FK to sessions)
├── model/
│   ├── LapTime.kt                     # UI model for lap display
│   └── SessionWithLaps.kt             # Model combining session with laps
├── ui/
│   ├── StopwatchScreen.kt             # Main stopwatch UI screen
│   ├── HistoryScreen.kt               # History view with session list
│   └── theme/                         # Material 3 theme configuration
├── viewmodel/
│   ├── StopwatchViewModel.kt          # Business logic and state management
│   ├── StopwatchViewModelFactory.kt   # Factory for ViewModel dependency injection
│   ├── HistoryViewModel.kt            # History screen business logic
│   └── HistoryViewModelFactory.kt     # Factory for HistoryViewModel
└── MainActivity.kt                     # Application entry point with navigation
```

## Key Features (Implemented)

### Stage 1: Settings Persistence ✅
- **Show/Hide Milliseconds**: IconToggleButton with AccessTime icon + "ms" label
- **Keep Screen On**: IconToggleButton with Smartphone icon + "Screen" label
- **Lock Orientation**: IconToggleButton with Lock icon + "Lock" label (NEW in 0.2.0)
- **SharedPreferences**: All settings persist via `PreferencesManager`
- **Location**: Horizontal row of icon toggles below control buttons

### Stage 2: UI Improvements ✅
- **Icon-Only Buttons**: 32dp icons without text labels
  - Reset: `Icons.Default.Refresh`
  - Start/Pause: `Icons.Default.PlayArrow` / `Icons.Default.Pause`
  - Lap: `Icons.Outlined.Flag`
- **Monospace Font**: FontFamily.Monospace for all time displays
  - Main timer: 56sp, bold
  - Lap times: bodyMedium with monospace
  - Prevents numbers from "jumping" when changing
- **Compact Lap Display**: Single-row layout with reduced padding
- **Adaptive Time Format**: Shows hours only when ≥1 hour elapsed
- **No TopAppBar**: Removed large "LapLog Free" title
- **Small App Name**: Added at bottom above navigation bar (labelSmall style)

### Stage 3: Room Database ✅
- **SessionEntity**: Stores session metadata (startTime, endTime, totalDuration, name, notes)
- **LapEntity**: Stores individual lap marks with foreign key to SessionEntity
- **Auto-save**: Sessions saved to database when Reset button is pressed
- **Cascade Delete**: Deleting a session automatically deletes associated laps

### Stage 4: Names and Notes with Autocomplete ✅
- **Add/Edit Names**: Attach session names (наименование) to saved sessions
- **Add/Edit Notes**: Attach additional notes (заметка) to sessions in history
- **Autocomplete**: Suggests previously used names while typing
- **Persistent Storage**: Used names saved in SharedPreferences
- **Dialogs**: Clean UI for entering and editing names and notes

### Stage 5: Delete Functions ✅
- **Delete Session**: Remove individual session with confirmation
- **Delete Before**: Remove all sessions before (and including) selected session
- **Delete All**: Clear entire history with warning dialog
- **Confirmation Dialogs**: All delete operations require user confirmation

### Stage 6: Export History ✅
- **CSV Export**: Export sessions and laps to CSV format
- **JSON Export**: Export sessions and laps to JSON format
- **Storage Access Framework**: User chooses save location
- **Auto-naming**: Files named with timestamp (e.g., `laplog_history_2025-11-09_143022.csv`)

### Stage 7: UI & Navigation ✅
- **Bottom Navigation**: Tab navigation between Stopwatch and History
- **Session Cards**: Expandable cards showing session details and laps
- **Date Formatting**: Human-readable dates and times
- **Material Design 3**: Consistent theming across all screens
- **Icon Toggles**: Horizontal row with filled/outlined icons and small labels
- **App Name**: Small centered label above navigation bar

## Database Schema

### sessions
```sql
CREATE TABLE sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    startTime INTEGER NOT NULL,
    endTime INTEGER NOT NULL,
    totalDuration INTEGER NOT NULL,
    name TEXT,
    notes TEXT
);
```

### laps
```sql
CREATE TABLE laps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sessionId INTEGER NOT NULL,
    lapNumber INTEGER NOT NULL,
    totalTime INTEGER NOT NULL,
    lapDuration INTEGER NOT NULL,
    FOREIGN KEY(sessionId) REFERENCES sessions(id) ON DELETE CASCADE
);
CREATE INDEX index_laps_sessionId ON laps(sessionId);
```

## Development Roadmap

See `task_2.md` for detailed requirements.

### Version 0.2.0 (COMPLETED - tagged v0.2.0)
- ✅ Stage 1: Settings persistence + orientation lock
- ✅ Stage 2: UI improvements + monospace font
- ✅ Stage 3: Room Database for session history
- ✅ Stage 4: Comments with autocomplete
- ✅ Stage 5: Delete functions (3 variants)
- ✅ Stage 6: CSV/JSON export
- ✅ Stage 7: UI polish and navigation
- ✅ Bug fix: Session saving to history (Flow.first)

### Version 0.3.0 (COMPLETED)
- ✅ DSEG7 digital clock font (angular/segmented display style)
- ✅ Lap statistics: AVG/MEDIAN on stopwatch and history
- ✅ Enhanced lap display: +/-N.N s difference indicators
  - Green for faster laps (negative difference)
  - Red for slower laps (positive difference)
- ✅ Redesigned control buttons (state-based):
  - Stopped: [Start] only
  - Running: [Pause] [Filled Flag (Lap+Pause)] [Empty Flag (Lap)]
  - Paused: [Start] [Stop]
- ✅ Collapse/Expand All toggle in History
- ✅ Comment moved to session header (next to date)
- ✅ Seconds added to session timestamps
- ✅ Fixed session saving bug (Flow.collect → Flow.first)

### Version 0.9.0 (COMPLETED - tagged v0.9.0)
- ✅ Brightness dimming toggle: allows dimming screen to 10% or use system brightness
- ✅ Comment spaces fix: preserve spaces inside comments, trim only at edges
- ✅ Backup deletion options:
  - Delete all backups option in top bar
  - Delete backups before selected backup
  - Confirmation dialogs for all operations
- ✅ Cloud storage support for backups:
  - "Save Backup To..." button for manual save anywhere
  - Compatible with Dropbox, Google Drive, MEGA
  - Dual backup approach: automatic to folder + manual to cloud
- ✅ Backup version updated to 0.9.0
- ✅ String resources added for all languages (EN, RU, ZH)

### Version 0.10.0 (IN DEVELOPMENT)
- ✅ Renamed "comment" field to "name" (наименование)
- ✅ Added "notes" field (заметка) for additional session information
- ✅ Added table view for sessions showing date, name, duration, lap count, average, and median
- ✅ Added filter by session name in history (backend ready, UI pending)
- ✅ Database migration from version 1 to 2
- ✅ Updated all UI screens to use name and notes fields
- ✅ Updated string resources for all languages (EN, RU, ZH)

### Version 0.9.7 (COMPLETED - tagged v0.9.7)
- ✅ Fixed milliseconds display on main timer (show only when paused)
- ✅ Fixed main timer update during pause (accurate milliseconds)
- ✅ Fixed notification not removing when stopwatch stopped
- ✅ Fixed notification reappearing after stop
- ✅ Fixed dimming toggle logic (swapped conditions)
- ✅ Fixed notification appearing on app start in stopped state (complete fix)
- ✅ Fixed race condition when stopping stopwatch (state reset synchronously)
- ✅ Fixed screen dimming in ALWAYS mode when dimBrightness is off
- ✅ Fixed screen turning off in ALWAYS mode when dimBrightness is off
- ✅ Add real-time wake lock updates when settings change during runtime
- ✅ Fixed ALWAYS mode notification showing "Paused" instead of "Screen stays on"
- ✅ Fixed screen turning off on pause in ALWAYS mode with dimBrightness off

### Version 0.9.6 (COMPLETED)
- Same fixes as 0.9.7 but had issues with ALWAYS mode notifications and pause behavior

### Version 0.9.5 (COMPLETED)
- Same fixes as 0.9.6 but notification fix was incomplete

### Known Issues
- None currently

## Code Conventions

### Naming
- **Packages**: lowercase, no underscores (`com.laplog.app.data.database`)
- **Classes**: PascalCase (`StopwatchViewModel`, `SessionEntity`)
- **Functions**: camelCase (`startOrPause()`, `formatTime()`)
- **Private fields**: camelCase with underscore prefix for StateFlow backing properties (`_elapsedTime`)

### State Management
- Use `StateFlow` for ViewModel state
- Expose immutable `StateFlow` via `.asStateFlow()`
- Collect state in Composables using `collectAsState()`

### Database Operations
- All DAO methods are `suspend` functions (except Flow queries)
- Use `viewModelScope.launch` for database operations in ViewModel
- Never perform database operations on main thread

## Git Workflow

### Branches
- `main`: Stable releases only (merged from dev)
- `dev`: Active development branch

### Versioning
- Semantic versioning: `MAJOR.MINOR.PATCH`
- 0.x.x for pre-release versions
- 1.0.0 for first public release on Google Play

### Commit Messages
- Use descriptive commit messages
- Multi-line format: summary + blank line + detailed description
- **IMPORTANT**: NO automated signatures or co-authorship lines
  - ❌ Do NOT add: "Generated with Claude Code"
  - ❌ Do NOT add: "Co-Authored-By: Claude"
  - ✅ Keep commits clean and professional

### Tags
- Git tags for version releases: `v0.1.0`, `v0.1.1`, etc.

## Build & Deployment

### Local Development
- ⚠️ **NO LOCAL BUILDS**: Android SDK not installed locally
- ✅ **Use GitHub Actions only**: All builds performed via CI/CD pipeline
- 🚫 **Do NOT run**: `./gradlew assembleDebug` or any gradle commands locally
- 📦 **APK Location**: Download from GitHub Actions artifacts

### CI/CD Pipeline
- GitHub Actions workflow: `.github/workflows/android.yml`
- ⚠️ **Triggers on `main` branch ONLY** - no builds on dev or other branches
- Workflow: Automatic build on every push to main
- Output: Debug APK uploaded as artifact
- Download: GitHub Actions tab → latest workflow run → Artifacts
- Testing: Install APK on device via `adb install app-debug.apk`

### Development Workflow
1. Develop on `dev` branch
2. Commit and push changes to `dev`
3. Switch to `main` branch
4. Merge `dev` into `main`
5. Push `main` to trigger GitHub Actions build
6. Download APK from Actions artifacts

## Important Implementation Details

### Session Saving Logic
**Location**: `StopwatchViewModel.kt:74-118`

Sessions save when Reset button is pressed IF:
- `elapsedTime > 0` OR
- `laps.isNotEmpty()`

Flow:
1. User presses Reset
2. Stopwatch stops
3. `saveSession()` called asynchronously
4. SessionEntity created with startTime, endTime, totalDuration
5. Insert to database via `sessionDao.insertSession()`
6. If laps exist, insert via `sessionDao.insertLaps()`
7. Values reset to 0 after save completes

**Issue**: Currently not working - sessions don't appear in History tab

### Icon Toggle Implementation
**Location**: `StopwatchScreen.kt:136-202`

Three toggles in horizontal row:
- **Milliseconds**: AccessTime (filled/outlined)
- **Screen**: Smartphone (filled/outlined)
- **Orientation**: Lock/LockOpen (filled/outlined)

Each has small text label below icon (needs removal)

### Monospace Font
**Current**: `FontFamily.Monospace` (rounded)
**Desired**: Angular/digital clock style (segmented display look)

## License

© 2025 Vitaly Sennikov. All rights reserved.

This is proprietary software. A paid version is planned for future release.

## Author

**Vitaly Sennikov**
- GitHub: [@vitalysennikov](https://github.com/vitalysennikov)
- Repository: [laplog-app](https://github.com/vitalysennikov/laplog-app)
- не заменяй коммиты, делай всегда новые