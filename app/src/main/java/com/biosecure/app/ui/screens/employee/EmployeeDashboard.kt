package com.biosecure.app.ui.screens.employee

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.ui.components.navigateTab
import com.biosecure.app.ui.components.swipeToNavigate
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.screens.scan.BioSecureBottomBar
import com.biosecure.app.ui.theme.ErrorRed
import com.biosecure.app.ui.theme.LocalAppLanguage
import com.biosecure.app.ui.theme.SuccessGreen
import com.biosecure.app.ui.theme.WarnOrange
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EmployeeDashboard(
    navController: NavController,
    viewModel: BioSecureViewModel? = null
) {
    val lang = LocalAppLanguage.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUserName by (viewModel?.currentUserName ?: MutableStateFlow("")).collectAsState()
    val attendanceHistory by (viewModel?.attendanceHistoryFlow ?: MutableStateFlow(emptyList<Attendance>())).collectAsState()
    LaunchedEffect(Unit) {
        viewModel?.observeAttendanceHistory()
    }

    val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    val todayRecord = attendanceHistory.firstOrNull { it.date == todayStr }

    val calendar = Calendar.getInstance()
    val weekRecords = (0..6).map { offset ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_YEAR, offset)
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
        val dayLabel = if (lang == "en") {
            listOf("M", "T", "W", "T", "F", "S", "S")[offset]
        } else {
            listOf("L", "M", "X", "J", "V", "S", "D")[offset]
        }
        Triple(dateStr, dayLabel, attendanceHistory.firstOrNull { it.date == dateStr })
    }

    val confirmedDays = weekRecords.count { (_, _, rec) ->
        rec != null && (rec.status == AttendanceStatus.EXITOSO || rec.status == AttendanceStatus.PUNTUAL)
    }

    val dateDisplay = SimpleDateFormat(
        if (lang == "en") "EEEE, MMMM d" else "EEEE, d 'de' MMMM",
        if (lang == "en") Locale.ENGLISH else Locale("es")
    ).format(Date())

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigate(
                onSwipeLeft = {
                    if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        navController.navigateTab(Screen.EmployeeScan.route)
                    }
                }
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                BioSecureBottomBar(
                    navController = navController,
                    currentRoute = Screen.EmployeeHome.route,
                    isAdmin = false
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${if (lang == "en") "Hello" else "Hola"}, ${currentUserName.ifEmpty { if (lang == "en") "Employee" else "Empleado" }}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = dateDisplay.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUserName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Today's Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (lang == "en") "Today's Status" else "Estado de Hoy",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        when {
                            todayRecord == null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "📋", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (lang == "en") "No check-in yet" else "Sin registro aún",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (lang == "en") "Go to Scan to register your attendance" else "Ve a Escaneo para registrar tu asistencia",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            todayRecord.status == AttendanceStatus.TARDANZA -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "⏳", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (lang == "en") "Pending confirmation" else "Pendiente de confirmación",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = WarnOrange
                                        )
                                        Text(
                                            text = "${if (lang == "en") "Registered at" else "Registrado a las"} ${todayRecord.checkIn}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            todayRecord.status == AttendanceStatus.INASISTENCIA || todayRecord.status == AttendanceStatus.FALLIDO -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🚫", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (lang == "en") "Absence recorded" else "Inasistencia registrada",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ErrorRed
                                        )
                                        Text(
                                            text = if (lang == "en") "Contact your administrator" else "Contacta a tu administrador",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "✅", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (lang == "en") "Attendance confirmed today" else "Asistencia confirmada hoy",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                        Text(
                                            text = "${if (lang == "en") "Entry registered" else "Entrada registrada"}: ${todayRecord.checkIn}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                    }
                }

                // Weekly Performance Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (lang == "en") "This week's performance" else "Rendimiento de esta semana",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekRecords.forEach { (dateStr, dayLabel, rec) ->
                                val isFuture = run {
                                    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    val d = fmt.parse(dateStr)
                                    d != null && d.after(Date())
                                }
                                val circleColor = when {
                                    rec == null || isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    rec.status == AttendanceStatus.EXITOSO || rec.status == AttendanceStatus.PUNTUAL -> SuccessGreen.copy(alpha = 0.2f)
                                    rec.status == AttendanceStatus.TARDANZA -> WarnOrange.copy(alpha = 0.2f)
                                    else -> ErrorRed.copy(alpha = 0.2f)
                                }
                                val textColor = when {
                                    rec == null || isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    rec.status == AttendanceStatus.EXITOSO || rec.status == AttendanceStatus.PUNTUAL -> SuccessGreen
                                    rec.status == AttendanceStatus.TARDANZA -> WarnOrange
                                    else -> ErrorRed
                                }
                                val emoji = when {
                                    rec == null || isFuture -> ""
                                    rec.status == AttendanceStatus.EXITOSO || rec.status == AttendanceStatus.PUNTUAL -> "✓"
                                    rec.status == AttendanceStatus.TARDANZA -> "!"
                                    else -> "✗"
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(circleColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 14.sp,
                                            color = textColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "$confirmedDays / 7 ${if (lang == "en") "days with confirmed attendance" else "días con asistencia confirmada"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Update status button
                OutlinedButton(
                    onClick = { viewModel?.observeAttendanceHistory() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar estado",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
