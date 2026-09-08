package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.WorkoutSession

@Database(entities = [WorkoutSession::class], version = 1, exportSchema = false)
abstract class PushCountDatabase : RoomDatabase() {

    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        @Volatile
        private var INSTANCE: PushCountDatabase? = null

        fun getDatabase(context: Context): PushCountDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PushCountDatabase::class.java,
                    "pushcount_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
