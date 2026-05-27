package com.laplog.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.dao.SessionNameDao
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.data.database.entity.SessionNameEntity

@Database(
    entities = [SessionEntity::class, LapEntity::class, SessionNameEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun sessionNameDao(): SessionNameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename comment column to name
                db.execSQL("ALTER TABLE sessions RENAME COLUMN comment TO name")
                // Add notes column
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN name_en TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN name_ru TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN name_zh TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes_en TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes_ru TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes_zh TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS session_names (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL UNIQUE,
                        toggles_json TEXT,
                        accents_json TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO session_names (name)
                    SELECT DISTINCT name FROM sessions
                    WHERE name IS NOT NULL AND name != ''
                """.trimIndent())
                db.execSQL("ALTER TABLE sessions ADD COLUMN name_id INTEGER")
                db.execSQL("""
                    UPDATE sessions SET name_id = (
                        SELECT id FROM session_names
                        WHERE session_names.name = sessions.name
                    ) WHERE name IS NOT NULL AND name != ''
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "laplog_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
