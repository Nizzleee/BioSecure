package com.biosecure.app.data.repository

import android.content.Context
import com.biosecure.app.data.network.RegisterAttendanceRequest
import com.biosecure.app.data.network.RegisterAttendanceResult
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.data.prefs.SessionStore
import java.util.UUID

/** Reemplaza a las Firebase Functions using la API MongoDB. */
class FunctionsRepository(context: Context) {
    private val session = SessionStore(context)
    private val api = RetrofitInstance.mongoApi

    suspend fun setUserClaims(uid: String, role: String, companyId: String): Result<Boolean> = try {
        val r = api.updateUser(session.authHeader(), uid, mapOf("role" to role, "companyId" to companyId))
        Result.success(r.ok)
    } catch (e: Exception) { Result.failure(e) }

    /** En Mongo el QR contiene directamente el uid del empleado. */
    suspend fun issueCheckInToken(): Result<String> = Result.success(session.uid ?: "")

    suspend fun registerAttendance(qrToken: String, lat: Double, lng: Double, sedeId: String?): Result<Map<String, Any>> {
        return try {
            val r = api.registerAttendance(session.authHeader(), RegisterAttendanceRequest(
                tipo = if (qrToken == session.uid) "HUELLA" else "QR",
                qrToken = qrToken,
                lat = lat, lng = lng,
                accuracy = 50.0,
                requestId = UUID.randomUUID().toString()
            ))
            if (!r.ok || r.data == null) return Result.failure(Exception(r.error ?: "Error al registrar"))
            val d: RegisterAttendanceResult = r.data!!
            Result.success(mapOf(
                "status" to d.status, "checkIn" to d.checkIn, "checkOut" to d.checkOut,
                "date" to d.date, "userName" to d.userName, "type" to d.type
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
            m.contains("400") -> "Error: verifica ubicación, turno y horario"
            m.contains("403") -> "Sin permisos para esta operación"
            m.contains("Failed to connect") || m.contains("refused") || m.contains("timeout") ->
                "No se pudo conectar con el servidor MongoDB"
            else -> m.ifBlank { "Error de conexión" }
        }
    }
}
