package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PushupMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val todayValidReps: Int = 0,
    val dailyGoal: Int = 50,
    val currentStreak: Int = 0,
    val bestSingleDayReps: Int = 0,
    val allTimeReps: Int = 0,
    val beginnerReps: Int = 0,
    val hardReps: Int = 0,
    val wallReps: Int = 0,
    val kneeReps: Int = 0,
    val selectedMode: PushupMode = PushupMode.BEGINNER
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
        workoutRepository.allTimeValidReps,
        workoutRepository.getTotalValidRepsForMode(PushupMode.BEGINNER),
        workoutRepository.getTotalValidRepsForMode(PushupMode.HARD),
        workoutRepository.getTotalValidRepsForMode(PushupMode.WALL),
        workoutRepository.getTotalValidRepsForMode(PushupMode.KNEE),
        preferencesRepository.selectedModeFlow
    ) { args: Array<Any> ->
        HomeUiState(
            todayValidReps = args[0] as Int,
            dailyGoal = args[1] as Int,
            currentStreak = args[2] as Int,
            bestSingleDayReps = args[3] as Int,
            allTimeReps = args[4] as Int,
            beginnerReps = args[5] as Int,
            hardReps = args[6] as Int,
            wallReps = args[7] as Int,
            kneeReps = args[8] as Int,
            selectedMode = args[9] as PushupMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun selectMode(mode: PushupMode) {
        viewModelScope.launch {
            preferencesRepository.setSelectedMode(mode)
        }
    }
}
