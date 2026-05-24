package com.biosecure.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biosecure.app.data.network.EmployeeRequest
import com.biosecure.app.data.repository.UserRepository
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterEmployeeScreen(navController: NavController, viewModel: BioSecureViewModel? = null) {

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var cargo by remember { mutableStateOf("") }
    var departamento by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var registeredId by remember { mutableStateOf<Int?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val repository = remember { UserRepository() }

    val snackbarHostState = remember { SnackbarHostState() }
    val lastCreatedUser by (viewModel?.lastCreatedUser
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val vmIsLoading by (viewModel?.isLoading
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val effectiveIsLoading = if (viewModel != null) vmIsLoading else isLoading

    LaunchedEffect(lastCreatedUser) {
        lastCreatedUser?.let { user ->
            registeredId = user.id
            snackbarHostState.showSnackbar("Empleado registrado con ID: ${user.id}")
            viewModel?.clearLastCreatedUser()
        }
    }

    val isFormValid = firstName.isNotBlank() && lastName.isNotBlank() &&
            email.isNotBlank() && cargo.isNotBlank() && departamento.isNotBlank()

    val fieldShape = RoundedCornerShape(12.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Teal,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = Teal,
        cursorColor = Teal
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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

            if (registeredId != null) {
                // ── Estado de éxito ──────────────────────────────────────
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(SuccessGreen.copy(alpha = 0.12f), CircleShape)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Text(
                    text = "¡Empleado Registrado!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "${firstName.trim()} ${lastName.trim()} ha sido agregado al sistema.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Card con ID generado
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ID del Empleado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "#${registeredId}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "El empleado usará este ID para iniciar sesión",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        // Datos registrados
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SummaryRow("Nombre", "${firstName.trim()} ${lastName.trim()}")
                            SummaryRow("Email", email.trim())
                            SummaryRow("Cargo", cargo.trim())
                            SummaryRow("Departamento", departamento.trim())
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(registeredId.toString()))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Copiar ID",
                                color = White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        firstName = ""; lastName = ""; email = ""
                        cargo = ""; departamento = ""
                        registeredId = null; errorMsg = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Registrar otro empleado",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

            } else {
                // ── Formulario ───────────────────────────────────────────
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

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        if (viewModel != null) {
                            viewModel.createEmployee(
                                firstName = firstName.trim(),
                                lastName = lastName.trim(),
                                email = email.trim(),
                                title = cargo.trim(),
                                department = departamento.trim()
                            )
                        } else {
                            scope.launch {
                                isLoading = true
                                errorMsg = null
                                val response = repository.createEmployee(
                                    EmployeeRequest(
                                        firstName = firstName.trim(),
                                        lastName = lastName.trim(),
                                        email = email.trim(),
                                        cargo = cargo.trim(),
                                        departamento = departamento.trim()
                                    )
                                )
                                if (response != null) {
                                    registeredId = response.id
                                } else {
                                    errorMsg = "Error al registrar. Verifica tu conexión."
                                }
                                isLoading = false
                            }
                        }
                    },
                    enabled = isFormValid && !effectiveIsLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                ) {
                    if (effectiveIsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Registrar Empleado",
                            color = White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
