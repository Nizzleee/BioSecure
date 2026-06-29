package com.biosecure.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.Company
import com.biosecure.app.data.model.ConfirmationData
import com.biosecure.app.data.model.CompanyRequest
import com.biosecure.app.data.model.CreateUserRequest
import com.biosecure.app.data.model.ScanType
import com.biosecure.app.data.model.UpdateUserRequest
import com.biosecure.app.data.model.User
import com.biosecure.app.data.network.AttendanceRequest
import com.biosecure.app.data.repository.AttendanceRepository
import com.biosecure.app.data.repository.AuthRepository
import com.biosecure.app.data.repository.FirebaseFunctionsRepository
import com.biosecure.app.data.prefs.ThemePreferences
import com.google.firebase.auth.FirebaseAuth
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
    private val themePreferences: ThemePreferences
) : ViewModel() {

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

    private val _lastAttendance = MutableStateFlow<AttendanceRequest?>(null)
    val lastAttendance: StateFlow<AttendanceRequest?> = _lastAttendance.asStateFlow()

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

    private val _firestoreEmployees = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val firestoreEmployees: StateFlow<List<Map<String, Any>>> = _firestoreEmployees.asStateFlow()

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    private val _selectedSedeId = MutableStateFlow<String?>(null)
    val selectedSedeId: StateFlow<String?> = _selectedSedeId.asStateFlow()

    fun setSelectedSede(sedeId: String?) {
        _selectedSedeId.value = sedeId
        observeDashboard()
    }

    private val _companyConfig = MutableStateFlow<Company?>(null)
    val companyConfig: StateFlow<Company?> = _companyConfig.asStateFlow()

    private val _aiAnalysis = MutableStateFlow<String?>(null)
    val aiAnalysis: StateFlow<String?> = _aiAnalysis.asStateFlow()

    private val _shifts = MutableStateFlow<List<com.biosecure.app.data.model.Shift>>(emptyList())
    val shifts: StateFlow<List<com.biosecure.app.data.model.Shift>> = _shifts.asStateFlow()

    private val _sedes = MutableStateFlow<List<com.biosecure.app.data.model.Sede>>(emptyList())
    val sedes: StateFlow<List<com.biosecure.app.data.model.Sede>> = _sedes.asStateFlow()

    private var nextAttendanceId = 1

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

        viewModelScope.launch {
            _dashboardState.value = _dashboardState.value.copy(isLoading = true)
            try {
                attendanceRepository.observeTodayAttendances(companyId).collectLatest { allAttendances ->
                    val filteredAttendances = if (filterSedeId == null) {
                        allAttendances
                    } else {
                        val companyEmployees = attendanceRepository.getEmployees()
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

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val employees = attendanceRepository.getEmployees()
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
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar usuarios"
            } finally {
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

    fun saveSede(nombre: String, lat: Double, lng: Double, radio: Int) {
        val companyId = _currentCompanyId.value
        if (companyId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val sede = com.biosecure.app.data.model.Sede(nombre = nombre, lat = lat, lng = lng, radioMetros = radio)
            val success = attendanceRepository.saveSede(companyId, sede)
            if (success) {
                _sedes.value = attendanceRepository.getSedes(companyId)
            } else {
                _error.value = "Error al guardar la sede"
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

    fun saveShift(name: String, startTime: String, toleranceMin: Int, endTime: String) {
        val companyId = _currentCompanyId.value
        if (companyId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val shift = com.biosecure.app.data.model.Shift(name = name, startTime = startTime, toleranceMin = toleranceMin, endTime = endTime)
            val success = attendanceRepository.saveShift(companyId, shift)
            if (success) {
                _shifts.value = attendanceRepository.getShifts(companyId)
            } else {
                _error.value = "Error al guardar el turno"
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

    private val _qrToken = MutableStateFlow<String?>(null)
    val qrToken: StateFlow<String?> = _qrToken.asStateFlow()

    suspend fun issueCheckInToken(): Result<String> {
        return functionsRepository.issueCheckInToken().onSuccess {
            _qrToken.value = it
        }
    }

    fun registerAttendance(qrToken: String, lat: Double, lng: Double, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isLoading.value = true
            val currentSedeId = _selectedSedeId.value

            functionsRepository.registerAttendance(qrToken, lat, lng, currentSedeId)
                .onSuccess { resultData ->
                    _authSuccess.value = "Asistencia registrada: ${resultData["status"]}"

                    _lastConfirmation.value = ConfirmationData(
                        userName = resultData["userName"] as? String ?: "Empleado",
                        checkIn = resultData["checkIn"] as? String ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                        date = resultData["date"] as? String ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                        scanType = ScanType.QR.name,
                        status = resultData["status"] as? String ?: "TARDANZA"
                    )
                    onResult(true, null)
                }
                .onFailure { error ->
                    val errorMsg = error.message ?: ""
                    if (errorMsg.contains("NOT_FOUND") || !qrToken.contains(".")) {
                        val success = registerAttendanceDirect(qrToken, lat, lng, currentSedeId)
                        onResult(success, if (success) null else "Este empleado ya puso asistencia hoy")
                    } else {
                        val msg = when {
                            errorMsg.contains("ALREADY_EXISTS") -> "Este código QR ya fue utilizado."
                            errorMsg.contains("ALREADY_REGISTERED") -> "Este empleado ya puso asistencia hoy"
                            else -> "Error: Fuera de rango o tiempo"
                        }
                        _error.value = msg
                        onResult(false, msg)
                    }
                }
            _isLoading.value = false
        }
    }

    private suspend fun registerAttendanceDirect(uid: String, lat: Double, lng: Double, sedeId: String?): Boolean {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val knownName = _currentUserName.value.ifEmpty { "Empleado" }

        // Set confirmation data immediately so ConfirmationScreen always gets something
        _lastConfirmation.value = ConfirmationData(
            userName = knownName,
            checkIn = time,
            date = today,
            scanType = ScanType.QR.name,
            status = "TARDANZA"
        )

        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            // Use a deterministic doc ID: no composite index needed
            val docId = "${uid}_${today.replace("/", "-")}"
            val existingDoc = db.collection("attendances").document(docId).get().await()

            if (existingDoc.exists()) {
                _lastConfirmation.value = ConfirmationData(
                    userName = existingDoc.getString("userName") ?: knownName,
                    checkIn = existingDoc.getString("checkIn") ?: time,
                    date = today,
                    scanType = existingDoc.getString("type") ?: ScanType.QR.name,
                    status = existingDoc.getString("status") ?: "TARDANZA"
                )
                return true
            }

            val userDoc = db.collection("users").document(uid).get().await()
            val userName = userDoc.getString("name") ?: knownName
            val companyId = userDoc.getString("companyId") ?: _currentCompanyId.value
            val userShiftId = userDoc.getString("shiftId") ?: ""
            val attendanceStatus = determineAttendanceStatus(userShiftId)

            val data = hashMapOf(
                "userId" to uid,
                "companyId" to companyId,
                "sedeId" to (sedeId ?: userDoc.getString("sedeId") ?: ""),
                "userName" to userName,
                "date" to today,
                "checkIn" to time,
                "status" to attendanceStatus.name,
                "type" to ScanType.QR.name,
                "location" to (sedes.value.find { it.id == sedeId }?.nombre ?: "Sede"),
                "latitude" to lat,
                "longitude" to lng,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            db.collection("attendances").document(docId).set(data).await()

            _lastConfirmation.value = ConfirmationData(
                userName = userName,
                checkIn = time,
                date = today,
                scanType = ScanType.QR.name,
                status = attendanceStatus.name
            )
            _authSuccess.value = "Asistencia registrada correctamente"
        } catch (e: Exception) {
            _error.value = "Error al guardar: ${e.message}"
            // lastConfirmation already set above — user will still see QR
        }

        return true // Always navigate to ConfirmationScreen
    }

    private fun determineAttendanceStatus(shiftId: String): AttendanceStatus {
        if (shiftId.isBlank()) return AttendanceStatus.TARDANZA
        val shift = _shifts.value.find { it.id == shiftId } ?: return AttendanceStatus.TARDANZA

        val cal = java.util.Calendar.getInstance()
        val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        fun timeToMinutes(t: String): Int {
            val parts = t.split(":").map { it.toIntOrNull() ?: 0 }
            return (parts.getOrNull(0) ?: 0) * 60 + (parts.getOrNull(1) ?: 0)
        }

        val startMinutes = timeToMinutes(shift.startTime)
        val endMinutes = timeToMinutes(shift.endTime)

        return when {
            currentMinutes > endMinutes -> AttendanceStatus.FALLIDO
            currentMinutes < (startMinutes - 60) -> AttendanceStatus.FALLIDO
            else -> AttendanceStatus.TARDANZA
        }
    }

    fun registerManualAttendance(user: User) {
        val now = Date()
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val checkIn = timeFormatter.format(now)
        val date = dateFormatter.format(now)
        val userName = "${user.firstName} ${user.lastName}"
        val attendance = Attendance(
            id = (nextAttendanceId++).toString(),
            userId = user.uid,
            companyId = user.companyId,
            sedeId = user.sedeId,
            userName = userName,
            userImage = user.image,
            date = date,
            checkIn = checkIn,
            checkOut = "",
            status = AttendanceStatus.EXITOSO,
            type = ScanType.QR
        )
        _lastConfirmation.value = ConfirmationData(
            userName = userName,
            checkIn = checkIn,
            date = date,
            scanType = ScanType.QR.name
        )
        _attendances.value = _attendances.value + attendance
        viewModelScope.launch {
            try {
                attendanceRepository.saveAttendance(attendance)
            } catch (e: Exception) {
                _error.value = "Error al registrar asistencia: ${e.message}"
            }
        }
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

        viewModelScope.launch {
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
                _firestoreEmployees.value = attendanceRepository.getEmployees()
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

    fun getAiAnalysis() {
        val companyId = _currentCompanyId.value
        val history = _attendanceHistoryFlow.value
        if (companyId.isEmpty() || history.isEmpty()) return

        viewModelScope.launch {
            _aiAnalysis.value = "Claude está analizando los patrones de asistencia..."

            val historyMaps = history.take(20).map {
                mapOf(
                    "userName" to it.userName,
                    "date" to it.date,
                    "checkIn" to it.checkIn,
                    "status" to it.status.name,
                    "location" to it.location
                )
            }

            functionsRepository.analyzeTrends(companyId, historyMaps)
                .onSuccess { _aiAnalysis.value = it }
                .onFailure { _aiAnalysis.value = "Error al obtener análisis: ${it.message}" }
        }
    }

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

    fun logout() {
        authRepository.logout()
        _currentUser.value = null
        _currentRole.value = ""
        _currentUserName.value = ""
        _currentUserUid.value = null
        _currentCompanyId.value = ""
        _lastAttendance.value = null
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
    private val themePreferences: ThemePreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BioSecureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BioSecureViewModel(authRepository, attendanceRepository, functionsRepository, themePreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
