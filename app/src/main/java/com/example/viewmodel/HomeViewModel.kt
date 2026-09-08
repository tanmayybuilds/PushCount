package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val todayValidReps: Int = 0,
    val dailyGoal: Int = 50,
    val currentStreak: Int = 0,
    val bestSingleDayReps: Int = 0,
    val allTimeReps: Int = 0
) {
    val goalProgress: Float
        get() = if (dailyGoal > 0) (todayValidReps.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f

    val isGoalAchieved: Boolean
        get() = todayValidReps >= dailyGoal
}

class HomeViewModel(
    private val workoutRepository: WorkoutRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        workoutRepository.getTodayValidReps(),
        preferencesRepository.dailyGoalFlow,
        workoutRepository.currentStreak,
        workoutRepository.bestSingleDayReps,
        workoutRepository.allTimeValidReps
    ) { todayReps, goal, streak, bestDay, allTime ->
        HomeUiState(
            todayValidReps = todayReps,
            dailyGoal = goal,
            currentStreak = streak,
            bestSingleDayReps = bestDay,
            allTimeReps = allTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
