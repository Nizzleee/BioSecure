package com.biosecure.app.data.repository

import android.content.Context
import com.biosecure.app.data.network.RegisterAttendanceRequest
import com.biosecure.app.data.network.RegisterAttendanceResult
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.data.prefs.SessionStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Reemplaza a las Firebase Functions usando la API MongoDB. */
class FunctionsRepository(context: Context) {
    private val session = SessionStore(context)
    private val api = RetrofitInstance.mongoApi
    private val pendingRequestIds = mutableMapOf<String, String>()

    suspend fun setUserClaims(uid: String, role: String, companyId: String): Result<Boolean> = try {
        val r = api.updateUser(session.authHeader(), uid, mapOf("role" to role, "companyId" to companyId))
        Result.success(r.ok)
    } catch (e: Exception) { Result.failure(e) }

    /** En Mongo el QR contiene directamente el uid del empleado. */
    suspend fun issueCheckInToken(): Result<String> = Result.success(session.uid ?: "")

    suspend fun registerAttendance(
        qrToken: String, lat: Double, lng: Double, sedeId: String?,
        accuracy: Double = 50.0, tipo: String? = null
    ): Result<Map<String, Any>> {
        return try {
            val effectiveType = tipo ?: if (qrToken == session.uid) "HUELLA" else "QR"
            // Un reintento por pérdida de red reutiliza el mismo requestId.
            val key = "$qrToken|${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}|${sedeId ?: ""}"
            val requestId = pendingRequestIds.getOrPut(key) { UUID.randomUUID().toString() }
            val r = api.registerAttendance(session.authHeader(), RegisterAttendanceRequest(
                tipo = effectiveType,
                qrToken = qrToken,
                lat = lat, lng = lng,
                accuracy = accuracy,
                requestId = requestId
            ))
            if (!r.ok || r.data == null) return Result.failure(Exception(r.error ?: "Error al registrar"))
            pendingRequestIds.remove(key)
            val d: RegisterAttendanceResult = r.data!!
            Result.success(mapOf(
                "status" to d.status, "checkIn" to d.checkIn, "checkOut" to d.checkOut,
                "date" to d.date, "userName" to d.userName, "type" to d.type,
                "scanType" to d.scanType.ifBlank { effectiveType }
            ))
        } catch (e: Exception) {
            Result.failure(Exception(serverError(e)))
        }
    }

    suspend fun createEmployee(
        email: String, password: String, firstName: String, lastName: String,
        department: String, cargo: String, companyId: String,
        sedeId: String, shiftId: String
    ): Result<String> {
        return try {
            val r = api.createUser(session.authHeader(), mapOf(
                "email" to email.trim(), "password" to password, "role" to "employee",
                "firstName" to firstName.trim(), "lastName" to lastName.trim(),
                "name" to "${firstName.trim()} ${lastName.trim()}",
                "department" to department.trim(), "cargo" to cargo.trim(),
                "companyId" to companyId, "sedeId" to sedeId, "shiftId" to shiftId,
                "isActive" to true
            ))
            if (!r.ok || r.data == null) Result.failure(Exception(r.error ?: "No se pudo crear el empleado"))
            else Result.success(r.data!!.uid)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun analyzeTrends(companyId: String, history: List<Map<String, Any>>): Result<String> =
        Result.success("Análisis disponible en el panel con Gemini.")

    private fun serverError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("ALREADY_COMPLETE") -> "Este empleado ya registró entrada y salida hoy"
            m.contains("TOO_SOON") -> "Deben transcurrir 5 minutos desde la entrada"
            m.contains("NO_SHIFT_ASSIGNED") -> "El empleado no tiene turno asignado"
            m.contains("NO_SEDE_ASSIGNED") -> "El empleado no tiene sede asignada"
            m.contains("OUT_OF_RANGE_GEOFENCE") -> "Fuera de rango: acércate a la sede"
            m.contains("OUT_OF_WINDOW") -> "Fuera del horario permitido del turno"
            m.contains("ACCURACY_TOO_HIGH") -> "Señal GPS imprecisa; espera mejor ubicación"
            m.contains("ADMIN_CANNOT_MARK_SELF") -> "Un administrador no puede marcar su propia asistencia"
            m.contains("TARGET_NOT_EMPLOYEE") -> "El QR no corresponde a un empleado"
            m.contains("NOT_FOUND") -> "Empleado no encontrado"
            m.contains("Failed to connect") || m.contains("refused") || m.contains("timeout") ->
                "No se pudo conectar con el servidor MongoDB"
            m.contains("401") || m.contains("403") -> "Sin permisos para esta operación"
            else -> m.ifBlank { "Error de conexión" }
        }
    }
}
