# LapLog Free - Project Context

## Project Overview

**LapLog Free** is an Android stopwatch application with lap tracking and session history features. The name reflects two key features: **Lap** marks and **Log** (history) of sessions.

- **Package**: `com.laplog.app`
- **Current Version**: 0.1.1 (versionCode 2)
- **Target Version**: 0.2.0 (in development)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

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
- **Show/Hide Milliseconds**: Toggle between `MM:SS.mm` and `MM:SS` display formats
- **Keep Screen On**: Keep device screen active during stopwatch operation
- **SharedPreferences**: Persistent storage via `PreferencesManager`

### Stage 2: UI Improvements ✅
- **Icon-Only Buttons**: 32dp icons without text labels
  - Reset: `Icons.Default.Refresh`
  - Start/Pause: `Icons.Default.PlayArrow` / `Icons.Default.Pause`
  - Lap: `Icons.Outlined.Flag`
- **Compact Lap Display**: Single-row layout with reduced padding
- **Adaptive Time Format**: Shows hours only when ≥1 hour elapsed

### Stage 3: Room Database ✅
- **SessionEntity**: Stores session metadata (startTime, endTime, totalDuration, comment)
- **LapEntity**: Stores individual lap marks with foreign key to SessionEntity
- **Auto-save**: Sessions saved to database when Reset button is pressed
- **Cascade Delete**: Deleting a session automatically deletes associated laps

### Stage 4: Comments with Autocomplete ✅
- **Add/Edit Comments**: Attach text comments to saved sessions
- **Autocomplete**: Suggests previously used comments while typing
- **Persistent Storage**: Used comments saved in SharedPreferences
- **Comment Dialog**: Clean UI for entering and editing comments

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

## Database Schema

### sessions
```sql
CREATE TABLE sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    startTime INTEGER NOT NULL,
    endTime INTEGER NOT NULL,
    totalDuration INTEGER NOT NULL,
    comment TEXT
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

### Version 0.2.0 (In Progress)
- ✅ Stage 1: Settings persistence with SharedPreferences
- ✅ Stage 2: UI improvements (icon buttons, compact laps)
- ✅ Stage 3: Room Database for session history
- ✅ Stage 4: Add comments to sessions with autocomplete
- ✅ Stage 5: Delete functions (selected session, sessions before date, all sessions)
- ✅ Stage 6: Export history to file (CSV or JSON format)
- ✅ Stage 7: UI polish and navigation
- ⏳ Stage 8: Prepare for Google Play publication

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
- No automated signatures (e.g., "Generated with Claude Code")

### Tags
- Git tags for version releases: `v0.1.0`, `v0.1.1`, etc.

## Build & Deployment

### Local Development
- Android SDK not installed locally
- All builds performed via GitHub Actions

### CI/CD Pipeline
- GitHub Actions workflow: `.github/workflows/android.yml`
- Triggers on push to `main` and `dev` branches
- Builds debug APK and uploads as artifact
- APK download available in Actions tab

## License

© 2025 Vitaly Sennikov. All rights reserved.

This is proprietary software. A paid version is planned for future release.

## Author

**Vitaly Sennikov**
- GitHub: [@vitalysennikov](https://github.com/vitalysennikov)
- Repository: [laplog-app](https://github.com/vitalysennikov/laplog-app)
