package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_devices")
data class SavedDeviceEntity(
    @PrimaryKey
    val id: String,
    val deviceName: String,
    val ipAddress: String,
    val port: Int = 8080,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

@Entity(tableName = "surveillance_events")
data class SurveillanceEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: String, // "MOTION", "RECORDING", "SNAPSHOT"
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val durationSeconds: Int = 0,
    val motionScore: Float = 0f,
    val note: String = ""
)
