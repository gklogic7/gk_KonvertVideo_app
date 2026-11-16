package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionHistoryDao {
    @Query("SELECT * FROM conversion_history ORDER BY createdAtEpochMs DESC")
    fun observeHistory(): Flow<List<ConversionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(entity: ConversionHistoryEntity): Long

    @Query("DELETE FROM conversion_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversion_history")
    suspend fun clearAll()
}
