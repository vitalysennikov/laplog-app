package com.laplog.app.model

enum class TickSoundType {
    TICK, TOCK, BELL, DEEP, HIGH, WOOD, BEEP, PING,
    SOFT, SNAP, CHIRP, DRUM, CHIME, BUZZ, CHIME2, GONG
}

data class TickAccent(
    val intervalSeconds: Int,
    val soundType: TickSoundType,
    val startOffsetSeconds: Int = 0
)

val TICK_PRESET_INTERVALS = listOf(
    1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 15, 30,
    60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 360
)

val DEFAULT_TICK_ACCENTS = listOf(
    TickAccent(1, TickSoundType.TICK, 0),
    TickAccent(8, TickSoundType.TOCK, 7),
    TickAccent(8, TickSoundType.BELL, 0),
    TickAccent(60, TickSoundType.CHIME, 0)
)
