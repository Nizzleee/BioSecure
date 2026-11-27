package com.biosecure.app.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.R
import com.biosecure.app.data.repository.AuthRepository
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    navController: NavController,
    authRepository: AuthRepository?,
    viewModel: BioSecureViewModel? = null
) {
    val scale = remember { Animatable(0.3f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        delay(2000L)
        if (authRepository?.isLoggedIn() == true) {
            viewModel?.refreshSession()
            delay(500)
            val role = authRepository.getRoleForCurrentUser()
            viewModel?.setRole(role ?: "employee")
            val destination = if (role == "admin") Screen.AdminSedes.route else Screen.EmployeeScan.route
            navController.navigate(destination) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D3B35)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.penguin_splash),
                contentDescription = "BioSecure Logo",
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale.value)
            )
            Text(
                text = "BioSecure",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Sistema de Asistencia Biométrica",
                fontSize = 14.sp,
                color = Color(0xFF00B4A6)
            )
        }
    }
}
