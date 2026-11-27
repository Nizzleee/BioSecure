package com.biosecure.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.data.model.Shift
import com.biosecure.app.data.model.User
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftManagerScreen(navController: NavController, viewModel: BioSecureViewModel? = null) {
    val shifts by (viewModel?.shifts ?: MutableStateFlow(emptyList<Shift>())).collectAsState()
    val users by (viewModel?.users ?: MutableStateFlow(emptyList<User>())).collectAsState()
    val isLoading by (viewModel?.isLoading ?: MutableStateFlow(false)).collectAsState()

    var showAddShiftSheet by remember { mutableStateOf(false) }
    var showAssignUserSheet by remember { mutableStateOf(false) }
    var selectedShiftForAssignment by remember { mutableStateOf<Shift?>(null) }
    var editingShift by remember { mutableStateOf<Shift?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajuste de Horarios", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingShift = null; showAddShiftSheet = true },
                icon = { Icon(Icons.Default.Add, null, tint = Color.White) },
                text = { Text("Nuevo Horario", color = Color.White) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Horarios configurados",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (isLoading && shifts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (shifts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay horarios registrados",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shifts) { shift ->
                        ShiftItemCard(
                            shift = shift,
                            employeeCount = users.count { it.shiftId == shift.id },
                            onEdit = {
                                editingShift = shift
                                showAddShiftSheet = true
                            },
                            onAssign = {
                                selectedShiftForAssignment = shift
                                showAssignUserSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddShiftSheet) {
        AddShiftBottomSheet(
            shift = editingShift,
            onDismiss = { showAddShiftSheet = false },
            onSave = { name, start, end, tolerance ->
                viewModel?.saveShift(name, start, tolerance, end)
                showAddShiftSheet = false
            }
        )
    }

    if (showAssignUserSheet && selectedShiftForAssignment != null) {
        AssignShiftBottomSheet(
            shift = selectedShiftForAssignment!!,
            users = users,
            onDismiss = { showAssignUserSheet = false },
            onAssign = { userId ->
                viewModel?.assignShiftToEmployee(userId, selectedShiftForAssignment!!.id)
            }
        )
    }
}

@Composable
fun ShiftItemCard(shift: Shift, employeeCount: Int, onEdit: () -> Unit, onAssign: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        shift.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${shift.startTime} - ${shift.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${shift.toleranceMin}m tol.",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$employeeCount empleados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onAssign) {
                    Text("Asignar empleados", color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShiftBottomSheet(
    shift: Shift?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf(shift?.name ?: "Tiempo Completo") }
    var startTime by remember { mutableStateOf(shift?.startTime ?: "08:00") }
    var endTime by remember { mutableStateOf(shift?.endTime ?: "17:00") }
    var tolerance by remember { mutableStateOf(shift?.toleranceMin?.toString() ?: "5") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (shift == null) "Nuevo Horario" else "Editar Horario",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
                FilterChip(
                    selected = name == "Tiempo Completo",
                    onClick = { name = "Tiempo Completo"; startTime = "08:00"; endTime = "17:00" },
                    label = { Text("Completo") },
                    colors = chipColors
                )
                FilterChip(
                    selected = name == "Medio Tiempo",
                    onClick = { name = "Medio Tiempo"; startTime = "08:00"; endTime = "12:00" },
                    label = { Text("Medio") },
                    colors = chipColors
                )
                FilterChip(
                    selected = name != "Tiempo Completo" && name != "Medio Tiempo",
                    onClick = { name = "Personalizado" },
                    label = { Text("Personalizado") },
                    colors = chipColors
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del horario") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Hora Entrada") },
                    placeholder = { Text("HH:mm") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("Hora Salida") },
                    placeholder = { Text("HH:mm") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = tolerance,
                onValueChange = { tolerance = it },
                label = { Text("Tolerancia (minutos)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Text("min") }
            )

            Button(
                onClick = { onSave(name, startTime, endTime, tolerance.toIntOrNull() ?: 5) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Guardar Horario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignShiftBottomSheet(
    shift: Shift,
    users: List<User>,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.7f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Asignar a: ${shift.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(users) { user ->
                    val isAssigned = user.shiftId == shift.id
                    ListItem(
                        headlineContent = {
                            Text(
                                "${user.firstName} ${user.lastName}",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                user.email,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Person,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isAssigned,
                                onCheckedChange = { if (!isAssigned) onAssign(user.uid) }
                            )
                        },
                        modifier = Modifier.clickable { if (!isAssigned) onAssign(user.uid) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
