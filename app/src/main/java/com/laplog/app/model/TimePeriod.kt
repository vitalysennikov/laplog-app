package com.laplog.app.model

enum class TimePeriod {
    ALL_TIME,
    LAST_7_DAYS,
    LAST_30_DAYS;

    fun getStartTimeMillis(): Long? {
        val now = System.currentTimeMillis()
        return when (this) {
            ALL_TIME -> null
            LAST_7_DAYS -> now - (7 * 24 * 60 * 60 * 1000L)
            LAST_30_DAYS -> now - (30 * 24 * 60 * 60 * 1000L)
        }
    }
}
