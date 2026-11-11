package com.biosecure.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.biosecure.app.data.prefs.FaceEnrollmentStore
import com.biosecure.app.ui.components.navigateTab
import com.biosecure.app.ui.components.swipeToNavigate
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.screens.scan.BioSecureBottomBar
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SettingsScreen(
    navController: NavController,
    isAdmin: Boolean = false,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    language: String = "es",
    onLanguageChange: (String) -> Unit = {},
    viewModel: BioSecureViewModel? = null
) {
    val context = LocalContext.current
    val notifications by (viewModel?.notificationsEnabled ?: MutableStateFlow(true)).collectAsState()
    val currentUserName by (viewModel?.currentUserName ?: MutableStateFlow("")).collectAsState()
    val displayName = currentUserName.ifEmpty { if (isAdmin) "Admin" else "Empleado" }
    val currentUser by (viewModel?.currentUser ?: MutableStateFlow(null)).collectAsState()
    val currentUid by (viewModel?.currentUserUid ?: MutableStateFlow<String?>(null)).collectAsState()
    val uid = currentUid ?: remember { FirebaseAuth.getInstance().currentUser?.uid }
    val faceStore = remember { FaceEnrollmentStore(context) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    var isFaceEnrolled by remember { mutableStateOf(false) }
    LaunchedEffect(uid, lifecycleState) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            isFaceEnrolled = uid?.let { faceStore.isEnrolled(it) } == true
        }
    }
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameValue by remember { mutableStateOf("") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showHowItWorksDialog by remember { mutableStateOf(false) }

    val labelDarkMode = if (language == "en") "Dark Mode" else "Modo Oscuro"
    val labelNotifications = if (language == "en") "Push Notifications" else "Notificaciones"
    val labelLanguage = if (language == "en") "Language" else "Idioma"
    val labelSystemPrefs = if (language == "en") "SYSTEM PREFERENCES" else "CONFIGURACIÓN GENERAL"
    val labelBiometric = if (language == "en") "BIOMETRIC SECURITY" else "SEGURIDAD BIOMÉTRICA"
    val labelFingerprint = if (language == "en") "Fingerprint" else "Huella dactilar"
    val labelFacial = if (language == "en") "Facial scan" else "Escaneo facial"
    val labelSignOut = if (language == "en") "Sign Out" else "Cerrar Sesión"
    val labelRole = if (isAdmin)
        (if (language == "en") "System Administrator" else "Administrador del Sistema")
    else
        (if (language == "en") "Operations Supervisor" else "Supervisor de Operaciones")

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigate(
                onSwipeRight = { navController.navigateTab(Screen.history(isAdmin)) }
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = {
                BioSecureBottomBar(
                    navController = navController,
                    currentRoute = Screen.settings(isAdmin),
                    isAdmin = isAdmin
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val photo = currentUser?.image.orEmpty()
                    if (photo.isNotEmpty()) {
                        AsyncImage(
                            model = photo,
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(text = if (isAdmin) "👨‍💼" else "👤", fontSize = 40.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!isAdmin) {
                        IconButton(
                            onClick = {
                                editNameValue = currentUserName
                                showEditNameDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = if (language == "en") "Edit name" else "Editar nombre",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Text(
                    text = labelRole,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showEditNameDialog) {
                    AlertDialog(
                        onDismissRequest = { showEditNameDialog = false },
                        title = { Text(if (language == "en") "Edit Name" else "Editar Nombre") },
                        text = {
                            OutlinedTextField(
                                value = editNameValue,
                                onValueChange = { editNameValue = it },
                                label = { Text(if (language == "en") "Name" else "Nombre") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (editNameValue.isNotBlank()) {
                                    viewModel?.updateUserName(editNameValue.trim())
                                    showEditNameDialog = false
                                }
                            }) {
                                Text(if (language == "en") "Save" else "Guardar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEditNameDialog = false }) {
                                Text(if (language == "en") "Cancel" else "Cancelar")
                            }
                        }
                    )
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SuccessGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ACTIVE • Tier 1",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                SectionLabel(text = labelSystemPrefs)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        // Notifications toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🔔", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = labelNotifications,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = notifications,
                                onCheckedChange = { viewModel?.setNotificationsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        // Language toggle (both admin and employee)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🌐", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = labelLanguage,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("es" to "ES", "en" to "EN").forEach { (code, label) ->
                                    val selected = language == code
                                    OutlinedButton(
                                        onClick = { if (!selected) onLanguageChange(code) },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        // Dark mode toggle (both admin and employee)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🌙", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = labelDarkMode,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { onDarkModeChange(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        SettingsItem(
                            icon = "🆘",
                            title = if (language == "en") "Help & Support" else "Ayuda y Soporte",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:soporte@biosecure.app")
                                    putExtra(Intent.EXTRA_SUBJECT, "Soporte BioSecure")
                                }
                                context.startActivity(Intent.createChooser(intent, "Enviar correo"))
                            }
                        )
                        if (isAdmin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            SettingsItem(
                                icon = "🔐",
                                title = if (language == "en") "Privacy & Audit Logs" else "Privacidad y Registros",
                                onClick = { navController.navigate(Screen.AdminHistory.route) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionLabel(text = labelBiometric)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        SettingsItem(
                            icon = "🙂",
                            title = if (language == "en") "Face enrollment" else "Enrolamiento facial",
                            subtitle = if (isFaceEnrolled) {
                                if (language == "en") "Face enrolled ✓" else "Rostro enrolado ✓"
                            } else {
                                if (language == "en") "Not enrolled · tap to start" else "Sin enrolar · toca para empezar"
                            },
                            onClick = { navController.navigate(Screen.EmployeeEnroll.route) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        SettingsItem(
                            icon = "❓",
                            title = if (language == "en") "How does it work?" else "¿Cómo funciona?",
                            onClick = { showHowItWorksDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        SettingsItem(
                            icon = "🔒",
                            title = if (language == "en") "Privacy & data" else "Privacidad y datos",
                            onClick = { showPrivacyDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { showChangePasswordDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (language == "en") "Change password" else "Cambiar contraseña",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel?.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(text = "➡ $labelSignOut", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "BioSecure v${appVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showHowItWorksDialog) {
        AlertDialog(
            onDismissRequest = { showHowItWorksDialog = false },
            title = {
                Text(
                    text = if (language == "en") "How does BioSecure work?" else "¿Cómo funciona BioSecure?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (language == "en")
                            "1. Check in with your fingerprint or your face."
                        else
                            "1. Marca tu entrada con tu huella o tu rostro."
                    )
                    Text(
                        text = if (language == "en")
                            "2. Your administrator validates the record with your personal QR."
                        else
                            "2. Tu administrador valida el registro con tu QR personal."
                    )
                    Text(
                        text = if (language == "en")
                            "3. Review your shifts, history and attendance status at any time."
                        else
                            "3. Revisa tus turnos, historial y estado de asistencia cuando quieras."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHowItWorksDialog = false }) {
                    Text(if (language == "en") "Got it" else "Entendido")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(
                    text = if (language == "en") "Privacy & data" else "Privacidad y datos",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (language == "en")
                            "Location: we only use it at the moment you check in, to verify you are inside your branch."
                        else
                            "Ubicación: solo se usa en el momento del marcaje, para verificar que estás dentro de tu sede."
                    )
                    Text(
                        text = if (language == "en")
                            "Biometrics: your fingerprint is verified by the phone's secure system; your face samples stay on this device."
                        else
                            "Biometría: tu huella la verifica el sistema seguro del teléfono; las muestras de tu rostro quedan en este dispositivo."
                    )
                    Text(
                        text = if (language == "en")
                            "We never share your biometric data with third parties."
                        else
                            "Nunca compartimos tus datos biométricos con terceros."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(if (language == "en") "Got it" else "Entendido")
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var isChanging by remember { mutableStateOf(false) }
        var changeError by remember { mutableStateOf<String?>(null) }
        var changeSuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isChanging) showChangePasswordDialog = false },
            title = {
                Text(
                    text = if (language == "en") "Change password" else "Cambiar contraseña",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (changeSuccess) {
                        Text(
                            text = if (language == "en") "Password changed successfully." else "Contraseña cambiada correctamente.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it; changeError = null },
                            label = { Text(if (language == "en") "Current password" else "Contraseña actual") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it; changeError = null },
                            label = { Text(if (language == "en") "New password" else "Nueva contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; changeError = null },
                            label = { Text(if (language == "en") "Confirm new password" else "Confirmar nueva contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        changeError?.let { err ->
                            Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (!changeSuccess) {
                    TextButton(
                        onClick = {
                            if (newPassword != confirmPassword) {
                                changeError = if (language == "en") "Passwords do not match" else "Las contraseñas no coinciden"
                                return@TextButton
                            }
                            if (newPassword.length < 6) {
                                changeError = if (language == "en") "Password must be at least 6 characters" else "La contraseña debe tener al menos 6 caracteres"
                                return@TextButton
                            }
                            isChanging = true
                            viewModel?.changePassword(currentPassword, newPassword) { success, errorMsg ->
                                isChanging = false
                                if (success) {
                                    changeSuccess = true
                                } else {
                                    changeError = errorMsg ?: (if (language == "en") "Error changing password" else "Error al cambiar contraseña")
                                }
                            }
                        },
                        enabled = !isChanging && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank()
                    ) {
                        if (isChanging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (language == "en") "Change" else "Cambiar")
                        }
                    }
                } else {
                    TextButton(onClick = { showChangePasswordDialog = false }) {
                        Text(if (language == "en") "Close" else "Cerrar")
                    }
                }
            },
            dismissButton = {
                if (!changeSuccess) {
                    TextButton(onClick = { showChangePasswordDialog = false }, enabled = !isChanging) {
                        Text(if (language == "en") "Cancel" else "Cancelar")
                    }
                }
            }
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
fun BiometricItem(icon: String, title: String, subtitle: String, isConfigured: Boolean, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Text(text = if (isConfigured) "✅" else "❌", fontSize = 20.sp)
    }
}

@Composable
fun SettingsItem(icon: String, title: String, subtitle: String? = null, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsItemWithValue(icon: String, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
