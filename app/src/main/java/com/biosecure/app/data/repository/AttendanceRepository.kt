package com.biosecure.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Repositorio de datos 100% MongoDB (sin Firestore). */
class AttendanceRepository(context: Context) {
    private val appContext = context.applicationContext
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

    /**
     * Reemplaza a Firebase Storage: comprime la imagen y la guarda como data URI
     * en users.image (MongoDB). Así sobrevive al reinicio y funciona sin Storage.
     */
    suspend fun uploadUserPhoto(uid: String, uri: Uri): String? {
        return try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input) ?: return null
            input.close()
            val maxSide = 640
            val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height))
            val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(
                original, (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1), true
            ) else original
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length > 1_500_000) null else "data:image/jpeg;base64,$encoded"
        } catch (e: Exception) { null }
    }

    suspend fun assignShiftToEmployee(uid: String, shiftId: String): Boolean = try {
        api.updateUser(auth(), uid, mapOf("shiftId" to shiftId)).ok
    } catch (e: Exception) { false }

    suspend fun getShifts(companyId: String): List<Shift> {
        return try {
            val r = api.getShifts(auth(), companyId)
            val fromApi = (r.data ?: emptyList()).map { s ->
                Shift(
                    id = s.id ?: s._id ?: "", name = s.name ?: "",
                    startTime = s.startTime ?: "08:00", endTime = s.endTime ?: "17:00",
                    toleranceMin = s.toleranceMin ?: 15
                )
            }
            // Compatibilidad con empresas que aún tienen los turnos dentro del documento.
            if (fromApi.isNotEmpty()) fromApi else {
                val c = api.getCompany(auth(), companyId).data
                (c?.shifts ?: emptyList()).map { s ->
                    Shift(id = s.id ?: s._id ?: "", name = s.name ?: "",
                        startTime = s.startTime ?: "08:00", endTime = s.endTime ?: "17:00",
                        toleranceMin = s.toleranceMin ?: 15)
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun saveShift(companyId: String, shift: Shift): Boolean {
        return try {
            val current = getShifts(companyId).toMutableList()
            val fixed = if (shift.id.isBlank()) shift.copy(id = UUID.randomUUID().toString()) else shift
            val idx = current.indexOfFirst { it.id == fixed.id }
            if (idx >= 0) current[idx] = fixed else current.add(fixed)
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

    suspend fun saveSede(companyId: String, sede: Sede): Result<Unit> = try {
        val response = api.saveSede(auth(), mapOf(
            "id" to (sede.id.ifBlank { UUID.randomUUID().toString() }),
            "companyId" to companyId, "nombre" to sede.nombre, "lat" to sede.lat, "lng" to sede.lng,
            "radioMetros" to sede.radioMetros, "activa" to sede.activa
        ))
        if (response.ok && response.data != null) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(response.error ?: "El servidor no confirmó la sede"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("No se pudo conectar con el servidor: ${e.message ?: "error desconocido"}"))
    }

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
