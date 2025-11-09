package com.laplog.app.model

import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity

data class SessionWithLaps(
    val session: SessionEntity,
    val laps: List<LapEntity>
)
