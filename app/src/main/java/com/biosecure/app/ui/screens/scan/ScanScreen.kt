package com.biosecure.app.ui.screens.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.biosecure.app.ui.components.CameraPreview
import com.biosecure.app.data.model.ScanType
import com.biosecure.app.ui.navigation.Screen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.biosecure.app.ui.theme.*
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.biosecure.app.ui.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(navController: NavController, isAdmin: Boolean = false, viewModel: BioSecureViewModel? = null, scanViewModel: ScanViewModel? = null) {

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Huella Dactilar", "Escaneo Facial")
    val context = LocalContext.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && !hasCameraPermission) {
            launcher.launch(android.Manifest.permission.CAMERA)
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    val currentTime by (scanViewModel?.currentTime ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()

    var scanStatus by remember { mutableStateOf("") }

    val biometricPrompt = remember {
        BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    scanStatus = "¡Éxito!"
                    viewModel?.registerAttendance(
                        if (selectedTab == 0) ScanType.HUELLA else ScanType.FACIAL
                    )
                    navController.navigate(Screen.Confirmation.route)
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
        .setTitle("BioSecure - Registro")
        .setSubtitle(if (selectedTab == 0) "Use su huella dactilar" else "Use reconocimiento facial")
        .setNegativeButtonText("Cancelar")
        .build()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (isAdmin) "ADMIN PORTAL" else "Registro",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                    text = if (isAdmin) "ADMIN PORTAL" else "Registro de Asistencia",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = currentTime.ifEmpty { "08:45 AM" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Tabs
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
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
                                    containerColor = if (isSelected) Color(0xFF0D3B35)
                                                    else if (isDarkTheme) Color(0xFF2C2C2C)
                                                    else Color(0xFFF0F0F0),
                                    contentColor = if (isSelected) Color.White else Color(0xFF00B4A6)
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

                // Área de escaneo
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .border(3.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(16.dp))
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
                                CameraPreview()
                            } else {
                                Text(text = "Falta permiso de cámara", textAlign = TextAlign.Center, fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = if (scanStatus.isNotEmpty()) "✅" else "☝️",
                                fontSize = 72.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (scanStatus.isNotEmpty()) scanStatus else "Reconocimiento requerido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (scanStatus.contains("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (selectedTab == 0)
                        "Coloque su dedo sobre el lector para marcar"
                    else
                        "Mire a la cámara para registrar su asistencia",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Botón registrar
                Button(
                    onClick = { 
                        biometricPrompt.authenticate(promptInfo)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "REGISTRAR ENTRADA",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
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
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val bottomBarItems = mutableListOf(
            Triple(Screen.scan(isAdmin), "SCAN", "📷"),
            Triple(Screen.history(isAdmin), "HISTORY", "🕐"),
            Triple(Screen.settings(isAdmin), "SETTINGS", "⚙️")
        )
        
        if (isAdmin) {
            bottomBarItems.add(0, Triple(Screen.Dashboard.route, "DASHBOARD", "📊"))
        }

        bottomBarItems.forEach { (route, label, icon) ->
            val isSelected = currentRoute == route
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
                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ) 
                },
                label = { 
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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