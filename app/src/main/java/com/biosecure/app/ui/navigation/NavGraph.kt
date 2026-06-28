package com.biosecure.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biosecure.app.data.repository.AuthRepository
import com.biosecure.app.ui.screens.admin.AdminEmployeeQRScreen
import com.biosecure.app.ui.screens.admin.EditEmployeeScreen
import com.biosecure.app.ui.screens.admin.EmployeeListScreen
import com.biosecure.app.ui.screens.admin.QRScannerScreen
import com.biosecure.app.ui.screens.admin.SedesScreen
import com.biosecure.app.ui.screens.admin.ShiftManagerScreen
import com.biosecure.app.ui.screens.admin.ShiftSettingsScreen
import com.biosecure.app.ui.screens.confirmation.ConfirmationScreen
import com.biosecure.app.ui.screens.dashboard.DashboardScreen
import com.biosecure.app.ui.screens.employee.EmployeeDashboard
import com.biosecure.app.ui.screens.history.HistoryScreen
import com.biosecure.app.ui.screens.login.LoginScreen
import com.biosecure.app.ui.screens.scan.ScanScreen
import com.biosecure.app.ui.screens.settings.SettingsScreen
import com.biosecure.app.ui.screens.splash.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Confirmation : Screen("confirmation")

    // Admin routes
    object Dashboard : Screen("admin/dashboard")
    object AdminScan : Screen("admin/scan")
    object AdminHistory : Screen("admin/history")
    object AdminSettings : Screen("admin/settings")
    object AdminEmployeeList : Screen("admin/employee-list")
    object AdminEditEmployee : Screen("admin/edit-employee/{userId}") {
        fun route(userId: String) = "admin/edit-employee/$userId"
    }
    object AdminQRScan : Screen("admin/qr-scan")
    object AdminEmployeeQR : Screen("admin/employee-qr/{uid}") {
        fun route(uid: String) = "admin/employee-qr/$uid"
    }
    object AdminSedes : Screen("admin/sedes")
    object AdminShiftSettings : Screen("admin/shift-settings")
    object AdminShiftManager : Screen("admin/shift-manager")
    object EmployeeHome : Screen("employee/home")
    object EmployeeScan : Screen("employee/scan")
    object EmployeeHistory : Screen("employee/history")
    object EmployeeSettings : Screen("employee/settings")
    object EmployeeQR : Screen("employee/qr")

    companion object {
        fun scan(isAdmin: Boolean) = if (isAdmin) AdminScan.route else EmployeeScan.route
        fun history(isAdmin: Boolean) = if (isAdmin) AdminHistory.route else EmployeeHistory.route
        fun settings(isAdmin: Boolean) = if (isAdmin) AdminSettings.route else EmployeeSettings.route
    }
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    language: String = "es",
    onLanguageChange: (String) -> Unit = {},
    authRepository: AuthRepository? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                navController = navController,
                authRepository = authRepository,
                viewModel = null
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(navController = navController, viewModel = null)
        }

        composable(Screen.Confirmation.route) {
            ConfirmationScreen(navController = navController, viewModel = null)
        }

        // Admin routes
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController, viewModel = null, sedeId = null)
        }
        composable(Screen.AdminScan.route) {
            ScanScreen(navController = navController, isAdmin = true, viewModel = null)
        }
        composable(Screen.AdminHistory.route) {
            HistoryScreen(navController = navController, isAdmin = true, viewModel = null)
        }
        composable(Screen.AdminSettings.route) {
            SettingsScreen(
                navController = navController,
                isAdmin = true,
                isDarkMode = isDarkMode,
                onDarkModeChange = onDarkModeChange,
                language = language,
                onLanguageChange = onLanguageChange,
                viewModel = null
            )
        }
        composable(Screen.AdminEmployeeList.route) {
            EmployeeListScreen(navController = navController, viewModel = null, sedeId = null)
        }
        composable(
            route = Screen.AdminEditEmployee.route
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            if (userId != null) {
                EditEmployeeScreen(navController = navController, userId = userId, viewModel = null)
            }
        }
        composable(Screen.AdminQRScan.route) {
            QRScannerScreen(navController = navController, viewModel = null)
        }
        composable(
            route = Screen.AdminEmployeeQR.route,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: ""
            AdminEmployeeQRScreen(navController = navController, uid = uid)
        }
        composable(Screen.AdminSedes.route) {
            SedesScreen(navController = navController, viewModel = null)
        }
        composable(Screen.AdminShiftSettings.route) {
            AdminRouteGuard(viewModel = null, navController = navController) {
                ShiftSettingsScreen(navController = navController, viewModel = null)
            }
        }
        composable(Screen.AdminShiftManager.route) {
            AdminRouteGuard(viewModel = null, navController = navController) {
                ShiftManagerScreen(navController = navController, viewModel = null)
            }
        }

        // Employee routes
        composable(Screen.EmployeeHome.route) {
            EmployeeDashboard(navController = navController, viewModel = null)
        }
        composable(Screen.EmployeeQR.route) {
            com.biosecure.app.ui.screens.employee.QRScreen(
                navController = navController,
                authRepository = authRepository,
                viewModel = null
            )
        }
        composable(Screen.EmployeeScan.route) {
            ScanScreen(navController = navController, isAdmin = false, viewModel = null)
        }
        composable(Screen.EmployeeHistory.route) {
            HistoryScreen(navController = navController, isAdmin = false, viewModel = null)
        }
        composable(Screen.EmployeeSettings.route) {
            SettingsScreen(
                navController = navController,
                isAdmin = false,
                isDarkMode = isDarkMode,
                onDarkModeChange = onDarkModeChange,
                language = language,
                onLanguageChange = onLanguageChange,
                viewModel = null
            )
        }
    }
}
