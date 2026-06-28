package com.biosecure.app.ui.screens.history

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.ScanType
import com.biosecure.app.ui.components.LottieIcon
import com.biosecure.app.ui.components.navigateTab
import com.biosecure.app.ui.components.swipeToNavigate
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.screens.scan.BioSecureBottomBar
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.theme.LocalAppLanguage
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun HistoryScreen(navController: NavController, isAdmin: Boolean = false, viewModel: BioSecureViewModel? = null) {

    val attendanceHistory by (viewModel?.attendanceHistoryFlow ?: MutableStateFlow(emptyList())).collectAsState()
    val isLoading by (viewModel?.isLoading ?: MutableStateFlow(false)).collectAsState()
    val vmUid by (viewModel?.currentUserUid ?: MutableStateFlow<String?>(null)).collectAsState()
    val currentUserUid = vmUid ?: remember { FirebaseAuth.getInstance().currentUser?.uid }
    val sedes by (viewModel?.sedes ?: MutableStateFlow(emptyList<com.biosecure.app.data.model.Sede>())).collectAsState()

    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var sedeFilter by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel?.observeAttendanceHistory()
    }

    val records = attendanceHistory
        .let { list ->
            if (isAdmin && searchQuery.isNotEmpty())
                list.filter { it.userName.contains(searchQuery, ignoreCase = true) }
            else list
        }
        .let { list ->
            when (statusFilter) {
                "EXITOSO" -> list.filter { it.status == AttendanceStatus.EXITOSO || it.status == AttendanceStatus.PUNTUAL }
                "TARDANZA" -> list.filter { it.status == AttendanceStatus.TARDANZA }
                "INASISTENCIA" -> list.filter { it.status == AttendanceStatus.INASISTENCIA || it.status == AttendanceStatus.FALLIDO }
                else -> list
            }
        }
        .let { list ->
            if (isAdmin && sedeFilter != null)
                list.filter { it.sedeId == sedeFilter }
            else list
        }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigate(
                onSwipeLeft = { navController.navigateTab(Screen.settings(isAdmin)) },
                onSwipeRight = {
                    if (!isAdmin) navController.navigateTab(Screen.EmployeeScan.route)
                    else navController.navigateTab(Screen.AdminSedes.route)
                }
            ),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAdmin) (if (lang == "en") "Detailed History" else "Historial Detallado") else (if (lang == "en") "My History" else "Mi Historial"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(text = "🔍", fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "👥", fontSize = 24.sp)
                                    Text(
                                        text = "${attendanceHistory.size}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                    Text(text = if (lang == "en") "RECORDS" else "REGISTROS", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "⚠️", fontSize = 24.sp)
                                    Text(
                                        text = "${attendanceHistory.count { it.status == AttendanceStatus.FALLIDO || it.status == AttendanceStatus.INASISTENCIA }}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ErrorRed
                                    )
                                    Text(text = if (lang == "en") "ALERTS" else "ALERTA", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sedes filter chips
                        if (sedes.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = sedeFilter == null,
                                    onClick = { sedeFilter = null },
                                    label = { Text(if (lang == "en") "All Branches" else "Todas las Sedes", style = MaterialTheme.typography.labelSmall) }
                                )
                                sedes.forEach { sede ->
                                    FilterChip(
                                        selected = sedeFilter == sede.id,
                                        onClick = { sedeFilter = sede.id },
                                        label = { Text(sede.nombre, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    if (lang == "en") "Search member by name..." else "Buscar miembro por nombre...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                cursorColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "ALL" to (if (lang == "en") "All" else "Todos"),
                                "EXITOSO" to (if (lang == "en") "On Time" else "Exitoso"),
                                "TARDANZA" to (if (lang == "en") "Late" else "Tardanza"),
                                "INASISTENCIA" to (if (lang == "en") "Absent" else "Inasistencia")
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = statusFilter == key,
                                    onClick = { statusFilter = key },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = if (lang == "en") "Activity Summary" else "Resumen de Actividad",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${records.size} ${if (lang == "en") "records found" else "registros encontrados"}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🟢", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${records.count { it.status == AttendanceStatus.EXITOSO || it.status == AttendanceStatus.PUNTUAL }} ${if (lang == "en") "successful attendances" else "asistencias exitosas"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = White.copy(alpha = 0.85f)
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
                            text = if (isAdmin) (if (lang == "en") "RECENT CHECK-INS" else "MARCACIONES RECIENTES") else (if (lang == "en") "RECENT RECORDS" else "REGISTROS RECIENTES"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        if (!isAdmin) {
                            Text(
                                text = if (lang == "en") "Filter" else "Filtrar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { showFilters = !showFilters }
                            )
                        }
                        if (isAdmin) {
                            Text(
                                text = if (lang == "en") "Export CSV" else "Exportar CSV",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.clickable {
                                    val csv = buildString {
                                        appendLine(if (lang == "en") "Name,Date,Check-in,Status,Type,Location" else "Nombre,Fecha,Hora Entrada,Estado,Tipo,Ubicación")
                                        records.forEach { r ->
                                            appendLine("${r.userName},${r.date},${r.checkIn},${r.status.name},${r.type.name},${r.location}")
                                        }
                                    }
                                    val subject = if (lang == "en") "BioSecure Attendance Report" else "Reporte de Asistencia BioSecure"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                        putExtra(Intent.EXTRA_SUBJECT, subject)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, subject))
                                }
                            )
                        }
                    }

                    if (!isAdmin && showFilters) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "ALL" to (if (lang == "en") "All" else "Todos"),
                                "EXITOSO" to (if (lang == "en") "On Time" else "A tiempo"),
                                "TARDANZA" to (if (lang == "en") "Pending" else "Pendiente"),
                                "INASISTENCIA" to (if (lang == "en") "Absent" else "Inasistencia")
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = statusFilter == key,
                                    onClick = { statusFilter = key },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (records.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LottieIcon(assetName = "empty_state.json", modifier = Modifier.size(200.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (lang == "en") "No records yet" else "No hay registros aún",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(records) { record ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            AttendanceCard(
                                record = record,
                                isAdmin = isAdmin,
                                uid = if (!isAdmin) currentUserUid else null
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryQRCard(uid: String) {
    val qrBitmap: Bitmap? = remember(uid) {
        if (uid.isBlank()) return@remember null
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(uid, BarcodeFormat.QR_CODE, 512, 512)
            val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) { null }
    }

    if (qrBitmap != null) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Mi código QR",
                modifier = Modifier.size(160.dp).padding(8.dp)
            )
        }
    } else {
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AttendanceCard(record: Attendance, isAdmin: Boolean = false, uid: String? = null) {
    val lang = LocalAppLanguage.current
    var showQR by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = when (record.status) {
                                AttendanceStatus.INASISTENCIA, AttendanceStatus.FALLIDO ->
                                    ErrorRed.copy(alpha = 0.1f)
                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            record.status == AttendanceStatus.INASISTENCIA -> "🚫"
                            record.status == AttendanceStatus.TARDANZA -> "⏳"
                            record.type == ScanType.HUELLA -> "☝️"
                            record.type == ScanType.QR -> "📱"
                            else -> "😊"
                        },
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isAdmin) {
                        Text(
                            text = record.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${record.checkIn} • ${record.location}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = record.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = record.date,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        when (record.status) {
                            AttendanceStatus.INASISTENCIA -> Text(
                                text = if (lang == "en") "No records for this day" else "Sin registros para este día",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AttendanceStatus.TARDANZA -> Text(
                                text = "${if (lang == "en") "Registered at" else "Registrado a las"} ${record.checkIn} — ${if (lang == "en") "pending" else "pendiente"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> if (record.checkIn.isNotEmpty()) Text(
                                text = "${if (lang == "en") "Entry" else "Entrada"}: ${record.checkIn}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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
                            AttendanceStatus.PUNTUAL -> if (lang == "en") "On Time" else "Puntual"
                            AttendanceStatus.EXITOSO -> if (lang == "en") "On Time" else "Exitoso"
                            AttendanceStatus.INASISTENCIA -> if (lang == "en") "Absent" else "Inasistencia"
                            AttendanceStatus.FALLIDO -> if (lang == "en") "Off Schedule" else "Fuera de Horario"
                            AttendanceStatus.TARDANZA -> if (lang == "en") "Pending" else "Pendiente"
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

            if (!isAdmin && record.status != AttendanceStatus.FALLIDO && record.status != AttendanceStatus.INASISTENCIA && uid != null) {
                var showQRExpanded by remember { mutableStateOf(false) }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = { showQRExpanded = !showQRExpanded },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (showQRExpanded)
                                (if (lang == "en") "Hide QR" else "Ocultar QR")
                            else
                                (if (lang == "en") "Show QR" else "Mostrar QR"),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    AnimatedVisibility(
                        visible = showQRExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HistoryQRCard(uid = uid)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (lang == "en") "Show this to your administrator" else "Muestra este código a tu administrador",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
