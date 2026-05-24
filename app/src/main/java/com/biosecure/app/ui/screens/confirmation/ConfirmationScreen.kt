package com.biosecure.app.ui.screens.confirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biosecure.app.data.model.User
import com.biosecure.app.data.network.AttendanceRequest
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel

@Composable
fun ConfirmationScreen(
    navController: NavController,
    viewModel: BioSecureViewModel? = null
) {
    val nullUserFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<User?>(null) }
    val nullAttFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<AttendanceRequest?>(null) }
    val defaultRoleFlow = remember { kotlinx.coroutines.flow.MutableStateFlow("employee") }

    val currentUser by (viewModel?.currentUser ?: nullUserFlow).collectAsState()
    val lastAttendance by (viewModel?.lastAttendance ?: nullAttFlow).collectAsState()
    val currentRole by (viewModel?.currentRole ?: defaultRoleFlow).collectAsState()

    val isAdmin = currentRole == "admin"
    val userName = currentUser?.let { "${it.firstName} ${it.lastName}" } ?: "Usuario"
    val checkIn = lastAttendance?.checkIn ?: "--:--"
    val scanLabel = when (lastAttendance?.scanType) {
        "HUELLA" -> "Huella Dactilar"
        "FACIAL" -> "Escaneo Facial"
        else -> "Biométrico"
    }
    val location = lastAttendance?.location ?: "Sede Central"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ícono de éxito
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(SuccessGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(76.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¡Asistencia Registrada!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tu ingreso ha sido registrado exitosamente",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Card con detalles reales
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    ConfirmationRow(label = "Empleado", value = userName)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    ConfirmationRow(label = "Hora de ingreso", value = checkIn)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    ConfirmationRow(label = "Método", value = scanLabel)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    ConfirmationRow(label = "Sede", value = location)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    navController.navigate(Screen.history(isAdmin)) {
                        popUpTo(Screen.Confirmation.route) { inclusive = true }
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
                    text = "Ver mi historial",
                    color = White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    navController.navigate(Screen.scan(isAdmin)) {
                        popUpTo(Screen.Confirmation.route) { inclusive = true }
                    }
                }
            ) {
                Text(
                    text = "Registrar otro ingreso",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ConfirmationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
