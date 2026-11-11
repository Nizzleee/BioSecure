package com.biosecure.app.ui.screens.scan

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
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
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavController
import com.biosecure.app.data.location.BestLocationProvider
import com.biosecure.app.data.security.BiometricCrypto
import kotlin.math.roundToInt
import java.util.Locale
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
    val userRole by (viewModel?.currentRole ?: remember { kotlinx.coroutines.flow.MutableStateFlow("employee") }).collectAsState()

    // Si el rol es admin, no puede automarcarse asistencia biométrica -> redirige al Dashboard
    LaunchedEffect(userRole, isAdmin) {
        if (isAdmin || userRole == "admin") {
            navController.navigate(Screen.Dashboard.route("null")) {
                popUpTo(Screen.Dashboard.route("null")) { inclusive = true }
            }
        }
    }

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
    var faceError by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        faceError = if (isGranted) {
            null
        } else if (lang == "en") {
            "Camera permission denied. Enable it in Settings to use facial check-in."
        } else {
            "Permiso de cámara denegado. Actívalo en Ajustes para usar el marcaje facial."
        }
    }

    var faceDetected by remember { mutableStateOf(false) }
    var faceHint by remember { mutableStateOf("") }
    val scanViewModel: ScanViewModel = viewModel()
    val currentTime by scanViewModel.currentTime.collectAsState()

    var scanStatus by remember { mutableStateOf("") }
    var scanSuccess by remember { mutableStateOf(false) }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    var isVerifyingFace by remember { mutableStateOf(false) }
    var faceCaptureRequest by remember { mutableStateOf(0) }
    var faceVerified by remember { mutableStateOf(false) }
    var locationIssue by remember { mutableStateOf<LocationIssue?>(null) }

    val currentUserName by (viewModel?.currentUserName
        ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()
    val currentUid by (viewModel?.currentUserUid
        ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)).collectAsState()
    val faceStore = remember { com.biosecure.app.data.prefs.FaceEnrollmentStore(context) }
    // Se recalcula al volver a la pantalla (p. ej. después de enrolarse) porque
    // antes quedaba en false hasta reiniciar la app y bloqueaba el marcaje facial.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val remoteFaceEnrolled by (viewModel?.faceEnrolled
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    var isFaceEnrolled by remember { mutableStateOf(false) }
    LaunchedEffect(currentUid, lifecycleState, remoteFaceEnrolled) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            // La plantilla puede existir en Firestore aunque este teléfono
            // no tenga la caché local (p. ej. enrolado en otro equipo o tras
            // reinstalar la app). Si la consulta remota ya dijo que existe,
            // no debemos volver a false al reanudar la pantalla.
            isFaceEnrolled = remoteFaceEnrolled ||
                currentUid?.let { faceStore.isEnrolled(it) } == true
            currentUid?.let { viewModel?.refreshFaceEnrollment(it) }
        }
    }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val bestLocationProvider = remember { BestLocationProvider(fusedLocationClient) }
    val locationManager = remember {
        context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
    }
    // El prompt biométrico se crea una sola vez; este estado evita que los
    // mensajes queden en el idioma de la primera composición.
    val currentLang by rememberUpdatedState(lang)
    var cryptoUsed by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }

    fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun hasCoarseLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    /** Devuelve qué falta para poder obtener una ubicación precisa. */
    fun locationGate(): LocationIssue? {
        if (!hasFineLocation()) {
            return if (hasCoarseLocation()) {
                // En Android 12+ el usuario pudo elegir "aproximada": sin
                // precisión exacta el GPS da errores de kilómetros.
                LocationIssue.APPROXIMATE_PERMISSION
            } else {
                LocationIssue.MISSING_PERMISSION
            }
        }
        val manager = locationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            return LocationIssue.LOCATION_DISABLED
        }
        // Modo "solo dispositivo/economía" apaga el GPS y deja solo red.
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationIssue.GPS_DISABLED
        }
        return null
    }

    fun openLocationSettings() {
        val intent = if (locationIssue == LocationIssue.APPROXIMATE_PERMISSION ||
            locationIssue == LocationIssue.MISSING_PERMISSION
        ) {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        } else {
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
        runCatching { context.startActivity(intent) }
    }

    fun beginLocationSearch() {
        if (isLocating) return
        isLocating = true
        scope.launch {
            scanSuccess = false
            scanStatus = if (currentLang == "en") {
                "Getting a precise location…"
            } else {
                "Obteniendo una ubicación precisa…"
            }
            try {
                val location = bestLocationProvider.awaitBest(
                    minAccuracyMeters = BioSecureViewModel.MAX_ACCURACY_METERS,
                    // 20 s: un fix real de GPS puede tardar en frío. Antes se
                    // cortaba a los 10 s y quedaba la precisión de la red.
                    timeoutMs = 20_000L,
                    onProgress = { _, accuracy ->
                        val meters = accuracy.roundToInt()
                        scanStatus = if (currentLang == "en") {
                            "Getting a precise location… (±$meters m)"
                        } else {
                            "Obteniendo una ubicación precisa… (±$meters m)"
                        }
                    }
                )
                when {
                    location == null -> {
                        faceVerified = false
                        scanStatus = attendanceErrorText("GPS_UNAVAILABLE", currentLang)
                    }
                    location.accuracy > BioSecureViewModel.MAX_ACCURACY_METERS -> {
                        faceVerified = false
                        val meters = location.accuracy.roundToInt()
                        scanStatus = if (currentLang == "en") {
                            "GPS signal is still weak (±$meters m). Check-in requires ±50 m or better. Step outside or closer to a window and try again."
                        } else {
                            "La señal GPS sigue siendo débil (±$meters m). El marcaje exige ±50 m o mejor. Sal al exterior o acércate a una ventana e inténtalo de nuevo."
                        }
                    }
                    else -> {
                        pendingLocation = location
                        confirmError = null
                        scanStatus = ""
                        showConfirmDialog = true
                    }
                }
            } finally {
                isLocating = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasFineLocation()
        when {
            fineGranted -> {
                locationIssue = null
                val manager = locationManager
                if (manager == null || LocationManagerCompat.isLocationEnabled(manager)) {
                    beginLocationSearch()
                } else {
                    locationIssue = LocationIssue.LOCATION_DISABLED
                }
            }
            hasCoarseLocation() -> locationIssue = LocationIssue.APPROXIMATE_PERMISSION
            else -> locationIssue = LocationIssue.MISSING_PERMISSION
        }
    }

    fun registerWithLocation() {
        if (isLocating) return
        when (locationGate()) {
            null -> beginLocationSearch()
            LocationIssue.MISSING_PERMISSION -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            // Pedir solo FINE abre el diálogo de "cambiar a ubicación precisa".
            LocationIssue.APPROXIMATE_PERMISSION -> locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            )
            LocationIssue.LOCATION_DISABLED,
            LocationIssue.GPS_DISABLED -> locationIssue = locationGate()
        }
    }

    // Al volver de Ajustes se refrescan permisos y estado de la ubicación.
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (locationIssue != null && locationGate() == null) {
                locationIssue = null
            }
        }
    }

    val biometricPrompt = remember {
        BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (cryptoUsed && result.cryptoObject?.cipher == null) {
                        scanSuccess = false
                        scanStatus = if (currentLang == "en") {
                            "Biometric verification could not be validated"
                        } else {
                            "No se pudo validar la verificación biométrica"
                        }
                        return
                    }
                    scanSuccess = true
                    scanStatus = if (currentLang == "en") "Identity verified" else "Identidad verificada"
                    registerWithLocation()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    scanSuccess = false
                    scanStatus = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED ->
                            if (currentLang == "en") "Verification cancelled"
                            else "Verificación cancelada"

                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            if (currentLang == "en")
                                "No fingerprint or face registered on this phone"
                            else
                                "No tienes huella ni rostro registrado en el teléfono"

                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            if (currentLang == "en")
                                "Too many attempts, try again later"
                            else
                                "Demasiados intentos, espera un momento"

                        else ->
                            if (currentLang == "en")
                                "Could not verify your identity"
                            else
                                "No se pudo verificar tu identidad"
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    scanStatus = if (currentLang == "en") "Not recognized, try again" else "No reconocido, intenta de nuevo"
                }
            }
        )
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("BioSecure - Verificación Biométrica")
        .setSubtitle("Use huella dactilar o rostro para registrar asistencia")
        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Cancelar")
        .build()

    // Autentica con una clave de Keystore ligada a biometría cuando el equipo
    // lo soporta; si no, usa el prompt normal.
    fun authenticateWithBiometrics() {
        val cipher = BiometricCrypto.newCipherOrNull()
        if (cipher != null) {
            cryptoUsed = true
            runCatching {
                biometricPrompt.authenticate(promptInfo, BiometricCrypto.wrap(cipher))
            }.onFailure {
                // Algunos equipos no aceptan CryptoObject: se reintenta sin él.
                cryptoUsed = false
                biometricPrompt.authenticate(promptInfo)
            }
        } else {
            cryptoUsed = false
            biometricPrompt.authenticate(promptInfo)
        }
    }

    LaunchedEffect(selectedTab) {
        scanStatus = ""
        scanSuccess = false
        faceError = null
        if (selectedTab == 1 && !hasCameraPermission) {
            cameraLauncher.launch(android.Manifest.permission.CAMERA)
        }
        if (selectedTab != 1) faceDetected = false
        if (selectedTab != 1) faceHint = ""
        isVerifyingFace = false
        faceVerified = false
        faceCaptureRequest = 0
    }

    // Confirmation AlertDialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRegistering) {
                    showConfirmDialog = false
                    pendingLocation = null
                    confirmError = null
                    scanSuccess = false
                    scanStatus = ""
                }
            },
            title = {
                Text(
                    text = if (lang == "en") "Confirm check-in" else "Confirmar asistencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "en") {
                            "Do you want to register your attendance now?"
                        } else {
                            "¿Deseas registrar tu asistencia ahora?"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (currentUserName.isNotEmpty()) {
                        Text(
                            text = "${if (lang == "en") "Employee" else "Empleado"}: $currentUserName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    pendingLocation?.let { loc ->
                        Text(
                            text = "${if (lang == "en") "Location" else "Ubicación"}: " +
                                "${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)} " +
                                "(±${loc.accuracy.roundToInt()} m)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    confirmError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isRegistering) return@Button
                        val loc = pendingLocation
                        if (loc == null) {
                            showConfirmDialog = false
                            scanStatus = attendanceErrorText("GPS_UNAVAILABLE", lang)
                            scanSuccess = false
                            return@Button
                        }
                        isRegistering = true
                        val tipo = if (selectedTab == 1) "FACIAL" else "HUELLA"
                        viewModel?.registerAttendanceBiometric(
                            tipo = tipo,
                            location = loc
                        ) { success, errorMsg ->
                            isRegistering = false
                            if (success) {
                                showConfirmDialog = false
                                pendingLocation = null
                                confirmError = null
                                navController.navigate(Screen.Confirmation.route)
                            } else {
                                scanSuccess = false
                                faceVerified = false
                                val baseCode = errorMsg?.substringBefore(':')
                                if (baseCode == "FACE_NOT_ENROLLED" || baseCode == "FACE_MISMATCH" ||
                                    baseCode == "FACE_NOT_VERIFIED"
                                ) {
                                    // La plantilla pendiente ya no sirve: hay que
                                    // repetir la captura (o enrolarse primero).
                                    showConfirmDialog = false
                                    pendingLocation = null
                                    confirmError = null
                                    if (baseCode == "FACE_NOT_ENROLLED") {
                                        isFaceEnrolled = false
                                        navController.navigate(Screen.EmployeeEnroll.route)
                                    } else {
                                        scanStatus = attendanceErrorText(errorMsg, lang)
                                    }
                                } else {
                                    confirmError = attendanceErrorText(errorMsg, lang)
                                    scanStatus = confirmError ?: ""
                                }
                            }
                        } ?: run { isRegistering = false }
                    },
                    enabled = !isRegistering,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = when {
                                lang == "en" && confirmError != null -> "Retry"
                                confirmError != null -> "Reintentar"
                                lang == "en" -> "Confirm"
                                else -> "Confirmar"
                            },
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        if (isRegistering) return@OutlinedButton
                        showConfirmDialog = false
                        pendingLocation = null
                        confirmError = null
                        scanSuccess = false
                        scanStatus = ""
                    },
                    enabled = !isRegistering
                ) {
                    Text(if (lang == "en") "Cancel" else "Cancelar")
                }
            }
        )
    }

    locationIssue?.let { issue ->
        val (title, body) = when (issue) {
            LocationIssue.APPROXIMATE_PERMISSION -> if (lang == "en") {
                "Allow precise location" to
                    "Check-in needs your exact location (±50 m). Open Settings → Permissions → Location and choose “Precise”."
            } else {
                "Activa la ubicación precisa" to
                    "Para marcar necesitamos tu ubicación exacta (±50 m). Ve a Ajustes → Permisos → Ubicación y elige «Precisión exacta»."
            }
            LocationIssue.MISSING_PERMISSION -> if (lang == "en") {
                "Location permission required" to
                    "Allow location access for BioSecure in Settings to register attendance."
            } else {
                "Permiso de ubicación requerido" to
                    "Concede el permiso de ubicación a BioSecure en Ajustes para registrar tu asistencia."
            }
            LocationIssue.LOCATION_DISABLED -> if (lang == "en") {
                "Turn on location" to
                    "Location is off on this phone. Turn it on to register attendance."
            } else {
                "Activa la ubicación" to
                    "La ubicación del teléfono está apagada. Actívala para registrar tu asistencia."
            }
            LocationIssue.GPS_DISABLED -> if (lang == "en") {
                "Turn on precise GPS" to
                    "The GPS is disabled or in battery-saving mode. Enable it to get an accurate location."
            } else {
                "Activa el GPS de precisión" to
                    "El GPS está desactivado o en modo ahorro. Actívalo para obtener una ubicación precisa."
            }
        }
        AlertDialog(
            onDismissRequest = { locationIssue = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(body) },
            confirmButton = {
                Button(onClick = { openLocationSettings() }) {
                    Text(if (lang == "en") "Open settings" else "Abrir ajustes")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationIssue = null }) {
                    Text(if (lang == "en") "Cancel" else "Cancelar")
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
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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

                if (selectedTab == 1 && !isFaceEnrolled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (lang == "en") "Face not enrolled yet" else "Aún no enrolaste tu rostro",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "en") {
                                    "Capture 3 reference photos to check in with facial recognition. You can still use your fingerprint."
                                } else {
                                    "Captura 3 fotos de referencia para marcar con reconocimiento facial. Mientras tanto puedes usar tu huella."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = { navController.navigate(Screen.EmployeeEnroll.route) }
                            ) {
                                Text(
                                    text = if (lang == "en") "Enroll now" else "Enrolar ahora",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val borderColor = when {
                    selectedTab == 1 && faceDetected -> MaterialTheme.colorScheme.secondary
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
                                com.biosecure.app.ui.components.FaceCaptureCamera(
                                    modifier = Modifier.fillMaxSize(),
                                    captureRequest = faceCaptureRequest,
                                    onCaptured = { bitmap ->
                                        val targetUid = currentUid
                                        if (targetUid == null) {
                                            isVerifyingFace = false
                                            scanStatus = attendanceErrorText("UNAUTHENTICATED", lang)
                                        } else {
                                            faceVerified = false
                                            viewModel?.verifyFace(targetUid, bitmap) { ok, error, score ->
                                                isVerifyingFace = false
                                                faceVerified = ok
                                                if (ok) {
                                                    scanSuccess = false
                                                    val scoreText = score?.let {
                                                        String.format(Locale.US, "%.3f", it)
                                                    }
                                                    scanStatus = if (lang == "en") {
                                                        "Face verified" +
                                                            (scoreText?.let { " (similarity $it)" } ?: "") +
                                                            ". Getting your location…"
                                                    } else {
                                                        "Rostro verificado" +
                                                            (scoreText?.let { " (similitud $it)" } ?: "") +
                                                            ". Obteniendo tu ubicación…"
                                                    }
                                                    registerWithLocation()
                                                } else {
                                                    scanSuccess = false
                                                    scanStatus = attendanceErrorText(error, lang)
                                                    if (error?.substringBefore(':') == "FACE_NOT_ENROLLED") {
                                                        isFaceEnrolled = false
                                                    }
                                                }
                                            } ?: run { isVerifyingFace = false }
                                        }
                                    },
                                    onQuality = { quality ->
                                        // Si llegan análisis, la cámara sí está
                                        // funcionando: limpiamos errores previos.
                                        faceError = null
                                        faceDetected = quality.faceCount > 0
                                        faceHint = when (quality.level) {
                                            com.biosecure.app.ui.components.QualityLevel.NO_FACE ->
                                                if (lang == "en") "No face detected" else "No se detecta un rostro"
                                            com.biosecure.app.ui.components.QualityLevel.MULTIPLE_FACES ->
                                                if (lang == "en") "Only one person at a time" else "Solo una persona a la vez"
                                            com.biosecure.app.ui.components.QualityLevel.TOO_FAR ->
                                                if (lang == "en") "Move closer to the camera" else "Acércate un poco más"
                                            com.biosecure.app.ui.components.QualityLevel.OFF_CENTER ->
                                                if (lang == "en") "Center your face" else "Centra tu rostro"
                                            com.biosecure.app.ui.components.QualityLevel.EYES_CLOSED ->
                                                if (lang == "en") "Keep your eyes open" else "Mantén los ojos abiertos"
                                            com.biosecure.app.ui.components.QualityLevel.TILTED ->
                                                if (lang == "en") "Keep your head straight" else "Mantén la cabeza derecha"
                                            com.biosecure.app.ui.components.QualityLevel.GOOD -> ""
                                        }
                                    },
                                    // Antes los fallos de cámara se ignoraban y la
                                    // pantalla quedaba en negro sin explicación.
                                    onError = { error ->
                                        faceError = error
                                        // Si falló la captura (p. ej. no se
                                        // pudieron alinear ojos/nariz), hay que
                                        // liberar el botón para reintentar.
                                        isVerifyingFace = false
                                    }
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = "📷", fontSize = 40.sp)
                                    TextButton(onClick = {
                                        cameraLauncher.launch(Manifest.permission.CAMERA)
                                    }) {
                                        Text(if (lang == "en") "Allow camera" else "Permitir cámara")
                                    }
                                }
                            }
                        } else {
                            when {
                                isRegistering || isLocating -> CircularProgressIndicator(
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
                        selectedTab == 1 && !isFaceEnrolled ->
                            if (lang == "en") "Enroll your face to continue" else "Enrola tu rostro para continuar"
                        selectedTab == 1 && !hasCameraPermission ->
                            if (lang == "en") "Allow camera access to scan your face" else "Concede permiso de cámara para escanear tu rostro"
                        selectedTab == 1 && faceError != null -> faceError!!
                        selectedTab == 1 && scanStatus.isNotEmpty() -> scanStatus
                        selectedTab == 1 && faceDetected -> if (lang == "en") "Face detected ✅" else "Rostro detectado ✅"
                        selectedTab == 1 && faceHint.isNotEmpty() -> faceHint
                        selectedTab == 1 -> if (lang == "en") "Place your face in front of the camera" else "Coloca tu rostro frente a la cámara"
                        scanStatus.isNotEmpty() -> scanStatus
                        else -> if (lang == "en") "Authentication required" else "Reconocimiento requerido"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        selectedTab == 1 && !isFaceEnrolled -> MaterialTheme.colorScheme.error
                        selectedTab == 1 && (faceError != null || !hasCameraPermission) -> MaterialTheme.colorScheme.error
                        selectedTab == 1 && scanStatus.isNotEmpty() && !faceVerified -> MaterialTheme.colorScheme.error
                        selectedTab == 1 && faceVerified -> MaterialTheme.colorScheme.secondary
                        selectedTab == 1 && faceDetected -> MaterialTheme.colorScheme.secondary
                        selectedTab == 1 -> MaterialTheme.colorScheme.error
                        isLocating || isRegistering -> MaterialTheme.colorScheme.onBackground
                        scanStatus.isNotEmpty() -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                    textAlign = TextAlign.Center
                )
                // La segunda línea solo se muestra cuando no repite el mensaje
                // principal (antes salían dos frases casi idénticas en facial).
                val showHelperLine = selectedTab == 0 ||
                    (faceError == null && !faceDetected && faceHint.isEmpty() && hasCameraPermission)
                if (showHelperLine) {
                    Text(
                        text = if (selectedTab == 0)
                            if (lang == "en") "Place your finger on the reader to check in" else "Coloque su dedo sobre el lector para marcar"
                        else
                            if (lang == "en") "Keep your face inside the frame with good lighting" else "Mantén tu rostro dentro del recuadro y con buena luz",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        when {
                            selectedTab == 1 && !hasCameraPermission -> {
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            }
                            selectedTab == 0 -> authenticateWithBiometrics()
                            !isFaceEnrolled ->
                                // Sin enrolamiento no se puede marcar por rostro.
                                navController.navigate(Screen.EmployeeEnroll.route)
                            faceError != null -> Unit
                            isVerifyingFace -> Unit
                            !faceDetected || faceHint.isNotEmpty() -> {
                                // Solo capturamos cuando ML Kit reporta GOOD;
                                // así evitamos embeddings de caras lejanas,
                                // giradas o con ojos cerrados.
                                scanStatus = if (faceHint.isNotEmpty()) {
                                    faceHint
                                } else if (lang == "en") {
                                    "Place your face in front of the camera"
                                } else {
                                    "Ubica tu rostro frente a la cámara"
                                }
                            }
                            else -> {
                                // La detección de ML Kit no identifica personas.
                                // Se captura la cara, se genera la plantilla en
                                // vivo y este teléfono decide si coincide.
                                isVerifyingFace = true
                                scanStatus = ""
                                faceVerified = false
                                faceCaptureRequest += 1
                            }
                        }
                    },
                    enabled = !isRegistering && !isLocating && !isVerifyingFace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .animateContentSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isRegistering || isLocating || isVerifyingFace) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                selectedTab == 1 && !hasCameraPermission ->
                                    if (lang == "en") "ALLOW CAMERA" else "PERMITIR CÁMARA"
                                selectedTab == 1 && !isFaceEnrolled ->
                                    if (lang == "en") "ENROLL MY FACE" else "ENROLAR MI ROSTRO"
                                lang == "en" -> "REGISTER CHECK-IN"
                                else -> "REGISTRAR ENTRADA"
                            },
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

/**
 * Motivo por el que no se puede obtener una ubicación fiable.
 */
private enum class LocationIssue {
    MISSING_PERMISSION,
    APPROXIMATE_PERMISSION,
    LOCATION_DISABLED,
    GPS_DISABLED
}

/**
 * Traduce los códigos del backend a mensajes que el empleado pueda entender.
 * Antes se mostraban códigos crudos como "ACCURACY_TOO_HIGH" o
 * "NO_SEDE_ASSIGNED", que parecían errores de la app.
 */
internal fun attendanceErrorText(code: String?, lang: String): String {
    val en = lang == "en"
    // El ViewModel adjunta el score como "FACE_MISMATCH:0.642".
    val baseCode = code?.substringBefore(':')
    val faceScoreText = code?.substringAfter(':', "")?.takeIf { it.isNotBlank() }
    return when (baseCode) {
        null, "", "INTERNAL_ERROR" ->
            if (en) "Could not register attendance. Try again." else "No se pudo registrar la asistencia. Inténtalo de nuevo."

        "BUSY" ->
            if (en) "An operation is already in progress. Wait a moment." else "Ya hay una operación en curso. Espera un momento."

        "GPS_UNAVAILABLE" ->
            if (en)
                "We could not get your location. Turn on GPS and try again."
            else
                "No se pudo obtener tu ubicación. Activa el GPS e inténtalo de nuevo."

        "ACCURACY_TOO_HIGH", "INVALID_ACCURACY" ->
            if (en)
                "The GPS signal is still imprecise. Wait a few seconds or move near a window and try again."
            else
                "La señal GPS es imprecisa. Espera unos segundos o acércate a una ventana e inténtalo de nuevo."

        "OUT_OF_RANGE_GEOFENCE" ->
            if (en)
                "You are outside your branch's allowed area."
            else
                "Estás fuera del área permitida de tu sede."

        "NO_SEDE_ASSIGNED" ->
            if (en)
                "Your user has no branch assigned. Ask your administrator."
            else
                "Tu usuario no tiene una sede asignada. Pídele al administrador que la asigne."

        "NO_SHIFT_ASSIGNED", "UNSUPPORTED_SHIFT" ->
            if (en)
                "Your user has no valid shift assigned. Ask your administrator."
            else
                "Tu usuario no tiene un turno válido asignado. Pídele al administrador que lo asigne."

        "OUT_OF_SCHEDULE_WINDOW", "OUT_OF_WINDOW" ->
            if (en)
                "You are outside your shift's check-in window."
            else
                "Estás fuera de la ventana horaria de tu turno."

        "TOO_SOON" ->
            if (en)
                "You must wait a few minutes after check-in before checking out."
            else
                "Deben pasar unos minutos desde tu entrada para registrar la salida."

        "ALREADY_COMPLETE" ->
            if (en)
                "You already registered check-in and check-out today."
            else
                "Ya registraste entrada y salida hoy."

        "EMPLOYEE_INACTIVE", "CALLER_INACTIVE" ->
            if (en)
                "Your user is inactive. Contact your administrator."
            else
                "Tu usuario está inactivo. Contacta al administrador."

        "ADMIN_CANNOT_MARK_SELF", "ADMIN_ONLY" ->
            if (en)
                "Administrators cannot check in from this screen."
            else
                "Los administradores no marcan asistencia desde esta pantalla."

        "NO_COMPANY_ASSIGNED" ->
            if (en)
                "Your user has no company assigned. Contact your administrator."
            else
                "Tu usuario no tiene una empresa asignada. Contacta al administrador."

        "COMPANY_NOT_FOUND" ->
            if (en)
                "The company assigned to your user does not exist in Firestore. Ask your administrator to check it."
            else
                "La empresa asignada a tu usuario no existe en Firestore. Pídele al administrador que la revise."

        "INVALID_SEDE_CONFIG", "INVALID_SHIFT_CONFIG", "INVALID_TIMEZONE" ->
            if (en)
                "The branch or shift configuration is invalid. Ask your administrator."
            else
                "La configuración de la sede o del turno no es válida. Pídele al administrador que la revise."

        "ATTENDANCE_WRITE_DENIED" ->
            if (en)
                "Firestore rejected the attendance write. Check that the security rules are deployed."
            else
                "Firestore rechazó el registro de asistencia. Verifica que las reglas de seguridad estén desplegadas."

        "INVALID_TYPE", "INVALID_REQUEST_ID", "INVALID_COORDINATES" ->
            if (en)
                "The attendance data is invalid. Try again."
            else
                "Los datos del marcaje no son válidos. Inténtalo de nuevo."

        "CALLER_NOT_FOUND", "TARGET_NOT_EMPLOYEE", "CORRUPT_CHECKIN_DATA" ->
            if (en)
                "Your user or today's attendance record is invalid. Contact your administrator."
            else
                "Tu usuario o el registro de asistencia de hoy no es válido. Contacta al administrador."

        "UNAUTHENTICATED" ->
            if (en) "Your session expired. Sign in again." else "Tu sesión expiró. Vuelve a iniciar sesión."

        "NO_NETWORK", "RETRY", "UNAVAILABLE" ->
            if (en)
                "No connection to the server. Check your internet and try again."
            else
                "Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo."

        "FACE_NOT_ENROLLED" ->
            if (en)
                "You have no face template enrolled. Enroll your face first."
            else
                "No tienes una plantilla facial enrolada. Enrola tu rostro primero."

        "FACE_MODEL_MISMATCH" ->
            if (en)
                "Your face enrollment uses an older model. Enroll your face again."
            else
                "Tu enrolamiento facial usa un modelo anterior. Vuelve a enrolar tu rostro."

        "FACE_MISMATCH" -> {
            val score = if (faceScoreText != null) {
                if (en) " (similarity $faceScoreText)" else " (similitud $faceScoreText)"
            } else {
                ""
            }
            if (en) {
                "The face does not match your enrollment$score. Look at the camera and try again."
            } else {
                "El rostro no coincide con tu enrolamiento$score. Mira de frente a la cámara e inténtalo de nuevo."
            }
        }

        "FACE_EMBEDDING_INVALID", "FACE_EMBEDDING_FAILED" ->
            if (en)
                "We could not process your face. Try again with good lighting and no accessories."
            else
                "No se pudo procesar tu rostro. Inténtalo de nuevo con buena luz y sin accesorios."

        "FACE_NOT_VERIFIED" ->
            if (en)
                "Verify your face before confirming the attendance."
            else
                "Verifica tu rostro antes de confirmar la asistencia."

        "FACE_VERIFICATION_FAILED" ->
            if (en)
                "Could not calculate the local similarity. Check your connection and try again."
            else
                "No se pudo calcular la similitud local. Revisa tu conexión e inténtalo de nuevo."

        "FACE_VERIFICATION_PERMISSION" ->
            if (en)
                "Firestore denied access to your face template. Sign in again and retry."
            else
                "Firestore denegó el acceso a tu plantilla facial. Vuelve a iniciar sesión e inténtalo de nuevo."

        "FACE_CALIBRATION_FAILED" ->
            if (en)
                "Could not calculate the local similarity. Check your connection and try again."
            else
                "No se pudo calcular la similitud local. Revisa tu conexión e inténtalo de nuevo."

        "FACE_CALIBRATION_PERMISSION" ->
            if (en)
                "Firestore denied access to your face template. Sign in again and retry."
            else
                "Firestore denegó el acceso a tu plantilla facial. Vuelve a iniciar sesión e inténtalo de nuevo."

        "UNIMPLEMENTED" ->
            if (en) "This feature is not available yet." else "Esta función aún no está disponible."

        else ->
            if (en) "Error (${baseCode ?: ""}). Try again." else "Error (${baseCode ?: ""}). Inténtalo de nuevo."
    }
}
