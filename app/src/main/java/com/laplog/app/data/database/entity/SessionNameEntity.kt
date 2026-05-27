package com.laplog.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_names",
    indices = [Index(value = ["name"], unique = true)]
)
data class SessionNameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "toggles_json") val togglesJson: String? = null,
    @ColumnInfo(name = "accents_json") val accentsJson: String? = null
)
