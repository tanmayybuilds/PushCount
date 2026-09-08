package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.preferences.ThemeMode
import com.example.di.AppViewModelProvider
import com.example.ui.navigation.Screen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.workout.WorkoutScreen
import com.example.ui.theme.PushCountTheme
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.HomeViewModel
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.WorkoutViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as PushCountApplication
            val themeMode by app.container.userPreferencesRepository.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )

            PushCountTheme(themeMode = themeMode) {
                PushCountApp()
            }
        }
    }
}

@Composable
fun PushCountApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
            HomeScreen(
                viewModel = homeViewModel,
                onStartWorkout = {
                    navController.navigate(Screen.Workout.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Workout.route) {
            val workoutViewModel: WorkoutViewModel = viewModel(factory = AppViewModelProvider.Factory)
            WorkoutScreen(
                viewModel = workoutViewModel,
                onFinishAndExit = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            val historyViewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
            HistoryScreen(
                viewModel = historyViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
