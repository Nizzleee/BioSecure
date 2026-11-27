package com.biosecure.app

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.biosecure.app.data.biometric.FaceTemplateStore
import com.biosecure.app.data.network.ApiConfig
import kotlinx.coroutines.runBlocking
import com.biosecure.app.data.repository.AttendanceRepository
import com.biosecure.app.data.repository.AuthRepository
import com.biosecure.app.data.repository.FunctionsRepository
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
        FaceTemplateStore.init(this)
        // Resuelve la URL pública del backend antes de crear los repositorios.
        runBlocking { ApiConfig.init(this@MainActivity) }

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

        val authRepository = AuthRepository(this)
        val attendanceRepository = AttendanceRepository(this)
        val functionsRepository = FunctionsRepository(this)
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
