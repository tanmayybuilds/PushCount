package com.example.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.PushCountApplication
import com.example.data.local.PushCountDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.HomeViewModel
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.WorkoutViewModel

/**
 * Manual Dependency Injection container providing application-scoped singletons.
 */
class AppContainer(private val context: Context) {

    private val database: PushCountDatabase by lazy {
        PushCountDatabase.getDatabase(context)
    }

    val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepository(database.workoutSessionDao())
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }
}

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as PushCountApplication
            val container = application.container

            return when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                    HomeViewModel(
                        workoutRepository = container.workoutRepository,
                        preferencesRepository = container.userPreferencesRepository
                    ) as T
                }
                modelClass.isAssignableFrom(WorkoutViewModel::class.java) -> {
                    WorkoutViewModel(
                        workoutRepository = container.workoutRepository,
                        preferencesRepository = container.userPreferencesRepository
                    ) as T
                }
                modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                    HistoryViewModel(
                        workoutRepository = container.workoutRepository
                    ) as T
                }
                modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                    SettingsViewModel(
                        workoutRepository = container.workoutRepository,
                        preferencesRepository = container.userPreferencesRepository
                    ) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
