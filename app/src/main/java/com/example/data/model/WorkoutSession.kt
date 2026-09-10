package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a completed pushup workout session stored locally on device.
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long, // Epoch day (e.g., LocalDate.now().toEpochDay())
    val validReps: Int,
    val invalidReps: Int,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String = PushupMode.BEGINNER.id
) {
    val totalReps: Int
        get() = validReps + invalidReps

    val accuracyPercentage: Int
        get() = if (totalReps > 0) ((validReps.toDouble() / totalReps) * 100).toInt() else 100

    val pushupMode: PushupMode
        get() = PushupMode.fromId(mode)
}
