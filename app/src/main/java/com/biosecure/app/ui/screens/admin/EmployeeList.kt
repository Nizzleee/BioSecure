package com.biosecure.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.data.model.User
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.ErrorRed
import com.biosecure.app.ui.theme.SuccessGreen
import com.biosecure.app.ui.theme.shimmerEffect
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeListScreen(navController: NavController, viewModel: BioSecureViewModel? = null, sedeId: String? = null) {
    val allEmployees by (viewModel?.users ?: MutableStateFlow(emptyList<User>())).collectAsState()
    val employees = if (sedeId != null) allEmployees.filter { it.sedeId == sedeId } else allEmployees
    val shifts by (viewModel?.shifts ?: MutableStateFlow(emptyList<com.biosecure.app.data.model.Shift>())).collectAsState()
    val isLoading by (viewModel?.isLoading ?: MutableStateFlow(false)).collectAsState()

    var selectedEmployee by remember { mutableStateOf<User?>(null) }
    var showDeleteDialog by remember { mutableStateOf<User?>(null) }
    var showShiftDialog by remember { mutableStateOf<User?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel?.loadUsers()
        viewModel?.currentCompanyId?.value?.let {
            if (it.isNotEmpty()) viewModel.loadCompanyConfig(it)
        }
    }

    if (showSheet && selectedEmployee != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            EmployeeActionSheetContent(
                user = selectedEmployee!!,
                onEdit = {
                    showSheet = false
                    navController.navigate(Screen.AdminEditEmployee.route(it.uid))
                },
                onToggleStatus = {
                    viewModel?.toggleEmployeeStatus(it.uid, it.isActive)
                    showSheet = false
                },
                onAssignShift = {
                    showSheet = false
                    showShiftDialog = it
                },
                onDelete = {
                    showSheet = false
                    showDeleteDialog = it
                }
            )
        }
    }

    if (showShiftDialog != null) {
        AlertDialog(
            onDismissRequest = { showShiftDialog = null },
            title = { Text("Asignar Turno", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    shifts.forEach { shift ->
                        ListItem(
                            headlineContent = { Text(shift.name, color = MaterialTheme.colorScheme.onSurface) },
                            supportingContent = { Text("${shift.startTime} - ${shift.endTime}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.clickable {
                                viewModel?.assignShiftToEmployee(showShiftDialog!!.uid, shift.id)
                                showShiftDialog = null
                            },
                            trailingContent = {
                                if (showShiftDialog?.shiftId == shift.id) {
                                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen)
                                }
                            }
                        )
                    }
                    if (shifts.isEmpty()) {
                        Text(
                            "No hay turnos configurados. Ve a ajustes para crearlos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShiftDialog = null }) { Text("Cerrar", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }

    showDeleteDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Eliminar empleado", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("¿Estás seguro de eliminar a ${user.firstName} ${user.lastName}?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel?.deleteFirestoreEmployee(user.uid)
                    showDeleteDialog = null
                }) {
                    Text("Eliminar", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Gestión de Empleados", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (isLoading && employees.isEmpty()) {
                items(6) { EmployeeSkeletonItem() }
            } else {
                item {
                    Text(
                        "${employees.size} miembros en total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(employees, key = { it.uid }) { user ->
                    val shiftName = shifts.find { it.id == user.shiftId }?.name ?: ""
                    EmployeeCard(
                        user = user,
                        onActionClick = {
                            selectedEmployee = user
                            showSheet = true
                        },
                        shiftName = shiftName
                    )
                }
            }
        }
    }
}

@Composable
fun EmployeeCard(user: User, onActionClick: () -> Unit, shiftName: String = "") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.firstName.take(1) + user.lastName.take(1),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (user.isActive) SuccessGreen else Color.Gray)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${user.firstName} ${user.lastName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.cargo.ifEmpty { "Sin cargo" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (shiftName.isNotEmpty()) {
                        Text(
                            " • $shiftName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (!user.isActive) {
                    Text(
                        "Cuenta Desactivada",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(onClick = onActionClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Acciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmployeeActionSheetContent(
    user: User,
    onEdit: (User) -> Unit,
    onToggleStatus: (User) -> Unit,
    onAssignShift: (User) -> Unit,
    onDelete: (User) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        ListItem(
            headlineContent = { Text("Editar información") },
            leadingContent = { Icon(Icons.Default.Edit, null) },
            modifier = Modifier.clickable { onEdit(user) }
        )
        ListItem(
            headlineContent = { Text("Asignar Turno") },
            leadingContent = { Icon(Icons.Default.Schedule, null) },
            modifier = Modifier.clickable { onAssignShift(user) }
        )
        ListItem(
            headlineContent = { Text(if (user.isActive) "Desactivar empleado" else "Activar empleado") },
            supportingContent = { Text(if (user.isActive) "No podrá marcar asistencia" else "Podrá volver a marcar") },
            leadingContent = { Icon(Icons.Default.PowerSettingsNew, null) },
            trailingContent = { Switch(checked = user.isActive, onCheckedChange = { onToggleStatus(user) }) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Eliminar permanentemente", color = ErrorRed) },
            leadingContent = { Icon(Icons.Default.Delete, null, tint = ErrorRed) },
            modifier = Modifier.clickable { onDelete(user) }
        )
    }
}

@Composable
fun EmployeeSkeletonItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).shimmerEffect())
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.width(120.dp).height(16.dp).shimmerEffect())
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.width(80.dp).height(12.dp).shimmerEffect())
        }
    }
}
