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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.ui.components.navigateTab
import com.biosecure.app.ui.components.swipeToNavigate
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.screens.scan.BioSecureBottomBar
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
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
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameValue by remember { mutableStateOf("") }

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
                        .background(GreenDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isAdmin) "👨‍💼" else "👤", fontSize = 40.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
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
                                singleLine = true
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

                Spacer(modifier = Modifier.height(28.dp))

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
                        BiometricItem(
                            icon = "☝️",
                            title = labelFingerprint,
                            subtitle = if (isAdmin) "2 prints registered" else if (language == "en") "Tap to update" else "Toca para actualizar",
                            isConfigured = true,
                            onClick = if (!isAdmin) { { navController.navigate(Screen.EmployeeScan.route) } } else { {} }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        BiometricItem(
                            icon = "😊",
                            title = labelFacial,
                            subtitle = if (isAdmin) "Active • Precise Mode" else if (language == "en") "Tap to update" else "Toca para actualizar",
                            isConfigured = true,
                            onClick = if (!isAdmin) { { navController.navigate(Screen.EmployeeScan.route) } } else { {} }
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
                                    checkedThumbColor = White,
                                    checkedTrackColor = Teal
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

                        if (isAdmin) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🏢", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (language == "en") "Enterprise Mode" else "Modo Empresa",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = true,
                                    onCheckedChange = {},
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = White,
                                        checkedTrackColor = Teal
                                    )
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }

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
                                    checkedThumbColor = White,
                                    checkedTrackColor = Teal
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
                    colors = ButtonDefaults.buttonColors(containerColor = GreenDark)
                ) {
                    Text(text = "➡ $labelSignOut", color = White, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "BioSecure Enterprise v2.4.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Teal)
        }
        Text(text = if (isConfigured) "✅" else "❌", fontSize = 20.sp)
    }
}

@Composable
fun SettingsItem(icon: String, title: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
