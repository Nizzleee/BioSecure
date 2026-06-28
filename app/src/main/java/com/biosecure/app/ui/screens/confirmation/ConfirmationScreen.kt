package com.biosecure.app.ui.screens.confirmation

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biosecure.app.data.model.ConfirmationData
import com.biosecure.app.ui.components.LottieIcon
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.theme.LocalAppLanguage
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

@Composable
fun ConfirmationScreen(
    navController: NavController,
    viewModel: BioSecureViewModel? = null
) {
    val defaultRoleFlow = remember { MutableStateFlow("employee") }
    val currentRole by (viewModel?.currentRole ?: defaultRoleFlow).collectAsState()
    val lastConfirmation by (viewModel?.lastConfirmation
        ?: MutableStateFlow<ConfirmationData?>(null)).collectAsState()
    val vmUid by (viewModel?.currentUserUid
        ?: MutableStateFlow<String?>(null)).collectAsState()
    val currentUserUid = vmUid ?: remember { FirebaseAuth.getInstance().currentUser?.uid }
    val currentUserName by (viewModel?.currentUserName
        ?: MutableStateFlow("")).collectAsState()

    val isAdmin = currentRole == "admin"

    val qrBitmap by produceState<Bitmap?>(initialValue = null, currentUserUid) {
        val uid = currentUserUid ?: return@produceState
        if (uid.isBlank()) return@produceState
        try {
            val bmp = withContext(Dispatchers.Default) {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(uid, BarcodeFormat.QR_CODE, 512, 512)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
                for (x in 0 until 512) {
                    for (y in 0 until 512) {
                        bitmap.setPixel(
                            x, y,
                            if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                        )
                    }
                }
                bitmap
            }
            value = bmp
        } catch (e: Exception) {
            value = null
        }
    }

    val checkScale = remember { Animatable(0f) }
    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            checkScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isAdmin) {
            AdminConfirmedContent(
                navController = navController,
                checkScale = checkScale.value,
                lastConfirmation = lastConfirmation
            )
        } else {
            EmployeeQRWaitingContent(
                navController = navController,
                currentUserName = currentUserName,
                qrBitmap = qrBitmap,
                lastConfirmation = lastConfirmation
            )
        }
    }
}

@Composable
private fun AdminConfirmedContent(
    navController: NavController,
    checkScale: Float,
    lastConfirmation: ConfirmationData?
) {
    val userName = lastConfirmation?.userName ?: "Empleado"
    val checkIn = lastConfirmation?.checkIn ?: "--:--"
    val date = lastConfirmation?.date ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieIcon(
            assetName = "success_check.json",
            modifier = Modifier.size(120.dp).scale(checkScale),
            iterations = 1
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Asistencia registrada exitosamente",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SuccessGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                ConfirmationRow(label = "Empleado", value = userName)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                ConfirmationRow(label = "Hora de ingreso", value = checkIn)
                if (date.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    ConfirmationRow(label = "Fecha", value = date)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                navController.navigate(Screen.Dashboard.route(null)) {
                    popUpTo(Screen.Confirmation.route) { inclusive = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(text = "VOLVER AL PANEL", color = White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmployeeQRWaitingContent(
    navController: NavController,
    currentUserName: String,
    qrBitmap: Bitmap?,
    lastConfirmation: ConfirmationData? = null
) {
    val lang = LocalAppLanguage.current
    val isFallido = lastConfirmation?.status == "FALLIDO"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentUserName.isNotEmpty()) {
            Text(
                text = "${if (lang == "en") "Hello" else "Hola"}, $currentUserName",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isFallido) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(ErrorRed.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (lang == "en") "Outside Working Hours" else "Fuera de Horario",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ErrorRed,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (lang == "en")
                    "Your attendance was recorded but you are outside your scheduled working hours."
                else
                    "Tu asistencia fue registrada, pero estás fuera de tu horario de trabajo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (lastConfirmation != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (lang == "en") "Date" else "Fecha",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastConfirmation.date,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (lang == "en") "Recorded at" else "Registrado a las",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastConfirmation.checkIn,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ErrorRed
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = if (lang == "en") "Show this QR to your admin to confirm" else "Muestra este QR al admin para confirmar",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            if (lastConfirmation != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (lang == "en") "Date" else "Fecha",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastConfirmation.date,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (lang == "en") "Check-in" else "Hora",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastConfirmation.checkIn,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            QRCardContent(qrBitmap = qrBitmap, userName = currentUserName)
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = {
                navController.navigate(Screen.EmployeeHome.route) {
                    popUpTo(Screen.Confirmation.route) { inclusive = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                text = if (lang == "en") "Back to Home" else "Volver al Inicio",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QRCardContent(qrBitmap: Bitmap?, userName: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR de $userName",
                modifier = Modifier
                    .size(240.dp)
                    .padding(12.dp)
            )
        } else {
            Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ConfirmationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
