package com.biosecure.app.ui.screens.enroll

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.biosecure.app.data.prefs.FaceEnrollmentStore
import com.biosecure.app.ui.components.FaceCaptureCamera
import com.biosecure.app.ui.components.FaceQuality
import com.biosecure.app.ui.components.QualityLevel
import com.biosecure.app.ui.theme.LocalAppLanguage
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class EnrollPhase { INTRO, CAMERA, SAVING, DONE }

private data class EnrollStep(
    val titleEs: String,
    val titleEn: String,
    val hintEs: String,
    val hintEn: String
)

private val steps = listOf(
    EnrollStep(
        titleEs = "Mira de frente",
        titleEn = "Look straight ahead",
        hintEs = "Ubica tu rostro dentro del óvalo y mantén los ojos abiertos.",
        hintEn = "Place your face inside the oval and keep your eyes open."
    ),
    EnrollStep(
        titleEs = "Gira un poco a un lado",
        titleEn = "Turn slightly to one side",
        hintEs = "Gira la cabeza sin mover el celular.",
        hintEn = "Turn your head without moving the phone."
    ),
    EnrollStep(
        titleEs = "Gira al otro lado",
        titleEn = "Turn to the other side",
        hintEs = "Gira la cabeza hacia el lado contrario al anterior.",
        hintEn = "Turn your head to the opposite side from the last step."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceEnrollScreen(
    navController: NavController,
    viewModel: BioSecureViewModel? = null,
    store: FaceEnrollmentStore? = null
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val enrollmentStore = remember { store ?: FaceEnrollmentStore(context) }
    val scope = rememberCoroutineScope()

    val vmUid by (viewModel?.currentUserUid
        ?: remember { MutableStateFlow<String?>(null) }).collectAsState()
    val uid = vmUid ?: remember { FirebaseAuth.getInstance().currentUser?.uid }

    var enrolled by remember { mutableStateOf(uid?.let { enrollmentStore.isEnrolled(it) } == true) }
    var enrolledAt by remember { mutableStateOf(uid?.let { enrollmentStore.enrolledAt(it) } ?: 0L) }
    var phase by remember { mutableStateOf(EnrollPhase.INTRO) }

    // Consentimiento explícito: la plantilla matemática se sube a Firestore
    // y el servidor la usa para decidir si la cara coincide.
    var consentAccepted by remember { mutableStateOf(false) }

    val remoteEnrolled by (viewModel?.faceEnrolled
        ?: remember { MutableStateFlow(false) }).collectAsState()

    LaunchedEffect(uid) {
        uid?.let { viewModel?.refreshFaceEnrollment(it) }
    }

    LaunchedEffect(remoteEnrolled) {
        if (remoteEnrolled) {
            enrolled = true
            enrolledAt = uid?.let { enrollmentStore.enrolledAt(it) } ?: enrolledAt
        }
    }

    var stepIndex by remember { mutableStateOf(0) }
    val captured = remember { mutableStateListOf<Bitmap>() }
    var captureRequest by remember { mutableStateOf(0) }
    var quality by remember { mutableStateOf(FaceQuality()) }
    var message by remember { mutableStateOf<String?>(null) }
    // Signo del giro del paso 2, para exigir el lado contrario en el paso 3
    // (el eje Yaw de ML Kit cambia de signo según el lado y la cámara).
    var firstSideSign by remember { mutableStateOf(0) }

    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission.value = granted
        if (!granted) message = if (lang == "en") {
            "Camera permission is required to enroll your face"
        } else {
            "Se necesita permiso de cámara para enrolar tu rostro"
        }
    }

    LaunchedEffect(phase) {
        if (phase == EnrollPhase.CAMERA && !hasCameraPermission.value) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun resetFlow() {
        captured.clear()
        stepIndex = 0
        quality = FaceQuality()
        message = null
        captureRequest = 0
        firstSideSign = 0
    }

    fun onEnrollmentFailed() {
        phase = EnrollPhase.CAMERA
        message = if (lang == "en") {
            "Could not save the enrollment. Check your connection and try again."
        } else {
            "No se pudo guardar el enrolamiento. Revisa tu conexión e inténtalo de nuevo."
        }
    }

    fun saveEnrollment() {
        val currentUid = uid ?: return
        val samples = captured.toList()
        if (samples.isEmpty()) return
        phase = EnrollPhase.SAVING

        val vm = viewModel
        if (vm != null) {
            // Genera las plantillas y las sube a users/{uid}/faceEmbedding/current.
            vm.enrollFace(currentUid, samples) { ok, _ ->
                if (ok) {
                    enrolled = true
                    enrolledAt = enrollmentStore.enrolledAt(currentUid)
                    phase = EnrollPhase.DONE
                } else {
                    onEnrollmentFailed()
                }
            }
        } else {
            // Sin ViewModel (preview/tests), conservamos el respaldo local.
            scope.launch {
                val ok = withContext(Dispatchers.IO) { enrollmentStore.save(currentUid, samples) }
                if (ok) {
                    enrolled = true
                    enrolledAt = enrollmentStore.enrolledAt(currentUid)
                    phase = EnrollPhase.DONE
                } else {
                    onEnrollmentFailed()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (lang == "en") "Face enrollment" else "Enrolamiento facial",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uid.isNullOrBlank()) {
                Text(
                    text = if (lang == "en") {
                        "Sign in to enroll your face"
                    } else {
                        "Inicia sesión para enrolar tu rostro"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                return@Column
            }

            when (phase) {
                EnrollPhase.INTRO -> IntroContent(
                    lang = lang,
                    enrolled = enrolled,
                    enrolledAt = enrolledAt,
                    message = message,
                    consentAccepted = consentAccepted,
                    onConsentChange = { consentAccepted = it },
                    onStart = {
                        resetFlow()
                        phase = EnrollPhase.CAMERA
                    },
                    onDelete = {
                        val vm = viewModel
                        if (vm != null) {
                            vm.deleteFaceEnrollment(uid) { ok, _ ->
                                if (ok) {
                                    enrolled = false
                                    enrolledAt = 0L
                                    resetFlow()
                                } else {
                                    message = if (lang == "en") {
                                        "Could not delete the enrollment. Try again."
                                    } else {
                                        "No se pudo eliminar el enrolamiento. Inténtalo de nuevo."
                                    }
                                }
                            }
                        } else {
                            enrollmentStore.clear(uid)
                            enrolled = false
                            enrolledAt = 0L
                            resetFlow()
                        }
                    }
                )

                EnrollPhase.CAMERA -> {
                    val yaw = quality.yaw
                    val turnedEnough = kotlin.math.abs(yaw) >= 15f
                    val yawSign = when {
                        yaw > 0f -> 1
                        yaw < 0f -> -1
                        else -> 0
                    }
                    val oppositeSide = firstSideSign != 0 && yawSign != 0 && yawSign != firstSideSign
                    val wrongSide = stepIndex == 2 && turnedEnough && !oppositeSide
                    val poseOk = quality.level == QualityLevel.GOOD && when (stepIndex) {
                        0 -> kotlin.math.abs(yaw) <= 12f
                        1 -> turnedEnough
                        else -> turnedEnough && oppositeSide
                    }
                    val hint = guidanceText(lang, quality, stepIndex, poseOk, wrongSide)

                    CameraContent(
                        lang = lang,
                        hasCameraPermission = hasCameraPermission.value,
                        stepIndex = stepIndex,
                        poseOk = poseOk,
                        hint = hint,
                        captured = captured,
                        message = message,
                        captureRequest = captureRequest,
                        onQuality = { newQuality ->
                            quality = newQuality
                            if (stepIndex == 1 && kotlin.math.abs(newQuality.yaw) >= 15f) {
                                firstSideSign = if (newQuality.yaw > 0f) 1 else -1
                            }
                        },
                        onCaptured = { bitmap ->
                            captured.add(bitmap)
                            message = null
                            if (stepIndex < steps.lastIndex) {
                                stepIndex += 1
                                quality = FaceQuality()
                            } else {
                                saveEnrollment()
                            }
                        },
                        onError = { message = it },
                        onRequestPermission = {
                            cameraLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onCapture = { captureRequest += 1 }
                    )
                }

                EnrollPhase.SAVING -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (lang == "en") "Saving enrollment…" else "Guardando enrolamiento…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                EnrollPhase.DONE -> SuccessContent(
                    lang = lang,
                    onFinish = { navController.navigateUp() },
                    onAgain = {
                        resetFlow()
                        phase = EnrollPhase.CAMERA
                    }
                )
            }
        }
    }
}

@Composable
private fun IntroContent(
    lang: String,
    enrolled: Boolean,
    enrolledAt: Long,
    message: String?,
    consentAccepted: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (lang == "en") "Why enroll your face?" else "¿Para qué sirve el enrolamiento?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (lang == "en") {
                    "We capture three reference samples of your face. BioSecure converts them into a " +
                        "mathematical template and stores that template in your account (Firestore) so the " +
                        "server can verify your attendance. The original photos are not uploaded: they stay " +
                        "only on this device."
                } else {
                    "Capturamos tres muestras de referencia de tu rostro. BioSecure las convierte en una " +
                        "plantilla matemática y guarda esa plantilla en tu cuenta (Firestore) para que el " +
                        "servidor verifique tu asistencia. Las fotos originales no se suben: quedan solo en " +
                        "este dispositivo."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            listOf(
                if (lang == "en") "1. Look straight ahead" else "1. Mira de frente",
                if (lang == "en") "2. Turn slightly to one side" else "2. Gira un poco a un lado",
                if (lang == "en") "3. Turn to the other side" else "3. Gira un poco al otro lado"
            ).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (enrolled) {
                Text(
                    text = if (lang == "en") {
                        "Face already enrolled on ${formatDate(enrolledAt)}"
                    } else {
                        "Rostro ya enrolado el ${formatDate(enrolledAt)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = consentAccepted,
            onCheckedChange = onConsentChange
        )
        Text(
            text = if (lang == "en") {
                "I agree that BioSecure stores a mathematical template of my face in my account and " +
                    "uses it to verify my attendance on the server."
            } else {
                "Acepto que BioSecure guarde una plantilla matemática de mi rostro en mi cuenta y la use " +
                    "para verificar mi asistencia en el servidor."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp)
        )
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = onStart,
        enabled = consentAccepted,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = when {
                enrolled && lang == "en" -> "Enroll again"
                enrolled -> "Volver a enrolar"
                lang == "en" -> "Start enrollment"
                else -> "Comenzar enrolamiento"
            },
            fontWeight = FontWeight.Bold
        )
    }

    if (enrolled) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(
                text = if (lang == "en") "Delete my enrollment" else "Eliminar mi enrolamiento",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CameraContent(
    lang: String,
    hasCameraPermission: Boolean,
    stepIndex: Int,
    poseOk: Boolean,
    hint: String,
    captured: List<Bitmap>,
    message: String?,
    captureRequest: Int,
    onQuality: (FaceQuality) -> Unit,
    onCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onCapture: () -> Unit
) {
    val step = steps[stepIndex.coerceIn(steps.indices)]

    Text(
        text = if (lang == "en") "Step ${stepIndex + 1} of 3" else "Paso ${stepIndex + 1} de 3",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = if (lang == "en") step.titleEn else step.titleEs,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Text(
        text = if (lang == "en") step.hintEn else step.hintEs,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )

    Spacer(Modifier.height(16.dp))

    Box(
        modifier = Modifier
            .size(300.dp)
            .border(
                width = 3.dp,
                color = if (poseOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                FaceCaptureCamera(
                    modifier = Modifier.fillMaxSize(),
                    captureRequest = captureRequest,
                    onQuality = onQuality,
                    onCaptured = onCaptured,
                    onError = onError
                )
                // Óvalo guía
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.72f)
                        .fillMaxWidth(0.62f)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), CircleShape)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "📷", fontSize = 48.sp)
                Text(
                    text = if (lang == "en") "Camera permission needed" else "Se necesita permiso de cámara",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRequestPermission) {
                    Text(if (lang == "en") "Allow camera" else "Permitir cámara")
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val done = index < captured.size
            Box(
                modifier = Modifier
                    .size(if (index == stepIndex) 12.dp else 9.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            done -> MaterialTheme.colorScheme.secondary
                            index == stepIndex -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = hint,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (poseOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )

    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onCapture,
        enabled = hasCameraPermission && poseOk,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = if (lang == "en") "Capture sample" else "Capturar muestra",
            fontWeight = FontWeight.Bold
        )
    }

    if (captured.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            captured.forEach { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    lang: String,
    onFinish: () -> Unit,
    onAgain: () -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(96.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = if (lang == "en") "Face enrolled!" else "¡Rostro enrolado!",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Text(
        text = if (lang == "en") {
            "You can now check in with facial recognition from the Scan tab."
        } else {
            "Ya puedes marcar tu asistencia con reconocimiento facial desde la pestaña Escaneo."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onFinish,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(if (lang == "en") "Done" else "Listo", fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onAgain) {
        Text(if (lang == "en") "Capture again" else "Capturar de nuevo")
    }
}

private fun guidanceText(
    lang: String,
    quality: FaceQuality,
    stepIndex: Int,
    poseOk: Boolean,
    wrongSide: Boolean
): String {
    if (quality.level == QualityLevel.GOOD && poseOk) {
        return if (lang == "en") "Perfect, hold still and capture" else "Perfecto, mantente quieto y captura"
    }
    return when (quality.level) {
        QualityLevel.NO_FACE -> if (lang == "en") "No face detected" else "No se detecta un rostro"
        QualityLevel.MULTIPLE_FACES -> if (lang == "en") "Only one person at a time" else "Solo una persona a la vez"
        QualityLevel.TOO_FAR -> if (lang == "en") "Move closer to the camera" else "Acércate un poco más"
        QualityLevel.OFF_CENTER -> if (lang == "en") "Center your face in the oval" else "Centra tu rostro en el óvalo"
        QualityLevel.EYES_CLOSED -> if (lang == "en") "Keep your eyes open" else "Mantén los ojos abiertos"
        QualityLevel.TILTED -> if (lang == "en") "Keep your head straight" else "Mantén la cabeza derecha"
        QualityLevel.GOOD -> when {
            stepIndex == 0 -> if (lang == "en") "Face the camera" else "Mira de frente a la cámara"
            wrongSide -> if (lang == "en") "Turn to the other side" else "Gira hacia el otro lado"
            else -> if (lang == "en") "Turn your head a little more" else "Gira un poco más la cabeza"
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
}
