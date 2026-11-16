package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPresetDao {
    @Query("SELECT * FROM custom_presets ORDER BY createdAtEpochMs DESC")
    fun observeCustomPresets(): Flow<List<CustomPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: CustomPresetEntity)

    @Query("DELETE FROM custom_presets WHERE id = :id")
    suspend fun deletePreset(id: String)
}
