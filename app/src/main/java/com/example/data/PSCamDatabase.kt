package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedDeviceEntity::class, SurveillanceEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PSCamDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun surveillanceDao(): SurveillanceDao

    companion object {
        @Volatile
        private var INSTANCE: PSCamDatabase? = null

        fun getDatabase(context: Context): PSCamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PSCamDatabase::class.java,
                    "ps_cam_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
