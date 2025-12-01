package com.wpinrui.snapmath.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wpinrui.snapmath.ui.screens.CheckScreen
import com.wpinrui.snapmath.ui.screens.ConvertScreen
import com.wpinrui.snapmath.ui.screens.HistoryDetailScreen
import com.wpinrui.snapmath.ui.screens.HistoryScreen
import com.wpinrui.snapmath.ui.screens.HomeScreen
import com.wpinrui.snapmath.ui.screens.SettingsScreen
import com.wpinrui.snapmath.ui.screens.SolveScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object Solve : Screen("solve")
    data object Check : Screen("check")
    data object Convert : Screen("convert")
    data object History : Screen("history")
    data object HistoryDetail : Screen("history/{entryId}") {
        fun createRoute(entryId: Long) = "history/$entryId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSolve = { navController.navigate(Screen.Solve.route) },
                onNavigateToCheck = { navController.navigate(Screen.Check.route) },
                onNavigateToConvert = { navController.navigate(Screen.Convert.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Solve.route) {
            SolveScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Check.route) {
            CheckScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Convert.route) {
            ConvertScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onEntryClick = { entry ->
                    navController.navigate(Screen.HistoryDetail.createRoute(entry.id))
                }
            )
        }

        composable(
            route = Screen.HistoryDetail.route,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
            HistoryDetailScreen(
                entryId = entryId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
