# LapLog Free — Stopwatch with Lap History

[Русская версия](README.ru.md) | English

A stopwatch app for Android with lap tracking, session history, charts, backup, and flexible display settings.

**Current version: 0.19.0** · Min Android: 7.0 (API 24) · Target: Android 14 (API 35)

---

## Features

### Stopwatch
- Start, pause, resume, reset
- Lap marks: two modes — lap only (empty flag) or lap + pause simultaneously (filled flag)
- **Main timer shows current lap time**; total elapsed time shown below in MM:SS (always)
- Per-lap statistics: average and median shown on main screen
- Lap comparison: each lap highlighted green (faster) or red (slower) relative to average
- Digital clock font (DSEG7) for the main timer
- Milliseconds shown on main timer when paused

### History
- All sessions saved automatically on reset (stop)
- Expandable session cards with full lap list
- Session name (optional) with autocomplete from previous sessions
- Notes field (optional, hidden by default — toggle with icon button)
- Filter sessions by name
- Table view and card view
- Session statistics: average and median lap per session

### Session Management (CRUD)
- **Create** sessions manually via the + button
- **Edit** any session: name, notes, date, time, duration, individual laps
- **Delete** session, delete all sessions before selected, delete all sessions
- Session edit dialog with date picker, time picker (24h), and per-lap editing

### Charts
- Lap time trend chart per session name
- Average and median lines on chart
- Time period filter: All time / Last 7 days / Last 30 days
- Works with any number of laps (even one)

### Statistics
- Per-name summary: total sessions, average duration, average lap, median lap
- Overall totals for all names combined

### Backup
- Automatic daily backup to a user-selected folder (compatible with Dropbox, Google Drive, MEGA)
- Manual "Save Backup To…" — saves to any location including cloud storage
- Restore: replace all data or merge with existing
- Backup retention: configurable number of days
- Delete individual backups, delete backups before selected, delete all
- Per-name toggle presets included in backup

### Export
- Export session history to **CSV**
- Export session history to **JSON**
- View/export individual session JSON

### Display Settings (Stopwatch)

| Toggle | Function |
|---|---|
| 📱 / 📵 Screen mode | OFF / ON while running / ALWAYS ON (cycles on tap) |
| 🔒 / 🔓 Orientation lock | Lock screen in current orientation |
| ⏱ / 🕐 Milliseconds | Show/hide milliseconds on main timer (visible when paused) |
| 🔄 / ↕️ Invert lap colors | Swap green/red for faster/slower laps |
| ☀️ / 🌑 Keep brightness | When screen is kept on: maintain system brightness or dim to 10% after timeout (long-press to set timeout: 5–300 s) |
| 🙈 / 👁 Hide time | Blink `--:--` placeholder instead of showing actual time (privacy/focus) |
| 🔢 / ⏰ Show seconds | Display elapsed time as total seconds instead of MM:SS |
| 🔔 / 🔕 Tick sounds | Audible ticks while running — tap to toggle, long-press to configure |

### Per-Name Toggle Presets
Each session name remembers its own toggle configuration. Selecting a name from history automatically restores its settings.

### Tick Sounds
- Enable/disable audible ticks while the stopwatch runs
- 18 sound types: Click, Clack, Bell, Bass, High, Wood, Beep, Ping, Soft, Snap, Chirp, Drum, Chime, Buzz, Chime 2, Gong, Bowl, Whistle
- Configurable accents: each accent has its own interval, sound type, and start offset
- **Sound preview panel**: play any sound directly in the settings dialog without assigning it
- Default: Chime every 60 seconds

### Background Operation
- Foreground service keeps the stopwatch running when the app is in the background
- Persistent notification shows **current activity name** (e.g. "Running running") and elapsed time
- Notification language follows the app's selected language
- Keeps timing accurately when the app is minimized or the screen is off

### Languages
English, Russian, Chinese (Simplified). System language detected automatically; manual override available in the About dialog.

---

## Building

### via GitHub Actions (recommended)

Every push to `main` automatically builds a debug APK:

1. Push to `main`
2. Open the **Actions** tab on GitHub
3. Download the APK from **Artifacts** (available for 30 days)

### Local Build

```bash
git clone https://github.com/vitalysennikov/laplog-app.git
cd laplog-app
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```

---

## Installation

1. Download the APK from GitHub Releases or Actions artifacts
2. Enable "Install from unknown sources" on your device
3. Open the APK and follow the installer

---

## Project Structure

```
laplog-app/
├── app/src/main/java/com/laplog/app/
│   ├── data/                    # PreferencesManager, BackupManager, TranslationManager
│   │   └── database/            # Room DB — AppDatabase, SessionDao, entities
│   ├── model/                   # Data models (SessionWithLaps, BackupData, TickAccent…)
│   ├── service/                 # StopwatchService (foreground)
│   ├── ui/                      # Compose screens and dialogs
│   │   ├── StopwatchScreen.kt
│   │   ├── HistoryScreen.kt
│   │   ├── ChartsScreen.kt
│   │   ├── BackupScreen.kt
│   │   ├── SessionEditDialog.kt
│   │   ├── TickSettingsDialog.kt
│   │   ├── AboutDialog.kt
│   │   └── theme/
│   ├── util/                    # AppLogger, TickSoundManager
│   ├── viewmodel/               # StopwatchViewModel, HistoryViewModel, ChartsViewModel, BackupViewModel
│   └── MainActivity.kt
├── .github/workflows/android.yml
└── README.md
```

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Database**: Room 2.6.1
- **State**: StateFlow + ViewModel (MVVM)
- **Background**: Foreground Service + WorkManager (scheduled backup)
- **Storage**: Storage Access Framework (SAF)
- **Charts**: Vico 2.1.3

---

## License

© 2025 Vitaly Sennikov. All rights reserved.

GitHub: [vitalysennikov/laplog-app](https://github.com/vitalysennikov/laplog-app)
