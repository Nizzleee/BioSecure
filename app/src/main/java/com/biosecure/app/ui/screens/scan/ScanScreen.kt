package com.biosecure.app.ui.screens.scan

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.biosecure.app.ui.components.CameraPreview
import com.biosecure.app.ui.components.LottieIcon
import com.biosecure.app.ui.components.navigateTab
import com.biosecure.app.ui.components.swipeToNavigate
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.theme.LocalAppLanguage
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.biosecure.app.ui.viewmodel.ScanViewModel
import com.google.android.gms.location.LocationServices
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(navController: NavController, isAdmin: Boolean = false, viewModel: BioSecureViewModel? = null) {

    val lang = LocalAppLanguage.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = if (lang == "en") listOf("Fingerprint", "Facial Scan") else listOf("Huella Dactilar", "Escaneo Facial")
    val context = LocalContext.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    var faceDetected by remember { mutableStateOf(false) }
    val scanViewModel: ScanViewModel = viewModel()
    val currentTime by scanViewModel.currentTime.collectAsState()

    var scanStatus by remember { mutableStateOf("") }
    var scanSuccess by remember { mutableStateOf(false) }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingLat by remember { mutableStateOf(0.0) }
    var pendingLng by remember { mutableStateOf(0.0) }

    val currentUserUid by (viewModel?.currentUserUid
        ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)).collectAsState()
    val currentUserName by (viewModel?.currentUserName
        ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()
    val isLoading by (viewModel?.isLoading
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun registerWithLocation(onResult: (Boolean) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val lat = location?.latitude ?: 0.0
            val lng = location?.longitude ?: 0.0
            pendingLat = lat
            pendingLng = lng
            showConfirmDialog = true
        }.addOnFailureListener {
            pendingLat = 0.0
            pendingLng = 0.0
            showConfirmDialog = true
        }
    }

    val biometricPrompt = remember {
        BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    scanSuccess = true
                    scanStatus = "¡Biométrica verificada!"
                    registerWithLocation {}
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    scanStatus = "Error: $errString"
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    scanStatus = "No reconocido"
                }
            }
        )
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("BioSecure - Verificación Biométrica")
        .setSubtitle("Use su huella dactilar para registrar asistencia")
        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Cancelar")
        .build()

    LaunchedEffect(selectedTab) {
        scanStatus = ""
        scanSuccess = false
        if (selectedTab == 1 && !hasCameraPermission) {
            cameraLauncher.launch(android.Manifest.permission.CAMERA)
        }
        if (selectedTab != 1) faceDetected = false
    }

    // Confirmation AlertDialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                scanSuccess = false
                scanStatus = ""
            },
            title = {
                Text(
                    text = "Confirmar asistencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿Deseas registrar tu asistencia ahora?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (currentUserName.isNotEmpty()) {
                        Text(
                            text = "Empleado: $currentUserName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (pendingLat != 0.0 || pendingLng != 0.0) {
                        Text(
                            text = "Ubicación: ${String.format("%.4f", pendingLat)}, ${String.format("%.4f", pendingLng)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        val uid = currentUserUid ?: ""
                        viewModel?.registerAttendance(uid, pendingLat, pendingLng) { success, _ ->
                            if (success) {
                                navController.navigate(Screen.Confirmation.route)
                            } else {
                                scanSuccess = false
                                scanStatus = "Error al registrar asistencia"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirmar", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showConfirmDialog = false
                        scanSuccess = false
                        scanStatus = ""
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigate(
                onSwipeLeft = {
                    navController.navigateTab(Screen.history(isAdmin))
                },
                onSwipeRight = {
                    if (!isAdmin) navController.navigateTab(Screen.EmployeeHome.route)
                    else navController.navigateTab(Screen.Dashboard.route(null))
                }
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (isAdmin) "ADMIN PORTAL" else if (lang == "en") "Check-in" else "Registro",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                BioSecureBottomBar(
                    navController = navController,
                    currentRoute = Screen.scan(isAdmin),
                    isAdmin = isAdmin
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isAdmin) "ADMIN PORTAL" else if (lang == "en") "Attendance Check-in" else "Registro de Asistencia",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                if (!isAdmin && currentUserName.isNotEmpty()) {
                    Text(
                        text = "${if (lang == "en") "Welcome" else "Bienvenido"}, $currentUserName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Button(
                                onClick = { selectedTab = index },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val borderColor = when {
                    selectedTab == 1 && faceDetected -> Color(0xFF4CAF50)
                    selectedTab == 1 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                }
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .border(3.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedTab == 1) {
                            if (hasCameraPermission) {
                                CameraPreview(onFaceDetected = { detected -> faceDetected = detected })
                            } else {
                                LottieIcon(assetName = "face_scan.json", modifier = Modifier.size(160.dp))
                            }
                        } else {
                            when {
                                isLoading -> CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                scanSuccess -> LottieIcon(
                                    assetName = "success_check.json",
                                    modifier = Modifier.size(120.dp),
                                    iterations = 1
                                )
                                scanStatus.isNotEmpty() -> Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                else -> LottieIcon(
                                    assetName = "fingerprint_scan.json",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = when {
                        selectedTab == 1 && faceDetected -> if (lang == "en") "Face detected ✅" else "Rostro detectado ✅"
                        selectedTab == 1 -> if (lang == "en") "Place your face in front of the camera" else "Coloque su rostro frente a la cámara"
                        scanStatus.isNotEmpty() -> scanStatus
                        else -> if (lang == "en") "Authentication required" else "Reconocimiento requerido"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        selectedTab == 1 && faceDetected -> Color(0xFF4CAF50)
                        selectedTab == 1 -> MaterialTheme.colorScheme.error
                        scanStatus.contains("Error") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onBackground
                    }
                )
                Text(
                    text = if (selectedTab == 0)
                        if (lang == "en") "Place your finger on the reader to check in" else "Coloque su dedo sobre el lector para marcar"
                    else
                        if (lang == "en") "Keep your face visible in front of the camera" else "Mantenga su rostro visible frente a la cámara",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            if (faceDetected) {
                                registerWithLocation {}
                            }
                        }
                    },
                    enabled = (selectedTab == 0 || faceDetected) && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .animateContentSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == "en") "REGISTER CHECK-IN" else "REGISTRAR ENTRADA",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BioSecureBottomBar(
    navController: NavController,
    currentRoute: String,
    isAdmin: Boolean = false
) {
    val lang = LocalAppLanguage.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val bottomBarItems: List<Triple<String, String, String>> = if (isAdmin) {
            listOf(
                Triple(Screen.Dashboard.route("null"), if (lang == "en") "PANEL" else "PANEL", "📊"),
                Triple(Screen.AdminSedes.route, if (lang == "en") "BRANCHES" else "SEDES", "🏢"),
                Triple(Screen.history(true), if (lang == "en") "HISTORY" else "HISTORIAL", "🕐"),
                Triple(Screen.settings(true), if (lang == "en") "SETTINGS" else "AJUSTES", "⚙️")
            )
        } else {
            listOf(
                Triple(Screen.EmployeeHome.route, if (lang == "en") "HOME" else "INICIO", "🏠"),
                Triple(Screen.EmployeeScan.route, if (lang == "en") "SCAN" else "ESCANEO", "📷"),
                Triple(Screen.history(false), if (lang == "en") "HISTORY" else "HISTORIAL", "🕐"),
                Triple(Screen.settings(false), if (lang == "en") "SETTINGS" else "AJUSTES", "⚙️")
            )
        }

        bottomBarItems.forEach { (route, label, icon) ->
            val isSelected = currentRoute == route ||
                (route.startsWith("admin/dashboard/") && currentRoute.startsWith("admin/dashboard/"))
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(route) {
                            popUpTo(Screen.Login.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Text(
                        text = icon,
                        fontSize = 20.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            )
        }
    }
}
