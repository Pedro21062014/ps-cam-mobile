package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM saved_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<SavedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDeviceEntity)

    @Update
    suspend fun updateDevice(device: SavedDeviceEntity)

    @Delete
    suspend fun deleteDevice(device: SavedDeviceEntity)

    @Query("DELETE FROM saved_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: String)

    @Query("DELETE FROM saved_devices")
    suspend fun clearAllDevices()
}

@Dao
interface SurveillanceDao {
    @Query("SELECT * FROM surveillance_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SurveillanceEventEntity>>

    @Query("SELECT * FROM surveillance_events WHERE type = :type ORDER BY timestamp DESC")
    fun getEventsByType(type: String): Flow<List<SurveillanceEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SurveillanceEventEntity): Long

    @Delete
    suspend fun deleteEvent(event: SurveillanceEventEntity)

    @Query("DELETE FROM surveillance_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM surveillance_events")
    suspend fun clearAllEvents()
}
