package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DailyRepAggregate
import com.example.data.model.WorkoutSession
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class HistoryUiState(
    val selectedYearMonth: YearMonth = YearMonth.now(),
    val monthlyAggregates: Map<LocalDate, DailyRepAggregate> = emptyMap(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateSessions: List<WorkoutSession> = emptyList(),
    val last30DaysAggregates: List<DailyRepAggregate> = emptyList(),
    val allTimeTotalReps: Int = 0,
    val bestSingleDayReps: Int = 0,
    val currentStreak: Int = 0
)

class HistoryViewModel(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthlyAggregatesFlow = _selectedYearMonth.flatMapLatest { ym ->
        workoutRepository.getMonthAggregates(ym)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedDateSessionsFlow = _selectedDate.flatMapLatest { date ->
        workoutRepository.getSessionsForDate(date.toEpochDay())
    }

    private data class OverviewStats(
        val last30Days: List<DailyRepAggregate>,
        val allTime: Int,
        val bestDay: Int,
        val streak: Int
    )

    private val overviewStatsFlow = combine(
        workoutRepository.getLast30DaysAggregates(),
        workoutRepository.allTimeValidReps,
        workoutRepository.bestSingleDayReps,
        workoutRepository.currentStreak
    ) { last30, allTime, bestDay, streak ->
        OverviewStats(last30, allTime, bestDay, streak)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        _selectedYearMonth,
        monthlyAggregatesFlow,
        _selectedDate,
        selectedDateSessionsFlow,
        overviewStatsFlow
    ) { ym, monthAggs, selDate, sessions, overview ->
        val monthMap = monthAggs.associateBy { LocalDate.ofEpochDay(it.date) }
        HistoryUiState(
            selectedYearMonth = ym,
            monthlyAggregates = monthMap,
            selectedDate = selDate,
            selectedDateSessions = sessions,
            last30DaysAggregates = overview.last30Days,
            allTimeTotalReps = overview.allTime,
            bestSingleDayReps = overview.bestDay,
            currentStreak = overview.streak
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun selectPreviousMonth() {
        _selectedYearMonth.update { it.minusMonths(1) }
    }

    fun selectNextMonth() {
        _selectedYearMonth.update { it.plusMonths(1) }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }
}
