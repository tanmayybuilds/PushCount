package com.example.data.repository

import com.example.data.local.DailyRepAggregate
import com.example.data.local.WorkoutSessionDao
import com.example.data.model.PushupMode
import com.example.data.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

class WorkoutRepository(
    private val dao: WorkoutSessionDao
) {

    val allTimeValidReps: Flow<Int> = dao.getAllTimeTotalValidReps()

    val bestSingleDayReps: Flow<Int> = dao.getBestSingleDayReps()

    val allSessions: Flow<List<WorkoutSession>> = dao.getAllSessions()

    fun getTotalValidRepsForMode(mode: PushupMode): Flow<Int> {
        return dao.getTotalValidRepsForMode(mode.id)
    }

    fun getSessionsForDate(date: Long): Flow<List<WorkoutSession>> {
        return dao.getSessionsForDate(date)
    }

    fun getTotalValidRepsForDate(date: Long): Flow<Int> {
        return dao.getTotalValidRepsForDate(date)
    }

    fun getTodayValidReps(): Flow<Int> {
        val today = LocalDate.now().toEpochDay()
        return dao.getTotalValidRepsForDate(today)
    }

    fun getTodaySessions(): Flow<List<WorkoutSession>> {
        val today = LocalDate.now().toEpochDay()
        return dao.getSessionsForDate(today)
    }

    fun getMonthAggregates(yearMonth: YearMonth): Flow<List<DailyRepAggregate>> {
        val startDate = yearMonth.atDay(1).toEpochDay()
        val endDate = yearMonth.atEndOfMonth().toEpochDay()
        return dao.getDailyAggregatesBetweenDates(startDate, endDate)
    }

    fun getLast30DaysAggregates(): Flow<List<DailyRepAggregate>> {
        val today = LocalDate.now()
        val startDate = today.minusDays(29).toEpochDay()
        val endDate = today.toEpochDay()
        return dao.getDailyAggregatesBetweenDates(startDate, endDate)
    }

    /**
     * Calculates the current consecutive-day streak from the distinct dates with valid reps > 0.
     * Computed in the repository layer as required.
     */
    val currentStreak: Flow<Int> = dao.getDistinctDatesWithValidReps().map { datesDesc ->
        calculateConsecutiveStreak(datesDesc, LocalDate.now().toEpochDay())
    }

    suspend fun insertSession(session: WorkoutSession): Long {
        return dao.insertSession(session)
    }

    suspend fun deleteAllData() {
        dao.deleteAllSessions()
    }

    companion object {
        fun calculateConsecutiveStreak(datesWithReps: List<Long>, todayEpochDay: Long): Int {
            if (datesWithReps.isEmpty()) return 0
            val dateSet = datesWithReps.toSet()

            var streak = 0
            var checkDate = todayEpochDay

            // If user did pushups today, count today and go backwards
            if (dateSet.contains(checkDate)) {
                while (dateSet.contains(checkDate)) {
                    streak++
                    checkDate--
                }
            } else if (dateSet.contains(checkDate - 1)) {
                // If user didn't do pushups today yet, streak is still active from yesterday
                checkDate--
                while (dateSet.contains(checkDate)) {
                    streak++
                    checkDate--
                }
            }

            return streak
        }
    }
}
