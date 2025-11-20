# LapLog Free - Free Stopwatch with History

[Русская версия](README.ru.md) | English

A simple and functional stopwatch application for Android with session history and modern Material Design 3 interface.

## Features

- **Basic stopwatch functionality**: start, pause, reset
- **Lap marks**: save intermediate times with total time and lap duration display
- **Precision toggle**: display time with milliseconds or seconds only
- **Dynamic format**: automatic switching between MM:SS and HH:MM:SS formats when reaching one hour
- **Keep screen on**: screen stays active while stopwatch is running
- **Dark theme**: automatic switching based on system settings
- **Material Design 3**: modern and intuitive interface

## Technologies

- Kotlin
- Jetpack Compose
- Material Design 3
- Android Architecture Components (ViewModel, StateFlow)
- Minimum Android version: 7.0 (API 24)
- Target version: Android 14 (API 34)

## Building the Project

### Local Build

1. Clone the repository:
```bash
git clone https://github.com/vitalysennikov/laplog-app.git
cd laplog-app
```

2. Build APK:
```bash
./gradlew assembleRelease
```

3. APK file will be located in `app/build/outputs/apk/release/`

### Build via GitHub Actions

The project is configured for automatic builds via GitHub Actions:

1. Push to `main` or `master` branch
2. GitHub Actions will automatically build the APK
3. Download the ready APK from artifacts in the Actions section

APK will be available for 30 days after build.

## Installation

1. Download the APK file from Releases section or from GitHub Actions artifacts
2. Allow installation of apps from unknown sources on your device
3. Open the APK file and follow the installation instructions

## Usage

1. **Start stopwatch**: press the "Start" button
2. **Pause**: press the "Pause" button while running
3. **Resume**: press "Resume" after pausing
4. **Reset**: press the "Reset" button
5. **Add lap**: press the "Lap" button while stopwatch is running
6. **Toggle precision**: use the "Show milliseconds" switch at the top of the screen

## Toggles & Settings

The app provides several toggles to customize stopwatch behavior:

### Screen Mode (`keep_screen_on`)
Cycles through three modes by tapping:
- **OFF** (`toggle_screen_on_off`) - Screen dims normally
  - Icon: Outlined smartphone
- **WHILE_RUNNING** (`toggle_screen_on_running`) - Screen stays on only when stopwatch is running
  - Icon: Filled smartphone
- **ALWAYS** (`toggle_screen_on_always`) - Screen stays on while app is open, even in background
  - Icon: Phone with lock

### Lock Orientation (`lock_orientation`)
- **ON** - Locks screen orientation (portrait/landscape)
  - Icon: Locked rotation
- **OFF** - Allows screen rotation
  - Icon: Unlocked rotation

### Show Milliseconds (`show_milliseconds`)
- **ON** - Displays milliseconds on main timer when paused
  - Icon: Filled timer
- **OFF** - Hides milliseconds
  - Icon: Outlined timer

### Invert Lap Colors (`toggle_invert_colors_desc`)
- **ON** - Inverts lap comparison colors
  - Fast laps (-N) shown in red
  - Slow laps (+N) shown in green
  - Icon: Filled swap arrows
- **OFF** - Normal colors
  - Fast laps (-N) shown in green
  - Slow laps (+N) shown in red
  - Icon: Outlined swap arrows

### Dim Brightness (`toggle_dim_brightness_desc`)
Applies when screen is kept on (RUNNING/ALWAYS modes):
- **ON** - Keeps system brightness (10% fixed)
  - Icon: Filled brightness
- **OFF** - Allows screen to dim naturally after timeout, but screen won't turn off
  - Icon: Outlined brightness

### Show Milliseconds in History
Available in History tab:
- **ON** - Shows milliseconds in saved sessions
  - Icon: Filled clock
- **OFF** - Shows only seconds
  - Icon: Outlined clock

## Project Structure

```
laplog-app/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/stopwatch/app/
│   │       │   ├── model/          # Data models
│   │       │   ├── ui/             # UI components
│   │       │   │   └── theme/      # App themes
│   │       │   ├── viewmodel/      # ViewModels
│   │       │   └── MainActivity.kt
│   │       ├── res/                # Resources
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/
│   └── workflows/
│       └── android.yml             # GitHub Actions workflow
└── README.md
```

## License

© 2025 Vitaly Sennikov. All rights reserved.
