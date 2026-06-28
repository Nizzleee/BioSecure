package com.biosecure.app

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.biosecure.app.data.repository.AttendanceRepository
import com.biosecure.app.data.repository.AuthRepository
import com.biosecure.app.data.repository.FirebaseFunctionsRepository
import com.biosecure.app.data.prefs.ThemePreferences
import androidx.compose.foundation.isSystemInDarkTheme
import com.biosecure.app.ui.navigation.NavGraph
import com.biosecure.app.ui.theme.BioSecureTheme
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.biosecure.app.ui.viewmodel.BioSecureViewModelFactory

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1001
            )
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1001
            )
        }

        val authRepository = AuthRepository()
        val attendanceRepository = AttendanceRepository()
        val functionsRepository = FirebaseFunctionsRepository()
        val themePreferences = ThemePreferences(this)
        val factory = BioSecureViewModelFactory(authRepository, attendanceRepository, functionsRepository, themePreferences)
        val viewModel = ViewModelProvider(this, factory)[BioSecureViewModel::class.java]

        setContent {
            val isDarkPref by viewModel.isDarkMode.collectAsState()
            val systemTheme = isSystemInDarkTheme()
            val isDarkMode = isDarkPref ?: systemTheme
            val language by viewModel.currentLanguage.collectAsState()

            BioSecureTheme(darkTheme = isDarkMode) {
                NavGraph(
                    startDestination = com.biosecure.app.ui.navigation.Screen.Login.route,
                    isDarkMode = isDarkMode,
                    onDarkModeChange = { viewModel.setDarkMode(it) },
                    language = language,
                    onLanguageChange = { viewModel.setLanguage(it) },
                    viewModel = viewModel,
                    authRepository = authRepository
                )
            }
        }
    }
}
