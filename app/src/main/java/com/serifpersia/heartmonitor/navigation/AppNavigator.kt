package com.serifpersia.heartmonitor.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.serifpersia.heartmonitor.ui.screens.HistoryScreen
import com.serifpersia.heartmonitor.ui.screens.MonitorScreen
import com.serifpersia.heartmonitor.viewmodel.HeartRateViewModel

object Routes {
    const val MONITOR = "monitor"
    const val HISTORY = "history"
}

@Composable
fun AppNavigator(startDestination: String) {
    val navController = rememberNavController()
    val viewModel: HeartRateViewModel = viewModel()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.MONITOR) {
            MonitorScreen(viewModel = viewModel, onNavigateToHistory = {
                navController.navigate(Routes.HISTORY)
            })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(viewModel = viewModel, onNavigateBack = {
                navController.popBackStack()
            })
        }
    }
}