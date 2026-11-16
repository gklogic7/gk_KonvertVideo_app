package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversionHistoryEntity::class, CustomPresetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KonvertDatabase : RoomDatabase() {
    abstract fun historyDao(): ConversionHistoryDao
    abstract fun customPresetDao(): CustomPresetDao

    companion object {
        @Volatile
        private var INSTANCE: KonvertDatabase? = null

        fun getInstance(context: Context): KonvertDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KonvertDatabase::class.java,
                    "konvert_video.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
