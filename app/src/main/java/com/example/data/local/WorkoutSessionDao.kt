package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

data class DailyRepAggregate(
    val date: Long,
    val totalValidReps: Int,
    val totalInvalidReps: Int
)

@Dao
interface WorkoutSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession): Long

    @Query("SELECT * FROM workout_sessions WHERE date = :date ORDER BY timestamp DESC")
    fun getSessionsForDate(date: Long): Flow<List<WorkoutSession>>

    @Query("SELECT COALESCE(SUM(validReps), 0) FROM workout_sessions WHERE date = :date")
    fun getTotalValidRepsForDate(date: Long): Flow<Int>

    @Query("SELECT * FROM workout_sessions WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC, timestamp ASC")
    fun getSessionsBetweenDates(startDate: Long, endDate: Long): Flow<List<WorkoutSession>>

    @Query("""
        SELECT date, SUM(validReps) as totalValidReps, SUM(invalidReps) as totalInvalidReps 
        FROM workout_sessions 
        WHERE date >= :startDate AND date <= :endDate 
        GROUP BY date 
        ORDER BY date ASC
    """)
    fun getDailyAggregatesBetweenDates(startDate: Long, endDate: Long): Flow<List<DailyRepAggregate>>

    @Query("SELECT COALESCE(SUM(validReps), 0) FROM workout_sessions")
    fun getAllTimeTotalValidReps(): Flow<Int>

    @Query("SELECT COALESCE(SUM(validReps), 0) FROM workout_sessions WHERE mode = :mode")
    fun getTotalValidRepsForMode(mode: String): Flow<Int>

    @Query("SELECT DISTINCT date FROM workout_sessions WHERE validReps > 0 ORDER BY date DESC")
    fun getDistinctDatesWithValidReps(): Flow<List<Long>>

    @Query("""
        SELECT COALESCE(MAX(dayTotal), 0) FROM (
            SELECT SUM(validReps) as dayTotal 
            FROM workout_sessions 
            GROUP BY date
        )
    """)
    fun getBestSingleDayReps(): Flow<Int>

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()
}
