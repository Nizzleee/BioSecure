package com.biosecure.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.biosecure.app.data.repository.UserRepository
import com.biosecure.app.ui.navigation.NavGraph
import com.biosecure.app.ui.theme.BioSecureTheme
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.biosecure.app.ui.viewmodel.BioSecureViewModelFactory

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = BioSecureViewModelFactory(UserRepository())
        val viewModel = ViewModelProvider(this, factory)[BioSecureViewModel::class.java]

        setContent {
            var isDarkMode by remember { mutableStateOf(false) }

            BioSecureTheme(darkTheme = isDarkMode) {
                NavGraph(
                    isDarkMode = isDarkMode,
                    onDarkModeChange = { isDarkMode = it },
                    viewModel = viewModel
                )
            }
        }
    }
}
