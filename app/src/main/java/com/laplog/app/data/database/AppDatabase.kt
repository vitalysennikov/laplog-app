package com.laplog.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, LapEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

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
                // Add translation cache columns
                db.execSQL("ALTER TABLE sessions ADD COLUMN name_en TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN name_ru TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN name_zh TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes_en TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes_ru TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes_zh TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "laplog_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
