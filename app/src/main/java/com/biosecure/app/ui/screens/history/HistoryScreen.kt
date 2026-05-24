package com.biosecure.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.ScanType
import com.biosecure.app.ui.screens.scan.BioSecureBottomBar
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel

@Composable
fun HistoryScreen(navController: NavController, isAdmin: Boolean = false, viewModel: BioSecureViewModel? = null) {

    val employeeRecords = listOf(
        Attendance(1, 1, "Juan Pérez", "", "Lunes, 12 Oct", "08:00 AM", "05:00 PM", AttendanceStatus.PUNTUAL, ScanType.HUELLA),
        Attendance(2, 1, "Juan Pérez", "", "Viernes, 09 Oct", "08:15 AM", "05:15 PM", AttendanceStatus.PUNTUAL, ScanType.FACIAL),
        Attendance(3, 1, "Juan Pérez", "", "Jueves, 08 Oct", "07:55 AM", "04:55 PM", AttendanceStatus.PUNTUAL, ScanType.HUELLA),
        Attendance(4, 1, "Juan Pérez", "", "Miércoles, 07 Oct", "", "", AttendanceStatus.INASISTENCIA, ScanType.HUELLA),
    )

    val adminRecords = listOf(
        Attendance(1, 1, "Javier Solís", "", "08:45 AM", "Planta Norte", "", AttendanceStatus.FALLIDO, ScanType.HUELLA),
        Attendance(2, 2, "María Paula", "", "08:32 AM", "Oficina Central", "", AttendanceStatus.EXITOSO, ScanType.FACIAL),
        Attendance(3, 3, "Ricardo Tovar", "", "08:15 AM", "Oficina Central", "", AttendanceStatus.EXITOSO, ScanType.HUELLA),
        Attendance(4, 4, "Elena Luna", "", "07:55 AM", "GPS Desconocido", "", AttendanceStatus.FALLIDO, ScanType.FACIAL),
    )

    val records = if (isAdmin) adminRecords else employeeRecords

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                BioSecureBottomBar(
                    navController = navController,
                    currentRoute = Screen.history(isAdmin),
                    isAdmin = isAdmin
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAdmin) "Historial Detallado" else "Mi Historial",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(text = "🔍", fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var searchQuery by remember { mutableStateOf("") }
                    val isDarkTheme = isSystemInDarkTheme()

                    if (isAdmin) {
                        // Card stats admin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = "👥", fontSize = 24.sp)
                                    Text(
                                        text = "124",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                    Text(text = "ACTIVO", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = "⚠️", fontSize = 24.sp)
                                    Text(
                                        text = "12",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ErrorRed
                                    )
                                    Text(text = "ALERTA", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Buscador
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar miembro por nombre o ID...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
                                unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
                                focusedBorderColor = Color(0xFF00B4A6),
                                unfocusedBorderColor = Color(0xFF00B4A6),
                                focusedContainerColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White,
                                unfocusedContainerColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White,
                                focusedLabelColor = Color(0xFF00B4A6),
                                unfocusedLabelColor = if (isDarkTheme) Color(0xFFCCCCCC) else Color.Gray,
                                cursorColor = Color(0xFF00B4A6)
                            )
                        )
                    } else {
                        // Card resumen empleado
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Resumen de Actividad",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TealLight
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Horas esta semana: 32h",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🟢", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Vas 4h por encima del promedio",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TealLight
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAdmin) "MARCACIONES RECIENTES" else "REGISTROS RECIENTES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (isAdmin) "Exportar CSV" else "Filtrar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(records) { record ->
                    AttendanceCard(record = record, isAdmin = isAdmin)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun AttendanceCard(record: Attendance, isAdmin: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (record.status == AttendanceStatus.INASISTENCIA || record.status == AttendanceStatus.FALLIDO)
                            ErrorRed.copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        record.status == AttendanceStatus.INASISTENCIA -> "🚫"
                        record.type == ScanType.HUELLA -> "☝️"
                        else -> "😊"
                    },
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAdmin) record.userName else record.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isAdmin)
                        "${record.checkIn} • ${record.location}"
                    else if (record.status == AttendanceStatus.INASISTENCIA)
                        "Sin registros para este día"
                    else
                        "Entrada: ${record.checkIn} | Salida: ${record.checkOut}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Badge estado
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when (record.status) {
                    AttendanceStatus.PUNTUAL, AttendanceStatus.EXITOSO -> SuccessGreen.copy(alpha = 0.15f)
                    AttendanceStatus.INASISTENCIA, AttendanceStatus.FALLIDO -> ErrorRed.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                }
            ) {
                Text(
                    text = when (record.status) {
                        AttendanceStatus.PUNTUAL -> "Puntual"
                        AttendanceStatus.EXITOSO -> "Exitoso"
                        AttendanceStatus.INASISTENCIA -> "Inasistencia"
                        AttendanceStatus.FALLIDO -> "Fuera de Horario"
                        AttendanceStatus.TARDANZA -> "Tardanza"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (record.status) {
                        AttendanceStatus.PUNTUAL, AttendanceStatus.EXITOSO -> SuccessGreen
                        AttendanceStatus.INASISTENCIA, AttendanceStatus.FALLIDO -> ErrorRed
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}