package com.biosecure.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel

@Composable
fun LoginScreen(navController: NavController, viewModel: BioSecureViewModel? = null) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val isDarkTheme = isSystemInDarkTheme()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
        unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
        focusedBorderColor = Color(0xFF00B4A6),
        unfocusedBorderColor = Color(0xFF00B4A6),
        focusedLabelColor = Color(0xFF00B4A6),
        unfocusedLabelColor = if (isDarkTheme) Color(0xFFCCCCCC) else Color.Gray,
        cursorColor = Color(0xFF00B4A6),
        focusedContainerColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White,
        unfocusedContainerColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header verde
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔒",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "BioSecure",
                    style = MaterialTheme.typography.headlineLarge,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sistema de Asistencia Biométrica",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TealLight
                )
            }
        }

        // Card de login
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .align(Alignment.Center)
                .offset(y = 60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Iniciar Sesión",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors
                )

                // Botón empleado
                Button(
                    onClick = {
                        viewModel?.loginAsEmployee(1)
                        navController.navigate(Screen.EmployeeScan.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Entrar como Empleado",
                        color = White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Botón admin
                OutlinedButton(
                    onClick = {
                        viewModel?.loginAsAdmin()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isDarkTheme) Color.White else Color(0xFF0D3B35)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B4A6))
                ) {
                    Text(
                        text = "Entrar como Admin",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}