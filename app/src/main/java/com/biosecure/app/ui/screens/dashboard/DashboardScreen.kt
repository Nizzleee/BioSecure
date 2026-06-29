package com.biosecure.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.data.model.User
import com.biosecure.app.ui.components.LottieIcon
import com.biosecure.app.ui.components.navigateTab
import com.biosecure.app.ui.components.swipeToNavigate
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.LocalAppLanguage
import com.biosecure.app.ui.screens.scan.BioSecureBottomBar
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.biosecure.app.ui.viewmodel.DashboardUiState
import kotlinx.coroutines.flow.MutableStateFlow

import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: BioSecureViewModel? = null, sedeId: String? = null) {

    val users by (viewModel?.users ?: MutableStateFlow(emptyList<User>())).collectAsState()
    val currentUser by (viewModel?.currentUser ?: MutableStateFlow(null)).collectAsState()
    val shifts by (viewModel?.shifts ?: MutableStateFlow(emptyList<com.biosecure.app.data.model.Shift>())).collectAsState()
    val sedes by (viewModel?.sedes ?: MutableStateFlow(emptyList<com.biosecure.app.data.model.Sede>())).collectAsState()
    val dashboardState by (viewModel?.dashboardState ?: MutableStateFlow(DashboardUiState())).collectAsState()
    val firestoreEmployees by (viewModel?.firestoreEmployees ?: MutableStateFlow(emptyList<Map<String, Any>>())).collectAsState()
    val currentUserName by (viewModel?.currentUserName ?: MutableStateFlow("")).collectAsState()
    val aiAnalysis by (viewModel?.aiAnalysis ?: MutableStateFlow(null)).collectAsState()
    val selectedSedeFromVm by (viewModel?.selectedSedeId ?: MutableStateFlow<String?>(null)).collectAsState()
    val attendanceHistory by (viewModel?.attendanceHistoryFlow ?: MutableStateFlow(emptyList())).collectAsState()
    val companyId by (viewModel?.currentCompanyId ?: MutableStateFlow("")).collectAsState()

    val lang = LocalAppLanguage.current
    var showManualDialog by remember { mutableStateOf(false) }
    var manualSearch by remember { mutableStateOf("") }
    var auditFilter by remember { mutableStateOf("ALL") }
    var selectedShiftId by remember { mutableStateOf<String?>(null) }
    var filterSedeId by remember { mutableStateOf<String?>(null) }
    var statsVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sedeId, companyId) {
        viewModel?.setSelectedSede(sedeId)
        viewModel?.loadUsers()
        viewModel?.loadFirestoreEmployees()
        viewModel?.observeAttendanceHistory()
        if (companyId.isNotEmpty()) viewModel?.loadCompanyConfig(companyId)
        statsVisible = true
    }

    if (showManualDialog) {
        ManualAttendanceDialog(
            users = users,
            sedes = sedes,
            search = manualSearch,
            onSearchChange = { manualSearch = it },
            onUserSelected = { user ->
                viewModel?.registerManualAttendance(user)
                showManualDialog = false
                manualSearch = ""
            },
            onDismiss = { showManualDialog = false; manualSearch = "" }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigate(
                onSwipeLeft = { navController.navigateTab(Screen.AdminSedes.route) }
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                BioSecureBottomBar(
                    navController,
                    currentRoute = Screen.Dashboard.route(sedeId),
                    isAdmin = true
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = dashboardState.isLoading && users.isNotEmpty(),
                onRefresh = {
                    viewModel?.setSelectedSede(selectedSedeFromVm)
                    viewModel?.loadUsers()
                    viewModel?.loadFirestoreEmployees()
                },
                modifier = Modifier.padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    if (dashboardState.isLoading && users.isEmpty()) {
                        item { DashboardSkeleton() }
                    } else {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            DashboardHeader(currentUserName, currentUser?.image)
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        item {
                            AnimatedVisibility(
                                visible = statsVisible,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MetricCard(
                                        modifier = Modifier.weight(1f),
                                        lottieAsset = "attendance.json",
                                        value = "${dashboardState.punctualityRate.toInt()}%",
                                        label = if (lang == "en") "Attendance" else "Asistencia",
                                        sublabel = if (lang == "en") "Today" else "Hoy",
                                        color = SuccessGreen
                                    )
                                    MetricCard(
                                        modifier = Modifier.weight(1f),
                                        lottieAsset = "alert.json",
                                        value = "${dashboardState.lateCount}",
                                        label = if (lang == "en") "Late" else "Tardanzas",
                                        sublabel = if (lang == "en") "Today" else "Hoy",
                                        color = ErrorRed
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            AnimatedVisibility(
                                visible = statsVisible,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        StatItem(
                                            value = "${dashboardState.punctualCount}",
                                            label = if (lang == "en") "On time" else "Puntuales",
                                            color = SuccessGreen
                                        )
                                        StatItem(
                                            value = "${dashboardState.absenceCount}",
                                            label = if (lang == "en") "Absences" else "Inasistencias",
                                            color = ErrorRed
                                        )
                                        StatItem(
                                            value = "${dashboardState.totalAttendances}",
                                            label = "Total",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))

                            // Sede selector chips
                            if (sedes.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedSedeFromVm == null,
                                        onClick = { viewModel?.setSelectedSede(null) },
                                        label = { Text(if (lang == "en") "All Branches" else "Todas las Sedes") }
                                    )
                                    sedes.forEach { sede ->
                                        FilterChip(
                                            selected = selectedSedeFromVm == sede.id,
                                            onClick = { viewModel?.setSelectedSede(sede.id) },
                                            label = { Text(sede.nombre) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Botón ABRIR ESCÁNER (visible solo cuando hay sede seleccionada)
                            if (selectedSedeFromVm != null) {
                                Button(
                                    onClick = { navController.navigate(Screen.AdminQRScan.route) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp).animateContentSize(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = if (lang == "en") "📷  OPEN SCANNER" else "📷  ABRIR ESCÁNER",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { navController.navigate(Screen.history(isAdmin = true)) },
                                    modifier = Modifier.weight(1f).height(48.dp).animateContentSize(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    LottieIcon(assetName = "history.json", modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (lang == "en") "History" else "Historial",
                                        color = White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Button(
                                    onClick = { showManualDialog = true },
                                    modifier = Modifier.weight(1f).height(48.dp).animateContentSize(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = ButtonDefaults.buttonElevation(2.dp)
                                ) {
                                    LottieIcon(assetName = "manual_register.json", modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (lang == "en") "Manual" else "Manual",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            QuickActionCard(
                                lottieAsset = "employees.json",
                                title = if (lang == "en") "View employees" else "Ver empleados",
                                subtitle = if (lang == "en") "Manage member list" else "Gestionar lista de miembros",
                                onClick = { navController.navigate(Screen.AdminEmployeeList.route(selectedSedeFromVm)) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            QuickActionCard(
                                lottieAsset = "add_employee.json",
                                title = if (lang == "en") "Register new employee" else "Registrar nuevo empleado",
                                subtitle = if (lang == "en") "Add member to system" else "Agregar miembro al sistema",
                                onClick = { navController.navigate(Screen.AdminRegisterEmployee.route) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            QuickActionCard(
                                lottieAsset = "schedule.json",
                                title = if (lang == "en") "Configure Schedules" else "Configurar Horarios",
                                subtitle = if (lang == "en") "Manage shifts and entry schedules" else "Gestionar turnos y horarios de entrada",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { navController.navigate(Screen.AdminShiftSettings.route) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            QuickActionCard(
                                lottieAsset = "shift_manager.json",
                                title = if (lang == "en") "Shift Manager" else "Gestor de Turnos",
                                subtitle = if (lang == "en") "Assign shifts and set tolerance" else "Asignar turnos a empleados y tolerancia",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = { navController.navigate(Screen.AdminShiftManager.route) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (lang == "en") "Weekly Statistics" else "Estadísticas Semanales",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            WeeklyChartCard(dashboardState.weeklyData)
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (lang == "en") "AI Analysis" else "Análisis IA",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                onClick = { viewModel?.getAiAnalysis() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✨", fontSize = 24.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = aiAnalysis ?: if (lang == "en") "Tap to generate an intelligent analysis for today." else "Toca para generar un análisis inteligente de hoy.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (lang == "en") "Today's Alerts" else "Alertas de hoy",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (shifts.isNotEmpty() || sedes.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = filterSedeId == null,
                                        onClick = { filterSedeId = null },
                                        label = { Text(if (lang == "en") "All Branches" else "Todas las Sedes") }
                                    )
                                    sedes.forEach { sede ->
                                        FilterChip(
                                            selected = filterSedeId == sede.id,
                                            onClick = { filterSedeId = sede.id },
                                            label = { Text(sede.nombre) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedShiftId == null,
                                        onClick = { selectedShiftId = null },
                                        label = { Text(if (lang == "en") "All Shifts" else "Todos los Turnos") }
                                    )
                                    shifts.forEach { shift ->
                                        FilterChip(
                                            selected = selectedShiftId == shift.id,
                                            onClick = { selectedShiftId = shift.id },
                                            label = { Text(shift.name) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        val filteredWarnings = dashboardState.recentWarnings.filter { warning ->
                            val user = users.find { it.uid == warning.userId }
                            val matchesShift = selectedShiftId == null || user?.shiftId == selectedShiftId
                            val matchesSede = filterSedeId == null || user?.sedeId == filterSedeId
                            matchesShift && matchesSede
                        }

                        if (filteredWarnings.isEmpty()) {
                            item {
                                Text(
                                    text = if (lang == "en") "No pending alerts today." else "No hay alertas pendientes hoy.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        filteredWarnings.take(5).forEachIndexed { index, attendance ->
                                            AnimatedVisibility(
                                                visible = true,
                                                enter = fadeIn(tween(500, delayMillis = index * 100)) +
                                                        slideInHorizontally(tween(500, delayMillis = index * 100))
                                            ) {
                                                Column {
                                                    AuditItem(
                                                        name = attendance.userName,
                                                        dept = attendance.checkIn,
                                                        score = "!",
                                                        badge = attendance.status.name
                                                    )
                                                    if (index < filteredWarnings.size - 1) {
                                                        HorizontalDivider(
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                                            modifier = Modifier.padding(vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (lang == "en") "Punctuality Audit" else "Auditoría de Puntualidad",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "ALL" to if (lang == "en") "All" else "Todos",
                                    "ALTA" to if (lang == "en") "High ≥80%" else "Alta ≥80%",
                                    "TARDANZA" to if (lang == "en") "Low <70%" else "Tardanza <70%"
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = auditFilter == key,
                                        onClick = { auditFilter = key },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val scoreByName = attendanceHistory
                                .groupBy { it.userName }
                                .mapValues { (_, records) ->
                                    val total = records.size
                                    val success = records.count { r ->
                                        r.status == com.biosecure.app.data.model.AttendanceStatus.EXITOSO ||
                                                r.status == com.biosecure.app.data.model.AttendanceStatus.PUNTUAL
                                    }
                                    if (total == 0) 0 else (success * 100 / total)
                                }

                            val auditEmployees = firestoreEmployees
                                .map { emp ->
                                    val name = emp["name"] as? String
                                        ?: "${emp["firstName"] as? String ?: ""} ${emp["lastName"] as? String ?: ""}".trim()
                                    val score = scoreByName[name] ?: 0
                                    Pair(name, score)
                                }
                                .sortedByDescending { it.second }
                                .let { list ->
                                    when (auditFilter) {
                                        "ALTA" -> list.filter { it.second >= 80 }
                                        "TARDANZA" -> list.filter { it.second < 70 }
                                        else -> list
                                    }
                                }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (auditEmployees.isEmpty()) {
                                        Text(
                                            text = if (lang == "en") "No employees" else "Sin empleados",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        auditEmployees.forEachIndexed { index, (name, score) ->
                                            val badge = when {
                                                score >= 90 -> if (lang == "en") "Top Tier" else "Destacado"
                                                score >= 70 -> if (lang == "en") "Consistent" else "Constante"
                                                score > 0 -> if (lang == "en") "Improve" else "Mejorar"
                                                else -> if (lang == "en") "No data" else "Sin datos"
                                            }
                                            AuditItem(name = name, dept = if (lang == "en") "Employee" else "Empleado", score = "$score%", badge = badge)
                                            if (index < auditEmployees.lastIndex) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyChartCard(data: List<Float>) {
    val chartData = if (data.isEmpty()) listOf(65f, 80f, 45f, 90f, 70f, 85f, 95f) else data
    val modelProducer = remember { CartesianChartModelProducer.build() }

    LaunchedEffect(chartData) {
        modelProducer.tryRunTransaction {
            lineSeries { series(chartData) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun DashboardHeader(userName: String, photoUrl: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl?.isNotEmpty() == true) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(photoUrl),
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(text = "👨‍💼", fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ADMIN PORTAL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (userName.isNotEmpty()) "${if (LocalAppLanguage.current == "en") "Welcome" else "Bienvenido"}, $userName" else if (LocalAppLanguage.current == "en") "Welcome" else "Bienvenido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen))
                }
            }
        }
        IconButton(onClick = { }) {
            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QuickActionCard(
    icon: String = "",
    lottieAsset: String? = null,
    title: String,
    subtitle: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lottieAsset != null) {
                    LottieIcon(assetName = lottieAsset, modifier = Modifier.size(36.dp))
                } else {
                    Text(text = icon, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.75f)
                    )
                }
            }
            Text(text = "›", fontSize = 20.sp, color = contentColor.copy(alpha = 0.75f))
        }
    }
}

@Composable
fun DashboardSkeleton() {
    Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(150.dp, 40.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).shimmerEffect())
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
            Box(modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
        }
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
    }
}

@Composable
private fun ManualAttendanceDialog(
    users: List<User>,
    sedes: List<com.biosecure.app.data.model.Sede>,
    search: String,
    onSearchChange: (String) -> Unit,
    onUserSelected: (User) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    var selectedSedeId by remember { mutableStateOf<String?>(null) }

    val filteredBySede: List<User> = if (selectedSedeId == null) {
        emptyList()
    } else {
        users.filter { it.sedeId == selectedSedeId }
    }

    val filtered: List<User> = filteredBySede.filter {
        "${it.firstName} ${it.lastName}".contains(search, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (selectedSedeId == null)
                    (if (lang == "en") "Select Branch" else "Seleccionar Sede")
                else
                    (if (lang == "en") "Manual registration" else "Registro manual"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedSedeId == null) {
                    Text(
                        text = if (lang == "en") "Choose a branch to list employees:" else "Elija la sede para listar los empleados:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (sedes.isEmpty()) {
                        Text(
                            if (lang == "en") "No branches registered" else "No hay sedes registradas",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        sedes.forEach { sede ->
                            Card(
                                onClick = { selectedSedeId = sede.id },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(
                                    text = sede.nombre,
                                    modifier = Modifier.padding(16.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                } else {
                    val selectedSedeName = sedes.find { it.id == selectedSedeId }?.nombre ?: "Sede"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sede: $selectedSedeName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { selectedSedeId = null; onSearchChange("") }) {
                            Text(if (lang == "en") "Change" else "Cambiar")
                        }
                    }

                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearchChange,
                        label = { Text(if (lang == "en") "Search employee" else "Buscar empleado", color = MaterialTheme.colorScheme.onSurface) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    if (filtered.isEmpty()) {
                        Text(
                            text = if (search.isEmpty())
                                (if (lang == "en") "Loading employees…" else "Cargando empleados…")
                            else
                                (if (lang == "en") "No results" else "Sin resultados"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filtered.take(6).forEach { user ->
                            Card(
                                onClick = { onUserSelected(user) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = "👤", fontSize = 20.sp)
                                    Column {
                                        Text(
                                            text = "${user.firstName} ${user.lastName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = user.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "en") "Cancel" else "Cancelar", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    icon: String = "",
    lottieAsset: String? = null,
    value: String,
    label: String,
    sublabel: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (lottieAsset != null) {
                LottieIcon(assetName = lottieAsset, modifier = Modifier.size(48.dp))
            } else {
                Text(text = icon, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = sublabel, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AuditItem(name: String, dept: String, score: String, badge: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "👤", fontSize = 36.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = dept, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = score, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(text = badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
