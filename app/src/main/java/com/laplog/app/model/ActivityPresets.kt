package com.laplog.app.model

data class ActivityPreset(
    val nameRu: String,
    val nameEn: String,
    val nameZh: String,
    val accents: List<TickAccent>
)

val ACTIVITY_PRESETS: List<ActivityPreset> = listOf(
    ActivityPreset("дыхание медведя", "Bear Breathing", "熊式呼吸", listOf(
        TickAccent(1, TickSoundType.TICK, 0),
        TickAccent(8, TickSoundType.TOCK, 7),
        TickAccent(8, TickSoundType.BELL, 0),
        TickAccent(60, TickSoundType.CHIME, 0),
        TickAccent(300, TickSoundType.GONG, 0),
    )),
    ActivityPreset("дыхание полоза", "Snake Breathing", "蛇式呼吸", listOf(
        TickAccent(60, TickSoundType.CHIME, 0),
        TickAccent(300, TickSoundType.GONG, 0),
    )),
    ActivityPreset("дыхание сокола", "Falcon Breathing", "鹰式呼吸", listOf(
        TickAccent(1, TickSoundType.TICK, 0),
        TickAccent(8, TickSoundType.TOCK, 7),
        TickAccent(8, TickSoundType.BELL, 0),
        TickAccent(60, TickSoundType.CHIME, 0),
        TickAccent(300, TickSoundType.GONG, 0),
    )),
    ActivityPreset("задержки на выдохе", "Exhale Holds", "呼气保持", listOf(
        TickAccent(180, TickSoundType.TICK,   30),
        TickAccent(180, TickSoundType.TOCK,   60),
        TickAccent(180, TickSoundType.BELL,   90),
        TickAccent(180, TickSoundType.CHIME,  120),
        TickAccent(180, TickSoundType.CHIME2, 150),
        TickAccent(180, TickSoundType.GONG,   0),
    )),
    ActivityPreset("дерево жизни", "Tree of Life", "生命之树", listOf(
        TickAccent(300, TickSoundType.WOOD, 0),
        TickAccent(900, TickSoundType.SOFT, 0),
    )),
)
