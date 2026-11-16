package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_presets")
data class CustomPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val containerId: String,
    val videoCodecId: String,
    val audioCodecId: String,
    val resolutionName: String,
    val crf: Int,
    val audioBitrateKbps: Int,
    val fps: Int?,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
