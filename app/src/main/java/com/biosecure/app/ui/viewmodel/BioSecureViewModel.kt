package com.biosecure.app.ui.viewmodel

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.Company
import com.biosecure.app.data.model.ConfirmationData
import com.biosecure.app.data.model.CompanyRequest
import com.biosecure.app.data.model.CreateUserRequest
import com.biosecure.app.data.model.UpdateUserRequest
import com.biosecure.app.data.model.User
import com.biosecure.app.data.repository.AttendanceRepository
import com.biosecure.app.data.repository.AttendanceResult
import com.biosecure.app.data.repository.AttendanceWriteResult
import com.biosecure.app.data.repository.AuthRepository
import com.biosecure.app.data.repository.FaceRecognitionRepository
import com.biosecure.app.data.repository.FaceVerification
import com.biosecure.app.data.repository.FirebaseFunctionsRepository
import com.biosecure.app.data.prefs.ThemePreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DashboardUiState(
    val totalAttendances: Int = 0,
    val punctualCount: Int = 0,
    val lateCount: Int = 0,
    val absenceCount: Int = 0,
    val punctualityRate: Float = 0f,
    val weeklyData: List<Float> = emptyList(),
    val recentWarnings: List<Attendance> = emptyList(),
    val isLoading: Boolean = false
)

class BioSecureViewModel(
    val authRepository: AuthRepository,
    val attendanceRepository: AttendanceRepository,
    val functionsRepository: FirebaseFunctionsRepository,
    private val faceRecognitionRepository: FaceRecognitionRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_LOCATION_AGE_SECONDS = 120L
    }

    val isDarkMode: StateFlow<Boolean?> = themePreferences.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setDarkMode(enabled) }
    }

    val currentLanguage: StateFlow<String> = themePreferences.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "es")

    fun setLanguage(lang: String) {
        viewModelScope.launch { themePreferences.setLanguage(lang) }
    }

    val notificationsEnabled: StateFlow<Boolean> = themePreferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setNotificationsEnabled(enabled) }
    }

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _currentRole = MutableStateFlow("employee")
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _attendances = MutableStateFlow<List<Attendance>>(emptyList())
    val attendances: StateFlow<List<Attendance>> = _attendances.asStateFlow()

    private val _currentUserName = MutableStateFlow("")
    val currentUserName: StateFlow<String> = _currentUserName.asStateFlow()

    private val _currentUserUid = MutableStateFlow<String?>(null)
    val currentUserUid: StateFlow<String?> = _currentUserUid.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authSuccess = MutableStateFlow<String?>(null)
    val authSuccess: StateFlow<String?> = _authSuccess.asStateFlow()

    private val _lastCreatedUser = MutableStateFlow<User?>(null)
    val lastCreatedUser: StateFlow<User?> = _lastCreatedUser.asStateFlow()

    private val _lastUpdatedUserId = MutableStateFlow<String?>(null)
    val lastUpdatedUserId: StateFlow<String?> = _lastUpdatedUserId.asStateFlow()

    private val _lastConfirmation = MutableStateFlow<ConfirmationData?>(null)
    val lastConfirmation: StateFlow<ConfirmationData?> = _lastConfirmation.asStateFlow()

    private val pendingRequestIds = mutableMapOf<String, String>()

    // --- Estado del reconocimiento facial (verificación local) ---
    private val _faceEnrolled = MutableStateFlow(false)
    val faceEnrolled: StateFlow<Boolean> = _faceEnrolled.asStateFlow()

    /**
     * Resultado de la última verificación local de la captura actual. Solo se
     * permite escribir la asistencia FACIAL si `matched == true`.
     */
    private val _pendingFaceVerification = MutableStateFlow<FaceVerification?>(null)

    // Marca de "marcaje en curso". Antes se usaba isLoading (compartido con la
    // carga del historial, el dashboard, etc.); si esa carga se quedaba
    // pendiente, el botón de marcar no hacía nada (fallaba en silencio).
    private val attendanceInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _firestoreEmployees = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val firestoreEmployees: StateFlow<List<Map<String, Any>>> = _firestoreEmployees.asStateFlow()

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    private val _selectedSedeId = MutableStateFlow<String?>(null)
    val selectedSedeId: StateFlow<String?> = _selectedSedeId.asStateFlow()

    // Cada llamada a observeDashboard/observeAttendanceHistory creaba un
    // collector nuevo que nunca se cancelaba (varias pantallas las invocan).
    // Eso duplicaba listeners de Firestore y dejaba isLoading oscilando.
    private var dashboardJob: Job? = null
    private var historyJob: Job? = null

    fun setSelectedSede(sedeId: String?) {
        _selectedSedeId.value = sedeId
        observeDashboard()
    }

    private val _companyConfig = MutableStateFlow<Company?>(null)
    val companyConfig: StateFlow<Company?> = _companyConfig.asStateFlow()

    private val _shifts = MutableStateFlow<List<com.biosecure.app.data.model.Shift>>(emptyList())
    val shifts: StateFlow<List<com.biosecure.app.data.model.Shift>> = _shifts.asStateFlow()

    private val _sedes = MutableStateFlow<List<com.biosecure.app.data.model.Sede>>(emptyList())
    val sedes: StateFlow<List<com.biosecure.app.data.model.Sede>> = _sedes.asStateFlow()


    fun setRole(role: String) {
        _currentRole.value = role
    }

    fun clearAuthSuccess() { _authSuccess.value = null }
    fun clearAuthError() { _authError.value = null }
    fun setConfirmationData(data: ConfirmationData) { _lastConfirmation.value = data }

    fun observeDashboard() {
        val companyId = _currentCompanyId.value
        val filterSedeId = _selectedSedeId.value
        if (companyId.isEmpty()) return

        loadCompanyConfig(companyId)

        dashboardJob?.cancel()
        dashboardJob = viewModelScope.launch {
            _dashboardState.value = _dashboardState.value.copy(isLoading = true)
            try {
                attendanceRepository.observeTodayAttendances(companyId).collectLatest { allAttendances ->
                    val filteredAttendances = if (filterSedeId == null) {
                        allAttendances
                    } else {
                        val companyEmployees = attendanceRepository.getEmployees(companyId)
                        val employeesInSede = companyEmployees
                            .filter { it["sedeId"] == filterSedeId }
                            .map { it["uid"] as String }
                        allAttendances.filter { it.userId in employeesInSede }
                    }

                    val total = filteredAttendances.size
                    val punctual = filteredAttendances.count { it.status == AttendanceStatus.PUNTUAL || it.status == AttendanceStatus.EXITOSO }
                    val late = filteredAttendances.count { it.status == AttendanceStatus.TARDANZA }
                    val absences = filteredAttendances.count { it.status == AttendanceStatus.INASISTENCIA }

                    val rate = if (total > 0) (punctual.toFloat() / total.toFloat()) * 100 else 0f
                    val simulatedWeekly = listOf(75f, 82f, 90f, rate, rate - 5, rate + 2, rate)

                    _dashboardState.value = DashboardUiState(
                        totalAttendances = total,
                        punctualCount = punctual,
                        lateCount = late,
                        absenceCount = absences,
                        punctualityRate = rate,
                        weeklyData = simulatedWeekly,
                        recentWarnings = filteredAttendances.filter { it.status == AttendanceStatus.TARDANZA || it.status == AttendanceStatus.FALLIDO },
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("index")) {
                    _error.value = "Error: Falta crear el índice para el Dashboard (companyId ASC, date ASC, checkIn DESC)"
                } else {
                    _error.value = "Error al cargar estadísticas del día"
                }
                _dashboardState.value = _dashboardState.value.copy(isLoading = false)
                e.printStackTrace()
            }
        }
    }

    private var usersJob: Job? = null

    fun loadUsers() {
        usersJob?.cancel()
        usersJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                attendanceRepository.observeEmployees(_currentCompanyId.value).collect { employees ->
                    _users.value = employees.map { data ->
                        val fullName = data["name"] as? String ?: ""
                        val first = data["firstName"] as? String ?: fullName.split(" ").firstOrNull() ?: ""
                        val last = data["lastName"] as? String ?: fullName.split(" ").drop(1).joinToString(" ")

                        User(
                            uid = data["uid"] as? String ?: "",
                            firstName = first,
                            lastName = last,
                            email = data["email"] as? String ?: "",
                            cargo = data["cargo"] as? String ?: "",
                            department = data["department"] as? String ?: "",
                            role = data["role"] as? String ?: "employee",
                            isActive = data["isActive"] as? Boolean ?: true,
                            shiftId = data["shiftId"] as? String ?: "",
                            sedeId = data["sedeId"] as? String ?: ""
                        )
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar usuarios"
                _isLoading.value = false
            }
        }
    }

    private val _currentCompanyId = MutableStateFlow("")
    val currentCompanyId: StateFlow<String> = _currentCompanyId.asStateFlow()

    fun clearLastConfirmation() { _lastConfirmation.value = null }

    fun loadCompanyConfig(companyId: String) {
        viewModelScope.launch {
            val config = attendanceRepository.getCompanyConfig(companyId)
            _companyConfig.value = config
            _shifts.value = attendanceRepository.getShifts(companyId)
            _sedes.value = attendanceRepository.getSedes(companyId)
        }
    }

    fun saveSede(nombre: String, lat: Double, lng: Double, radio: Int, id: String = "") {
        val companyId = _currentCompanyId.value
        if (companyId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val sede = com.biosecure.app.data.model.Sede(id = id, nombre = nombre, lat = lat, lng = lng, radioMetros = radio)
            val success = attendanceRepository.saveSede(companyId, sede)
            if (success) {
                _sedes.value = attendanceRepository.getSedes(companyId)
            } else {
                _error.value = "Error al guardar la sede"
            }
            _isLoading.value = false
        }
    }

    fun deleteSede(sedeId: String) {
        val companyId = _currentCompanyId.value
        if (companyId.isEmpty() || sedeId.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            val success = attendanceRepository.deleteSede(companyId, sedeId)
            if (success) {
                _sedes.value = attendanceRepository.getSedes(companyId)
            } else {
                _error.value = "Error al eliminar la sede"
            }
            _isLoading.value = false
        }
    }

    fun assignSedeToEmployee(uid: String, sedeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = attendanceRepository.assignSedeToEmployee(uid, sedeId)
            if (success) {
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(sedeId = sedeId) else it
                }
            } else {
                _error.value = "Error al asignar sede"
            }
            _isLoading.value = false
        }
    }

    fun saveShift(name: String, startTime: String, toleranceMin: Int, endTime: String, id: String = "") {
        val companyId = _currentCompanyId.value
        if (companyId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val shift = com.biosecure.app.data.model.Shift(id = id, name = name, startTime = startTime, toleranceMin = toleranceMin, endTime = endTime)
            val success = attendanceRepository.saveShift(companyId, shift)
            if (success) {
                _shifts.value = attendanceRepository.getShifts(companyId)
            } else {
                _error.value = "Error al guardar el turno"
            }
            _isLoading.value = false
        }
    }

    fun deleteShift(shiftId: String) {
        val companyId = _currentCompanyId.value
        if (companyId.isEmpty() || shiftId.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            val success = attendanceRepository.deleteShift(companyId, shiftId)
            if (success) {
                _shifts.value = attendanceRepository.getShifts(companyId)
            } else {
                _error.value = "Error al eliminar el turno"
            }
            _isLoading.value = false
        }
    }

    fun saveShifts(companyId: String, shifts: List<com.biosecure.app.data.model.Shift>) {
        viewModelScope.launch {
            _isLoading.value = true
            shifts.forEach { shift -> attendanceRepository.saveShift(companyId, shift) }
            _shifts.value = attendanceRepository.getShifts(companyId)
            _isLoading.value = false
        }
    }

    fun assignShiftToEmployee(uid: String, shiftId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = attendanceRepository.assignShiftToEmployee(uid, shiftId)
            if (success) {
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(shiftId = shiftId) else it
                }
            } else {
                _error.value = "Error al asignar turno"
            }
            _isLoading.value = false
        }
    }

    fun updateUserPhoto(uid: String, uriString: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val uri = android.net.Uri.parse(uriString)
            val downloadUrl = attendanceRepository.uploadUserPhoto(uid, uri)
            if (downloadUrl != null) {
                val success = attendanceRepository.updateUserPhoto(uid, downloadUrl)
                if (success) {
                    if (_currentUserUid.value == uid) {
                        _currentUser.value = _currentUser.value?.copy(image = downloadUrl)
                    }
                    _users.value = _users.value.map {
                        if (it.uid == uid) it.copy(image = downloadUrl) else it
                    }
                } else {
                    _error.value = "Error al actualizar la base de datos con la nueva imagen"
                }
            } else {
                _error.value = "Error al subir la imagen al servidor"
            }
            _isLoading.value = false
        }
    }

    fun updateCompanySchedule(horaEntrada: String, toleranciaMin: Int) {
        val companyId = _currentCompanyId.value
        if (companyId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val success = attendanceRepository.updateCompanyConfig(companyId, horaEntrada, toleranciaMin)
            if (success) {
                _companyConfig.value = _companyConfig.value?.copy(
                    horaEntrada = horaEntrada,
                    toleranciaMin = toleranciaMin
                ) ?: Company(id = companyId, horaEntrada = horaEntrada, toleranciaMin = toleranciaMin)
            } else {
                _error.value = "Error al actualizar el horario"
            }
            _isLoading.value = false
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.getCurrentUserResult().onSuccess { result ->
                    _currentUserName.value = result.name
                    _currentUserUid.value = result.user.uid
                    _currentCompanyId.value = result.companyId
                    _currentRole.value = result.role

                    _currentUser.value = User(
                        uid = result.user.uid,
                        firstName = result.name.split(" ").firstOrNull() ?: "",
                        lastName = result.name.split(" ").lastOrNull() ?: "",
                        email = result.user.email ?: "",
                        role = result.role,
                        companyId = result.companyId,
                        image = result.image
                    )

                    loadCompanyConfig(result.companyId)
                }
            } catch (e: Exception) {
                _error.value = "Error al restaurar sesión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                val result = authRepository.loginWithRole(email.trim(), password.trim()).getOrThrow()
                _currentUserName.value = result.name
                _currentUserUid.value = result.user.uid
                _currentCompanyId.value = result.companyId
                _currentRole.value = result.role
                _authSuccess.value = result.role

                _currentUser.value = User(
                    uid = result.user.uid,
                    firstName = result.name.split(" ").firstOrNull() ?: "",
                    lastName = result.name.split(" ").lastOrNull() ?: "",
                    email = result.user.email ?: "",
                    role = result.role,
                    companyId = result.companyId,
                    image = result.image
                )
            } catch (e: Exception) {
                _authError.value = e.message ?: "Error de autenticación"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loginWithGoogle(idToken: String, photoUrl: String?, displayName: String?, email: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                val result = authRepository.loginWithGoogle(idToken, photoUrl, displayName, email).getOrThrow()
                _currentUserName.value = result.name
                _currentUserUid.value = result.user.uid
                _currentCompanyId.value = result.companyId
                _currentRole.value = result.role
                _authSuccess.value = result.role

                _currentUser.value = User(
                    uid = result.user.uid,
                    firstName = result.name.split(" ").firstOrNull() ?: "",
                    lastName = result.name.split(" ").lastOrNull() ?: "",
                    email = result.user.email ?: "",
                    role = result.role,
                    companyId = result.companyId,
                    image = result.image
                )
            } catch (e: Exception) {
                _authError.value = e.message ?: "Error de autenticación con Google"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = authRepository.changePassword(currentPassword, newPassword)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Error al cambiar contraseña")
            }
            _isLoading.value = false
        }
    }

    fun loginAsAdmin(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            authRepository.loginWithRole(email, password)
                .onSuccess { result ->
                    if (result.role == "admin") {
                        _currentUserName.value = result.name
                        _currentUserUid.value = result.user.uid
                        _currentCompanyId.value = result.companyId
                        _currentRole.value = "admin"
                        _authSuccess.value = "admin"

                        _currentUser.value = User(
                            uid = result.user.uid,
                            firstName = result.name.split(" ").firstOrNull() ?: "",
                            lastName = result.name.split(" ").lastOrNull() ?: "",
                            email = result.user.email ?: "",
                            role = "admin",
                            companyId = result.companyId,
                            image = result.image
                        )
                    } else {
                        authRepository.logout()
                        _authError.value = "No tienes permisos de administrador"
                    }
                }
                .onFailure { _authError.value = it.message ?: "Credenciales incorrectas" }
            _isLoading.value = false
        }
    }

    fun loginAsEmployee(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            authRepository.loginWithRole(email, password)
                .onSuccess { result ->
                    if (result.role == "employee") {
                        _currentUserName.value = result.name
                        _currentUserUid.value = result.user.uid
                        _currentCompanyId.value = result.companyId
                        _currentRole.value = "employee"
                        _authSuccess.value = "employee"

                        _currentUser.value = User(
                            uid = result.user.uid,
                            firstName = result.name.split(" ").firstOrNull() ?: "",
                            lastName = result.name.split(" ").lastOrNull() ?: "",
                            email = result.user.email ?: "",
                            role = "employee",
                            companyId = result.companyId,
                            image = result.image
                        )
                    } else {
                        authRepository.logout()
                        _authError.value = "No tienes permisos de empleado"
                    }
                }
                .onFailure { _authError.value = it.message ?: "Credenciales incorrectas" }
            _isLoading.value = false
        }
    }

    private fun validateLocation(location: android.location.Location?): String? {
        if (location == null) return "GPS_UNAVAILABLE"
        if (!location.hasAccuracy()) return "GPS_UNAVAILABLE"
        if (location.accuracy > MAX_ACCURACY_METERS) return "ACCURACY_TOO_HIGH"

        val ageSeconds = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000_000L
        if (location.elapsedRealtimeNanos <= 0L || ageSeconds > MAX_LOCATION_AGE_SECONDS) {
            return "GPS_UNAVAILABLE"
        }
        return null
    }

    fun registerAttendanceQR(
        scannedUid: String,
        location: android.location.Location?,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (!attendanceInFlight.compareAndSet(false, true)) {
            onResult(false, "BUSY")
            return
        }

        val locError = validateLocation(location)
        if (locError != null) {
            attendanceInFlight.set(false)
            _error.value = locError
            onResult(false, locError)
            return
        }
        val validLoc = location!!

        viewModelScope.launch {
            try {
                val isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    validLoc.isMock
                } else {
                    @Suppress("DEPRECATION")
                    validLoc.isFromMockProvider
                }

                val requestKey = "QR_${scannedUid}"
                val requestId = pendingRequestIds.getOrPut(requestKey) { UUID.randomUUID().toString() }

                when (val result = functionsRepository.registerAttendance(
                    tipo = "QR",
                    lat = validLoc.latitude,
                    lng = validLoc.longitude,
                    accuracy = validLoc.accuracy,
                    isMock = isMock,
                    requestId = requestId,
                    qrToken = scannedUid
                )) {
                    is AttendanceResult.Success -> {
                        pendingRequestIds.remove(requestKey)
                        val resData = result.data
                        _lastConfirmation.value = ConfirmationData(
                            userName = resData.userName,
                            checkIn = resData.checkIn,
                            checkOut = resData.checkOut,
                            date = resData.date,
                            scanType = "QR",
                            status = resData.status
                        )
                        _authSuccess.value = "Asistencia registrada correctamente"
                        onResult(true, null)
                    }
                    is AttendanceResult.Error -> {
                        val code = result.code
                        val retryableCodes = setOf("NO_NETWORK", "RETRY", "UNAVAILABLE", "INTERNAL_ERROR")
                        if (code !in retryableCodes) {
                            pendingRequestIds.remove(requestKey)
                        }
                        _error.value = code
                        onResult(false, code)
                    }
                }
            } finally {
                attendanceInFlight.set(false)
            }
        }
    }

    /**
     * Registra un marcaje propio sin Cloud Functions.
     *
     * Para FACIAL exige que la verificación local (`verifyFace`) haya dado
     * `matched == true`; si no, no se escribe nada. Antes de escribir también
     * se valida GPS (<= 50 m, ubicación reciente), sede, turno, geocerca y
     * ventana horaria dentro de AttendanceRepository.
     */
    fun registerAttendanceBiometric(
        tipo: String, // "HUELLA" | "FACIAL"
        location: android.location.Location?,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (!attendanceInFlight.compareAndSet(false, true)) {
            onResult(false, "BUSY")
            return
        }

        val pendingFace = if (tipo == "FACIAL") {
            _pendingFaceVerification.value
        } else {
            null
        }
        if (tipo == "FACIAL") {
            if (pendingFace == null) {
                attendanceInFlight.set(false)
                _error.value = "FACE_NOT_VERIFIED"
                onResult(false, "FACE_NOT_VERIFIED")
                return
            }
            if (!pendingFace.matched) {
                attendanceInFlight.set(false)
                val code = "FACE_MISMATCH:" +
                    String.format(Locale.US, "%.3f", pendingFace.similarity)
                _pendingFaceVerification.value = null
                _error.value = code
                onResult(false, code)
                return
            }
        }

        val locError = validateLocation(location)
        if (locError != null) {
            attendanceInFlight.set(false)
            _error.value = locError
            onResult(false, locError)
            return
        }
        val validLoc = location!!
        val targetUid = _currentUserUid.value
            ?: FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        viewModelScope.launch {
            try {
                val isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    validLoc.isMock
                } else {
                    @Suppress("DEPRECATION")
                    validLoc.isFromMockProvider
                }

                val requestKey = "${tipo}_SELF"
                val requestId = pendingRequestIds.getOrPut(requestKey) { UUID.randomUUID().toString() }

                when (val result = attendanceRepository.registerLocalAttendance(
                    uid = targetUid,
                    tipo = tipo,
                    latitude = validLoc.latitude,
                    longitude = validLoc.longitude,
                    accuracy = validLoc.accuracy,
                    isMock = isMock,
                    requestId = requestId,
                    faceVerified = pendingFace?.matched == true
                )) {
                    is AttendanceWriteResult.Success -> {
                        pendingRequestIds.remove(requestKey)
                        _pendingFaceVerification.value = null
                        _lastConfirmation.value = ConfirmationData(
                            userName = result.userName,
                            checkIn = result.checkIn,
                            checkOut = result.checkOut,
                            date = result.date,
                            scanType = tipo,
                            status = result.status
                        )
                        _authSuccess.value = "Asistencia registrada correctamente"
                        onResult(true, null)
                    }
                    is AttendanceWriteResult.Error -> {
                        val code = result.code
                        val retryableCodes = setOf("NO_NETWORK", "RETRY", "UNAVAILABLE", "INTERNAL_ERROR")
                        if (code.substringBefore(':') !in retryableCodes) {
                            pendingRequestIds.remove(requestKey)
                            // La verificación facial sigue siendo válida para
                            // reintentar errores de GPS/sede/turno; solo se
                            // descarta cuando el fallo es de la propia cara o
                            // de la sesión.
                            if (code.startsWith("FACE_") || code == "UNAUTHENTICATED") {
                                _pendingFaceVerification.value = null
                            }
                        }
                        _error.value = code
                        onResult(false, code)
                    }
                }
            } finally {
                attendanceInFlight.set(false)
            }
        }
    }

    /**
     * Verificación facial 100 % local: genera la plantilla en vivo, la
     * compara con la guardada en `users/{uid}/faceEmbedding/current` y aplica
     * el umbral 0.70. No llama a Cloud Functions.
     */
    fun verifyFace(
        uid: String,
        sample: Bitmap,
        onResult: (Boolean, String?, Double?) -> Unit = { _, _, _ -> }
    ) {
        if (uid.isBlank()) {
            onResult(false, "UNAUTHENTICATED", null)
            return
        }
        viewModelScope.launch {
            faceRecognitionRepository.verify(uid, sample)
                .onSuccess { verification ->
                    _pendingFaceVerification.value = verification
                    val score = verification.similarity.toDouble()
                    if (verification.matched) {
                        onResult(true, null, score)
                    } else {
                        onResult(
                            false,
                            "FACE_MISMATCH:" + String.format(Locale.US, "%.3f", score),
                            score
                        )
                    }
                }
                .onFailure { e ->
                    _pendingFaceVerification.value = null
                    onResult(false, faceVerificationErrorCode(e), null)
                }
        }
    }

    private fun faceVerificationErrorCode(e: Throwable): String {
        val message = e.message.orEmpty()
        return when {
            message == "FACE_NOT_ENROLLED" ||
            message == "FACE_MODEL_MISMATCH" ||
            message == "FACE_EMBEDDING_INVALID" -> message
            message.contains("PERMISSION_DENIED") -> "FACE_VERIFICATION_PERMISSION"
            message.contains("UNAVAILABLE") ||
            message.contains("network", ignoreCase = true) -> "NO_NETWORK"
            else -> "FACE_VERIFICATION_FAILED"
        }
    }

    /**
     * Calibración manual (opcional): compara una captura contra la plantilla
     * guardada y devuelve el score. No registra asistencia.
     */
    fun calibrateFace(
        uid: String,
        sample: Bitmap,
        onResult: (Boolean, String?, Double?) -> Unit = { _, _, _ -> }
    ) {
        if (uid.isBlank()) {
            onResult(false, "UNAUTHENTICATED", null)
            return
        }
        viewModelScope.launch {
            faceRecognitionRepository.generateLiveEmbedding(sample)
                .onSuccess { live ->
                    try {
                        val result = faceRecognitionRepository.calibrate(uid, live)
                        _pendingFaceVerification.value = null
                        onResult(true, null, result.score)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _pendingFaceVerification.value = null
                        onResult(false, faceVerificationErrorCode(e), null)
                    }
                }
                .onFailure {
                    _pendingFaceVerification.value = null
                    onResult(false, "FACE_EMBEDDING_FAILED", null)
                }
        }
    }

    /** Resultado de verificación facial de la captura actual (o null). */
    fun peekPendingFaceVerification(): FaceVerification? = _pendingFaceVerification.value

    /** Genera las plantillas de las muestras y las sube a Firestore. */
    fun enrollFace(
        uid: String,
        samples: List<Bitmap>,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                faceRecognitionRepository.enrollFace(uid, samples)
                    .onSuccess {
                        _faceEnrolled.value = true
                        _pendingFaceVerification.value = null
                        onResult(true, null)
                    }
                    .onFailure {
                        _error.value = it.message ?: "No se pudo enrolar el rostro"
                        onResult(false, it.message ?: "FACE_ENROLLMENT_FAILED")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Borra la plantilla remota y la caché local. */
    fun deleteFaceEnrollment(
        uid: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                faceRecognitionRepository.deleteFaceEnrollment(uid)
                _faceEnrolled.value = false
                _pendingFaceVerification.value = null
                onResult(true, null)
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo eliminar el enrolamiento facial"
                onResult(false, e.message ?: "FACE_DELETE_FAILED")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresca si el usuario tiene plantilla facial. Si Firestore no se
     * puede consultar, conserva el estado local en lugar de asumir que no
     * está enrolado.
     */
    fun refreshFaceEnrollment(uid: String) {
        if (uid.isBlank()) return
        viewModelScope.launch {
            val local = faceRecognitionRepository.isLocallyEnrolled(uid)
            try {
                val remote = faceRecognitionRepository.loadRemote(uid)
                _faceEnrolled.value = remote != null || local
            } catch (e: Exception) {
                _faceEnrolled.value = local
            }
        }
    }

    fun registerManualAttendance(user: User) {
        // TODO: Migrar a la Cloud Function registerManualAttendance cuando esté implementada.
        _error.value = "Función no disponible temporalmente"
    }

    fun getStats(): Map<AttendanceStatus, Int> {
        return _attendances.value
            .groupBy { it.status }
            .mapValues { it.value.size }
    }

    fun createEmployee(firstName: String, lastName: String, email: String, title: String, department: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val newUser = User(uid = "temp_${Date().time}", firstName = firstName, lastName = lastName, email = email)
                _lastCreatedUser.value = newUser
                _users.value = _users.value + newUser
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al registrar empleado"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateEmployee(uid: String, firstName: String, lastName: String, email: String, cargo: String = "", department: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val success = attendanceRepository.updateEmployee(uid, firstName, lastName, email, cargo, department)
                if (success) {
                    _users.value = _users.value.map {
                        if (it.uid == uid) it.copy(
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            cargo = cargo,
                            department = department
                        ) else it
                    }
                    _lastUpdatedUserId.value = uid
                } else {
                    _error.value = "Error al actualizar empleado en el servidor"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al actualizar empleado"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEmployee(uid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val deleted = attendanceRepository.deleteEmployee(uid)
                if (deleted) {
                    _users.value = _users.value.filter { it.uid != uid }
                } else {
                    _error.value = "Error al eliminar empleado"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al eliminar empleado"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _attendanceHistoryFlow = MutableStateFlow<List<Attendance>>(emptyList())
    val attendanceHistoryFlow: StateFlow<List<Attendance>> = _attendanceHistoryFlow.asStateFlow()

    fun observeAttendanceHistory() {
        _error.value = null

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            combine(_currentCompanyId, _currentUserUid, _currentRole) { companyId, uid, role ->
                Triple(companyId, uid, role)
            }.collectLatest { (companyId, uid, role) ->
                if (companyId.isEmpty() || uid == null) return@collectLatest

                _isLoading.value = true
                try {
                    val flow = if (role == "admin")
                        attendanceRepository.observeAllAttendances(companyId)
                    else
                        attendanceRepository.observeUserAttendances(uid)

                    flow.collectLatest { history ->
                        _attendanceHistoryFlow.value = history
                        _isLoading.value = false
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: ""
                    if (errorMsg.contains("index")) {
                        val missingIndex = if (role == "admin")
                            "companyId ASC, timestamp DESC"
                        else
                            "userId ASC, timestamp DESC"
                        _error.value = "Error: Falta crear el índice para el Historial ($missingIndex)"
                    } else {
                        _error.value = "Error de conexión con el servidor"
                    }
                    _isLoading.value = false
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadFirestoreEmployees() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _firestoreEmployees.value = attendanceRepository.getEmployees(_currentCompanyId.value)
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar empleados"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFirestoreEmployee(uid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deleted = attendanceRepository.deleteEmployee(uid)
                if (deleted) {
                    _firestoreEmployees.value = _firestoreEmployees.value.filter { it["uid"] != uid }
                } else {
                    _error.value = "Error al eliminar empleado"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al eliminar empleado"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearLastCreatedUser() { _lastCreatedUser.value = null }

    fun updateUserName(name: String) {
        val uid = _currentUserUid.value ?: return
        viewModelScope.launch {
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .update("name", name).await()
                _currentUserName.value = name
                _currentUser.value = _currentUser.value?.let {
                    val parts = name.split(" ")
                    it.copy(firstName = parts.firstOrNull() ?: "", lastName = parts.drop(1).joinToString(" "))
                }
            } catch (e: Exception) {
                _error.value = "Error al actualizar nombre: ${e.message}"
            }
        }
    }

    fun clearLastUpdatedUserId() { _lastUpdatedUserId.value = null }

    fun setUserClaims(uid: String, role: String, companyId: String) {
        viewModelScope.launch {
            functionsRepository.setUserClaims(uid, role, companyId)
        }
    }

    fun toggleEmployeeStatus(uid: String, currentStatus: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = attendanceRepository.updateEmployeeStatus(uid, !currentStatus)
            if (success) {
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(isActive = !currentStatus) else it
                }
                _firestoreEmployees.value = _firestoreEmployees.value.map {
                    if (it["uid"] == uid) it.plus("isActive" to !currentStatus) else it
                }
            } else {
                _error.value = "Error al cambiar estado del empleado"
            }
            _isLoading.value = false
        }
    }

    fun exportAttendancesToCSV(context: android.content.Context) {
        val records = _attendanceHistoryFlow.value
        if (records.isEmpty()) return

        val fileName = "Reporte_Asistencia_${SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())}.csv"
        val content = StringBuilder("Empleado,Fecha,Hora,Estado,Ubicacion\n")

        records.forEach { record ->
            content.append("${record.userName},${record.date},${record.checkIn},${record.status.name},${record.location}\n")
        }

        try {
            val file = java.io.File(context.cacheDir, fileName)
            file.writeText(content.toString())

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Compartir Reporte BioSecure"))
        } catch (e: Exception) {
            _error.value = "Error al exportar CSV: ${e.message}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceRecognitionRepository.close()
    }

    fun logout() {
        authRepository.logout()
        _faceEnrolled.value = false
        _pendingFaceVerification.value = null
        _currentUser.value = null
        _currentRole.value = ""
        _currentUserName.value = ""
        _currentUserUid.value = null
        _currentCompanyId.value = ""
        _error.value = null
        _authError.value = null
        _authSuccess.value = null
        _lastConfirmation.value = null
        _attendanceHistoryFlow.value = emptyList()
    }
}

class BioSecureViewModelFactory(
    private val authRepository: AuthRepository,
    private val attendanceRepository: AttendanceRepository,
    private val functionsRepository: FirebaseFunctionsRepository,
    private val faceRecognitionRepository: FaceRecognitionRepository,
    private val themePreferences: ThemePreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BioSecureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BioSecureViewModel(
                authRepository,
                attendanceRepository,
                functionsRepository,
                faceRecognitionRepository,
                themePreferences
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
