package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Workout : Screen("workout")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}
