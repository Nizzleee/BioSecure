package com.biosecure.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
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
import com.biosecure.app.ui.screens.admin.RegisterEmployeeScreen
import com.biosecure.app.ui.screens.admin.SedesScreen
import com.biosecure.app.ui.screens.admin.ShiftManagerScreen
import com.biosecure.app.ui.screens.admin.ShiftSettingsScreen
import com.biosecure.app.ui.screens.confirmation.ConfirmationScreen
import com.biosecure.app.ui.screens.dashboard.DashboardScreen
import com.biosecure.app.ui.screens.employee.EmployeeDashboard
import com.biosecure.app.ui.screens.employee.QRScreen
import com.biosecure.app.ui.screens.history.HistoryScreen
import com.biosecure.app.ui.screens.login.LoginScreen
import com.biosecure.app.ui.screens.scan.ScanScreen
import com.biosecure.app.ui.screens.settings.SettingsScreen
import com.biosecure.app.ui.screens.splash.SplashScreen
import com.biosecure.app.ui.theme.LocalAppLanguage
import com.biosecure.app.ui.viewmodel.BioSecureViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Confirmation : Screen("confirmation")

    // Admin routes
    object Dashboard : Screen("admin/dashboard/{sedeId}") {
        fun route(sedeId: String?) = "admin/dashboard/${sedeId ?: "null"}"
    }
    object AdminScan : Screen("admin/scan")
    object AdminHistory : Screen("admin/history")
    object AdminSettings : Screen("admin/settings")
    object AdminRegisterEmployee : Screen("admin/register-employee")
    object AdminEmployeeList : Screen("admin/employee-list/{sedeId}") {
        fun route(sedeId: String?) = "admin/employee-list/${sedeId ?: "null"}"
    }
    object AdminEditEmployee : Screen("admin/edit-employee/{userId}") {
        fun route(userId: String) = "admin/edit-employee/$userId"
    }
    object AdminQRScan : Screen("admin/qr-scan")
    object AdminEmployeeQR : Screen("admin/employee-qr/{uid}") {
        fun route(uid: String) = "admin/employee-qr/$uid"
    }
    object AdminShiftSettings : Screen("admin/shift-settings")
    object AdminShiftManager : Screen("admin/shift-manager")
    object AdminSedes : Screen("admin/sedes")

    // Employee routes
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
fun AdminRouteGuard(
    viewModel: BioSecureViewModel?,
    navController: NavController,
    content: @Composable () -> Unit
) {
    if (viewModel == null) {
        content()
        return
    }
    val role by viewModel.currentRole.collectAsState()
    val hasNavigated = remember { mutableStateOf(false) }
    LaunchedEffect(role) {
        if (!hasNavigated.value && role.isNotEmpty() && role != "admin") {
            if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                hasNavigated.value = true
                navController.navigate(Screen.EmployeeHome.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
        }
    }
    if (role == "admin") content()
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    language: String = "es",
    onLanguageChange: (String) -> Unit = {},
    viewModel: BioSecureViewModel? = null,
    authRepository: AuthRepository? = null
) {
    val enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 4 })
    val exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 4 })
    val popEnter = fadeIn() + slideInHorizontally(initialOffsetX = { -it / 4 })
    val popExit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 4 })

    CompositionLocalProvider(LocalAppLanguage provides language) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { popEnter },
        popExitTransition = { popExit }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                navController = navController,
                authRepository = authRepository,
                viewModel = viewModel
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(navController = navController, viewModel = viewModel)
        }

        composable(Screen.Confirmation.route) {
            ConfirmationScreen(navController = navController, viewModel = viewModel)
        }

        // Admin routes
        composable(
            route = Screen.Dashboard.route,
            arguments = listOf(
                navArgument("sedeId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rawSedeId = backStackEntry.arguments?.getString("sedeId")
            val sedeId = if (rawSedeId == "null") null else rawSedeId
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                DashboardScreen(navController = navController, viewModel = viewModel, sedeId = sedeId)
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
                    language = language,
                    onLanguageChange = onLanguageChange,
                    viewModel = viewModel
                )
            }
        }
        composable(Screen.AdminRegisterEmployee.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                RegisterEmployeeScreen(navController = navController, viewModel = viewModel)
            }
        }
        composable(
            route = Screen.AdminEmployeeList.route,
            arguments = listOf(
                navArgument("sedeId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rawSedeId = backStackEntry.arguments?.getString("sedeId")
            val sedeId = if (rawSedeId == "null") null else rawSedeId
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                EmployeeListScreen(navController = navController, viewModel = viewModel, sedeId = sedeId)
            }
        }
        composable(
            route = Screen.AdminEditEmployee.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            if (userId != null) {
                AdminRouteGuard(viewModel = viewModel, navController = navController) {
                    EditEmployeeScreen(navController = navController, userId = userId, viewModel = viewModel)
                }
            }
        }
        composable(Screen.AdminQRScan.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                QRScannerScreen(navController = navController, viewModel = viewModel)
            }
        }
        composable(
            route = Screen.AdminEmployeeQR.route,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: ""
            AdminEmployeeQRScreen(navController = navController, uid = uid)
        }
        composable(Screen.AdminShiftSettings.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                ShiftSettingsScreen(navController = navController, viewModel = viewModel)
            }
        }
        composable(Screen.AdminShiftManager.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                ShiftManagerScreen(navController = navController, viewModel = viewModel)
            }
        }
        composable(Screen.AdminSedes.route) {
            AdminRouteGuard(viewModel = viewModel, navController = navController) {
                SedesScreen(navController = navController, viewModel = viewModel)
            }
        }

        // Employee routes
        composable(Screen.EmployeeHome.route) {
            EmployeeDashboard(navController = navController, viewModel = viewModel)
        }
        composable(Screen.EmployeeQR.route) {
            QRScreen(
                navController = navController,
                authRepository = authRepository,
                viewModel = viewModel
            )
        }
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
                language = language,
                onLanguageChange = onLanguageChange,
                viewModel = viewModel
            )
        }
    }
    } // CompositionLocalProvider
}
