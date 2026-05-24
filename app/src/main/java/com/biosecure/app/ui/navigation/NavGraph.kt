package com.biosecure.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biosecure.app.ui.screens.admin.EditEmployeeScreen
import com.biosecure.app.ui.screens.admin.EmployeeListScreen
import com.biosecure.app.ui.screens.admin.RegisterEmployeeScreen
import com.biosecure.app.ui.screens.confirmation.ConfirmationScreen
import com.biosecure.app.ui.screens.dashboard.DashboardScreen
import com.biosecure.app.ui.screens.history.HistoryScreen
import com.biosecure.app.ui.screens.login.LoginScreen
import com.biosecure.app.ui.screens.scan.ScanScreen
import com.biosecure.app.ui.screens.settings.SettingsScreen
import com.biosecure.app.ui.viewmodel.BioSecureViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Confirmation : Screen("confirmation")

    // Admin routes
    object Dashboard : Screen("admin/dashboard")
    object AdminScan : Screen("admin/scan")
    object AdminHistory : Screen("admin/history")
    object AdminSettings : Screen("admin/settings")
    object AdminRegisterEmployee : Screen("admin/register-employee")
    object AdminEmployeeList : Screen("admin/employee-list")
    object AdminEditEmployee : Screen("admin/edit-employee/{userId}") {
        fun route(userId: Int) = "admin/edit-employee/$userId"
    }

    // Employee routes
    object EmployeeScan : Screen("employee/scan")
    object EmployeeHistory : Screen("employee/history")
    object EmployeeSettings : Screen("employee/settings")

    companion object {
        fun scan(isAdmin: Boolean) = if (isAdmin) AdminScan.route else EmployeeScan.route
        fun history(isAdmin: Boolean) = if (isAdmin) AdminHistory.route else EmployeeHistory.route
        fun settings(isAdmin: Boolean) = if (isAdmin) AdminSettings.route else EmployeeSettings.route
    }
}

@Composable
private fun AdminRouteGuard(
    viewModel: BioSecureViewModel?,
    navController: NavController,
    content: @Composable () -> Unit
) {
    if (viewModel == null) {
        content()
        return
    }
    val role by viewModel.currentRole.collectAsState()
    LaunchedEffect(role) {
        if (role != "admin") {
            navController.navigate(Screen.EmployeeScan.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }
    if (role == "admin") content()
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    viewModel: BioSecureViewModel? = null
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(navController = navController, viewModel = viewModel)
        }

        composable(Screen.Confirmation.route) {
            ConfirmationScreen(navController = navController, viewModel = viewModel)
        }

        // Admin routes — protegidas por AdminRouteGuard
        composable(Screen.Dashboard.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                DashboardScreen(navController = navController, viewModel = viewModel)
            }
        }
        composable(Screen.AdminScan.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                ScanScreen(navController = navController, isAdmin = true, viewModel = viewModel)
            }
        }
        composable(Screen.AdminHistory.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                HistoryScreen(navController = navController, isAdmin = true, viewModel = viewModel)
            }
        }
        composable(Screen.AdminSettings.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                SettingsScreen(
                    navController = navController,
                    isAdmin = true,
                    isDarkMode = isDarkMode,
                    onDarkModeChange = onDarkModeChange,
                    viewModel = viewModel
                )
            }
        }
        composable(Screen.AdminRegisterEmployee.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                RegisterEmployeeScreen(navController = navController, viewModel = viewModel)
            }
        }
        composable(Screen.AdminEmployeeList.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                EmployeeListScreen(navController = navController, viewModel = viewModel)
            }
        }
        composable(
            route = Screen.AdminEditEmployee.route,
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId")
            if (userId != null) {
                AdminRouteGuard(viewModel = viewModel, navController = navController) {
                    EditEmployeeScreen(navController = navController, userId = userId, viewModel = viewModel)
                }
            }
        }

        // Employee routes — sin restricción
        composable(Screen.EmployeeScan.route) {
            ScanScreen(navController = navController, isAdmin = false, viewModel = viewModel)
        }
        composable(Screen.EmployeeHistory.route) {
            HistoryScreen(navController = navController, isAdmin = false, viewModel = viewModel)
        }
        composable(Screen.EmployeeSettings.route) {
            SettingsScreen(
                navController = navController,
                isAdmin = false,
                isDarkMode = isDarkMode,
                onDarkModeChange = onDarkModeChange,
                viewModel = viewModel
            )
        }
    }
}
