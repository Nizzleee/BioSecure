package com.biosecure.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.CompanyRequest
import com.biosecure.app.data.model.CreateUserRequest
import com.biosecure.app.data.model.ScanType
import com.biosecure.app.data.model.UpdateUserRequest
import com.biosecure.app.data.model.User
import com.biosecure.app.data.network.AttendanceRequest
import com.biosecure.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BioSecureViewModel(private val userRepository: UserRepository) : ViewModel() {

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastCreatedUser = MutableStateFlow<User?>(null)
    val lastCreatedUser: StateFlow<User?> = _lastCreatedUser.asStateFlow()

    private val _lastUpdatedUserId = MutableStateFlow<Int?>(null)
    val lastUpdatedUserId: StateFlow<Int?> = _lastUpdatedUserId.asStateFlow()

    private var nextAttendanceId = 1

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _users.value = userRepository.getUsers()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar usuarios"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loginAsAdmin() {
        _currentRole.value = "admin"
        _currentUser.value = null
    }

    fun loginAsEmployee(userId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val user = userRepository.getUserById(userId)
                _currentUser.value = user
                _currentRole.value = "employee"
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al iniciar sesión"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerAttendance(scanType: ScanType) {
        val user = _currentUser.value ?: return
        val now = Date()
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val request = AttendanceRequest(
            userId = user.id,
            userName = "${user.firstName} ${user.lastName}",
            checkIn = timeFormatter.format(now),
            scanType = scanType.name,
            location = "Sede Central"
        )

        // Actualizar estado local inmediatamente para que ConfirmationScreen lo lea al navegar
        _lastAttendance.value = request
        _attendances.value = _attendances.value + Attendance(
            id = nextAttendanceId++,
            userId = user.id,
            userName = "${user.firstName} ${user.lastName}",
            userImage = user.image,
            date = dateFormatter.format(now),
            checkIn = timeFormatter.format(now),
            checkOut = "",
            status = AttendanceStatus.EXITOSO,
            type = scanType
        )

        // POST en background — fire-and-forget
        viewModelScope.launch {
            try {
                userRepository.registerAttendance(request)
            } catch (e: Exception) {
                _error.value = "Error al sincronizar asistencia: ${e.message}"
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
                val request = CreateUserRequest(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    company = CompanyRequest(name = "BioSecure", title = title, department = department)
                )
                val user = userRepository.createUser(request)
                if (user != null) {
                    _lastCreatedUser.value = user
                    _users.value = _users.value + user
                } else {
                    _error.value = "Error al registrar empleado"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al registrar empleado"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateEmployee(id: Int, firstName: String, lastName: String, email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val request = UpdateUserRequest(firstName, lastName, email)
                val updated = userRepository.updateUser(id, request)
                if (updated != null) {
                    _users.value = _users.value.map { if (it.id == id) updated else it }
                    _lastUpdatedUserId.value = id
                } else {
                    _error.value = "Error al actualizar empleado"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al actualizar empleado"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEmployee(id: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val deleted = userRepository.deleteUser(id)
                if (deleted) {
                    _users.value = _users.value.filter { it.id != id }
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

    fun clearLastUpdatedUserId() { _lastUpdatedUserId.value = null }

    fun logout() {
        _currentUser.value = null
        _currentRole.value = "employee"
        _lastAttendance.value = null
        _error.value = null
    }
}

class BioSecureViewModelFactory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BioSecureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BioSecureViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
