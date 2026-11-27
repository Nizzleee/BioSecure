package com.biosecure.app.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biosecure.app.data.model.Sede
import com.biosecure.app.data.model.Shift
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterEmployeeScreen(navController: NavController, viewModel: BioSecureViewModel? = null) {
    val context = LocalContext.current

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var cargo by remember { mutableStateOf("") }
    var departamento by remember { mutableStateOf("") }

    var selectedShiftId by remember { mutableStateOf("") }
    var selectedShiftName by remember { mutableStateOf("") }
    var shiftDropdownExpanded by remember { mutableStateOf(false) }

    var selectedSedeId by remember { mutableStateOf("") }
    var selectedSedeName by remember { mutableStateOf("") }
    var sedeDropdownExpanded by remember { mutableStateOf(false) }

    val shifts by (viewModel?.shifts ?: MutableStateFlow(emptyList<Shift>())).collectAsState()
    val sedes by (viewModel?.sedes ?: MutableStateFlow(emptyList<Sede>())).collectAsState()
    val companyId by (viewModel?.currentCompanyId ?: MutableStateFlow("")).collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel?.loadCompanyConfig(companyId)
    }

    val isFormValid = firstName.isNotBlank() && lastName.isNotBlank() &&
            email.isNotBlank() && password.length >= 6 &&
            cargo.isNotBlank() && departamento.isNotBlank()

    val fieldShape = RoundedCornerShape(12.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Registrar Empleado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Datos del nuevo empleado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
                singleLine = true
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Apellido") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
                singleLine = true
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña (mín. 6 caracteres)") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = cargo,
                onValueChange = { cargo = it },
                label = { Text("Cargo") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
                singleLine = true
            )
            OutlinedTextField(
                value = departamento,
                onValueChange = { departamento = it },
                label = { Text("Departamento") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors,
                singleLine = true
            )

            // Sede selection header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sede",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = { navController.navigate(Screen.AdminSedes.route) }) {
                    Text("Gestionar Sedes", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Sede dropdown
            if (sedes.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                ) {
                    Text(
                        "No hay sedes registradas. Pulsa 'Gestionar' para añadir una.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = sedeDropdownExpanded,
                    onExpandedChange = { sedeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (selectedSedeName.isEmpty()) "Seleccionar sede" else selectedSedeName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = fieldShape,
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = sedeDropdownExpanded,
                        onDismissRequest = { sedeDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin sede asignada", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                selectedSedeId = ""
                                selectedSedeName = ""
                                sedeDropdownExpanded = false
                            }
                        )
                        sedes.forEach { sede ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(sede.nombre, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            "Arequipa, Peru",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedSedeId = sede.id
                                    selectedSedeName = sede.nombre
                                    sedeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Shift selection header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Turno",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = { navController.navigate(Screen.AdminShiftSettings.route) }) {
                    Text("Configurar Horarios", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Shift dropdown
            if (shifts.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                ) {
                    Text(
                        "No hay turnos configurados. Pulsa 'Configurar' para añadir uno.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = shiftDropdownExpanded,
                    onExpandedChange = { shiftDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (selectedShiftName.isEmpty()) "Seleccionar turno" else selectedShiftName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = fieldShape,
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = shiftDropdownExpanded,
                        onDismissRequest = { shiftDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin turno asignado", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                selectedShiftId = ""
                                selectedShiftName = ""
                                shiftDropdownExpanded = false
                            }
                        )
                        shifts.forEach { shift ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(shift.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            "${shift.startTime} – ${shift.endTime}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedShiftId = shift.id
                                    selectedShiftName = shift.name
                                    shiftDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMsg = null
                        try {
                            val repo = viewModel?.functionsRepository
                                ?: throw Exception("No hay conexión con el servidor")
                            val uid = repo.createEmployee(
                                email = email, password = password,
                                firstName = firstName, lastName = lastName,
                                department = departamento, cargo = cargo,
                                companyId = companyId,
                                sedeId = selectedSedeId, shiftId = selectedShiftId
                            ).getOrThrow()

                            navController.navigate(Screen.AdminEmployeeQR.route(uid)) {
                                popUpTo(Screen.AdminRegisterEmployee.route) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Error al registrar empleado"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = isFormValid && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(text = "Registrar Empleado", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
