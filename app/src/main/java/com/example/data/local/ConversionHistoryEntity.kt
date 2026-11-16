package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceName: String,
    val outputName: String,
    val outputFilePath: String,
    val outputGalleryUri: String?,
    val containerId: String,
    val containerExtension: String,
    val mimeType: String,
    val videoCodec: String,
    val audioCodec: String,
    val inputSizeBytes: Long,
    val outputSizeBytes: Long,
    val durationMs: Long,
    val processingTimeMs: Long,
    val status: String,
    val commandUsed: String,
    val errorMessage: String?,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
