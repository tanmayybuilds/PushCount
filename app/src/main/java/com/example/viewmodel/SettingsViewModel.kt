package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val dailyGoal: Int = 50,
    val useFrontCamera: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class SettingsViewModel(
    private val workoutRepository: WorkoutRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.dailyGoalFlow,
        preferencesRepository.useFrontCameraFlow,
        preferencesRepository.themeModeFlow
    ) { goal, useFront, theme ->
        SettingsUiState(
            dailyGoal = goal,
            useFrontCamera = useFront,
            themeMode = theme
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun updateDailyGoal(newGoal: Int) {
        viewModelScope.launch {
            preferencesRepository.setDailyGoal(newGoal)
        }
    }

    fun setUseFrontCamera(useFront: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUseFrontCamera(useFront)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            workoutRepository.deleteAllData()
            preferencesRepository.clearPreferences()
        }
    }
}
