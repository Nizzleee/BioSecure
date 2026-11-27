package com.biosecure.app.data.repository

import android.content.Context
import android.net.Uri
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.Company
import com.biosecure.app.data.model.ScanType
import com.biosecure.app.data.model.Sede
import com.biosecure.app.data.model.Shift
import com.biosecure.app.data.network.AttendanceDto
import com.biosecure.app.data.network.RegisterAttendanceRequest
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.data.prefs.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Repositorio de datos 100% MongoDB (sin Firestore). */
class AttendanceRepository(context: Context) {
    private val session = SessionStore(context)
    private val api = RetrofitInstance.mongoApi
    private fun auth() = session.authHeader()
    private fun today() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    private fun AttendanceDto.toAttendance() = Attendance(
        id = docId,
        userId = userId ?: uid ?: "",
        companyId = companyId ?: "",
        sedeId = sedeId ?: "",
        userName = userName ?: "Empleado",
        date = date ?: "",
        checkIn = checkIn ?: "",
        checkOut = checkOut ?: "",
        status = AttendanceStatus.fromString(status),
        type = ScanType.fromString(type),
        location = location ?: "Sede",
        latitude = latitude,
        longitude = longitude
    )

    private fun dateSortKey(date: String): String {
        val p = date.split("/")
        return if (p.size == 3) "${p[2]}${p[1].padStart(2, '0')}${p[0].padStart(2, '0')}" else date
    }

    /** Poll ligero al backend; reemplaza al listener en tiempo real de Firestore. */
    private fun pollAttendances(companyId: String? = null, uid: String? = null): Flow<List<Attendance>> = flow {
        while (true) {
            val items = try {
                val r = api.getAttendances(auth(), companyId, uid)
                (r.data ?: emptyList()).map { it.toAttendance() }
            } catch (e: Exception) { emptyList() }
            emit(items)
            delay(4000)
        }
    }

    fun observeTodayAttendances(companyId: String): Flow<List<Attendance>> = flow {
        while (true) {
            val items = try {
                val r = api.getAttendances(auth(), companyId = companyId)
                (r.data ?: emptyList()).map { it.toAttendance() }
                    .filter { it.date == today() }
                    .sortedByDescending { it.checkIn }
            } catch (e: Exception) { emptyList() }
            emit(items)
            delay(4000)
        }
    }

    suspend fun saveAttendance(attendance: Attendance): Result<String> {
        return try {
            val r = api.registerAttendance(auth(), RegisterAttendanceRequest(
                tipo = attendance.type.name,
                qrToken = attendance.userId,
                lat = attendance.latitude ?: 0.0,
                lng = attendance.longitude ?: 0.0,
                requestId = UUID.randomUUID().toString()
            ))
            if (r.ok) Result.success(attendance.userId) else Result.failure(Exception(r.error ?: "Error al guardar"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getAttendances(): List<Map<String, Any>> = rawAttendances()

    suspend fun getAttendancesByUser(uid: String): List<Map<String, Any>> = rawAttendances(uid = uid)

    suspend fun getAttendanceByUser(uid: String): List<Map<String, Any>> = rawAttendances(uid = uid)

    suspend fun getAllAttendances(): List<Map<String, Any>> = rawAttendances()

    private suspend fun rawAttendances(companyId: String? = null, uid: String? = null): List<Map<String, Any>> {
        return try {
            val r = api.getAttendances(auth(), companyId, uid)
            (r.data ?: emptyList()).map { d ->
                mapOf(
                    "id" to d.docId, "userId" to (d.userId ?: ""), "companyId" to (d.companyId ?: ""),
                    "userName" to (d.userName ?: ""), "date" to (d.date ?: ""), "checkIn" to (d.checkIn ?: ""),
                    "status" to (d.status ?: ""), "type" to (d.type ?: "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun observeAllAttendances(companyId: String): Flow<List<Attendance>> = flow {
        while (true) {
            val items = try {
                val r = api.getAttendances(auth(), companyId = companyId)
                (r.data ?: emptyList()).map { it.toAttendance() }
                    .sortedWith(compareByDescending<Attendance> { dateSortKey(it.date) }.thenByDescending { it.checkIn })
            } catch (e: Exception) { emptyList() }
            emit(items)
            delay(4000)
        }
    }

    fun observeUserAttendances(uid: String): Flow<List<Attendance>> = flow {
        while (true) {
            val items = try {
                val r = api.getAttendances(auth(), uid = uid)
                (r.data ?: emptyList()).map { it.toAttendance() }
                    .sortedWith(compareByDescending<Attendance> { dateSortKey(it.date) }.thenByDescending { it.checkIn })
            } catch (e: Exception) { emptyList() }
            emit(items)
            delay(4000)
        }
    }

    suspend fun getEmployees(): List<Map<String, Any>> {
        return try {
            val r = api.getUsers(auth(), companyId = session.companyId, role = "employee")
            (r.data ?: emptyList()).map { u ->
                mapOf(
                    "uid" to u.uid, "name" to u.name, "email" to u.email, "role" to u.role,
                    "companyId" to u.companyId, "shiftId" to u.shiftId, "sedeId" to u.sedeId,
                    "firstName" to u.firstName, "lastName" to u.lastName, "department" to u.department,
                    "cargo" to u.cargo, "isActive" to u.isActive, "image" to u.image
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteEmployee(uid: String): Boolean = try {
        api.deleteUser(auth(), uid).ok
    } catch (e: Exception) { false }

    suspend fun updateEmployee(uid: String, firstName: String, lastName: String, email: String, cargo: String = "", department: String = ""): Boolean = try {
        api.updateUser(auth(), uid, mapOf(
            "firstName" to firstName, "lastName" to lastName, "name" to "$firstName $lastName",
            "email" to email, "cargo" to cargo, "department" to department
        )).ok
    } catch (e: Exception) { false }

    suspend fun updateUserName(uid: String, name: String): Boolean = try {
        api.updateUser(auth(), uid, mapOf("name" to name)).ok
    } catch (e: Exception) { false }

    suspend fun updateEmployeeStatus(uid: String, isActive: Boolean): Boolean = try {
        api.updateUser(auth(), uid, mapOf("isActive" to isActive)).ok
    } catch (e: Exception) { false }

    suspend fun updateUserPhoto(uid: String, imageUrl: String): Boolean = try {
        api.updateUser(auth(), uid, mapOf("image" to imageUrl)).ok
    } catch (e: Exception) { false }

    /** Firebase Storage ya no se usa; se deja el contrato para no romper la UI. */
    suspend fun uploadUserPhoto(uid: String, uri: Uri): String? = null

    suspend fun assignShiftToEmployee(uid: String, shiftId: String): Boolean = try {
        api.updateUser(auth(), uid, mapOf("shiftId" to shiftId)).ok
    } catch (e: Exception) { false }

    suspend fun getShifts(companyId: String): List<Shift> {
        return try {
            val r = api.getCompany(auth(), companyId)
            (r.data?.shifts ?: emptyList()).map { s ->
                Shift(
                    id = s.id ?: s._id ?: "", name = s.name ?: "",
                    startTime = s.startTime ?: "08:00", endTime = s.endTime ?: "17:00",
                    toleranceMin = s.toleranceMin ?: 15
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun saveShift(companyId: String, shift: Shift): Boolean {
        return try {
            val current = getShifts(companyId).toMutableList()
            val idx = current.indexOfFirst { it.id == shift.id }
            if (idx >= 0) current[idx] = shift else current.add(shift)
            api.saveShifts(auth(), companyId, mapOf("shifts" to current.map {
                mapOf("id" to it.id, "name" to it.name, "startTime" to it.startTime,
                    "endTime" to it.endTime, "toleranceMin" to it.toleranceMin)
            })).ok
        } catch (e: Exception) { false }
    }

    suspend fun getSedes(companyId: String): List<Sede> {
        return try {
            val r = api.getSedes(auth(), companyId)
            (r.data ?: emptyList()).filter { it.activa != false }.map { s ->
                Sede(
                    id = s.docId, nombre = s.nombre ?: "", lat = s.lat ?: 0.0, lng = s.lng ?: 0.0,
                    radioMetros = s.radioMetros ?: 100, activa = s.activa != false
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun saveSede(companyId: String, sede: Sede): Boolean = try {
        api.saveSede(auth(), mapOf(
            "id" to (sede.id.ifBlank { UUID.randomUUID().toString() }),
            "companyId" to companyId, "nombre" to sede.nombre, "lat" to sede.lat, "lng" to sede.lng,
            "radioMetros" to sede.radioMetros, "activa" to sede.activa
        )).ok
    } catch (e: Exception) { false }

    suspend fun assignSedeToEmployee(uid: String, sedeId: String): Boolean = try {
        api.updateUser(auth(), uid, mapOf("sedeId" to sedeId)).ok
    } catch (e: Exception) { false }

    suspend fun getCompanyConfig(companyId: String): Company? = try {
        val c = api.getCompany(auth(), companyId).data ?: return null
        Company(
            id = companyId, name = c.name ?: companyId,
            horaEntrada = c.horaEntrada ?: "08:00", toleranciaMin = c.toleranciaMin ?: 15,
            timezone = c.timezone ?: "UTC-5"
        )
    } catch (e: Exception) { null }

    suspend fun updateCompanyConfig(companyId: String, horaEntrada: String, toleranciaMin: Int): Boolean = try {
        api.updateCompany(auth(), companyId, mapOf("horaEntrada" to horaEntrada, "toleranciaMin" to toleranciaMin)).ok
    } catch (e: Exception) { false }
}
