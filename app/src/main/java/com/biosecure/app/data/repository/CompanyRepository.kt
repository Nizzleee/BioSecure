package com.biosecure.app.data.repository

import android.content.Context
import com.biosecure.app.data.model.Shift
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.data.prefs.SessionStore

/** Empresa y turnos en MongoDB (antes Firestore). */
class CompanyRepository(context: Context) {
    private val session = SessionStore(context)
    private val api = RetrofitInstance.mongoApi

    suspend fun getCompany(companyId: String): Map<String, Any>? = try {
        val c = api.getCompany(session.authHeader(), companyId).data ?: return null
        mapOf(
            "name" to (c.name ?: ""), "horaEntrada" to (c.horaEntrada ?: "08:00"),
            "toleranciaMin" to (c.toleranciaMin ?: 15), "timezone" to (c.timezone ?: "UTC-5")
        )
    } catch (e: Exception) { null }

    suspend fun createCompany(companyId: String, name: String, checkInStart: String, checkInEnd: String) {
        api.updateCompany(session.authHeader(), companyId, mapOf(
            "name" to name, "horaEntrada" to checkInStart, "timezone" to "UTC-5"
        ))
    }

    suspend fun getShifts(companyId: String): List<Shift> = try {
        (api.getCompany(session.authHeader(), companyId).data?.shifts ?: emptyList()).map { s ->
            Shift(
                id = s.id ?: s._id ?: "", name = s.name ?: "",
                startTime = s.startTime ?: "08:00", endTime = s.endTime ?: "17:00",
                toleranceMin = s.toleranceMin ?: 15
            )
        }
    } catch (e: Exception) { emptyList() }

    suspend fun saveShifts(companyId: String, shifts: List<Shift>) {
        api.saveShifts(session.authHeader(), companyId, mapOf("shifts" to shifts.map {
            mapOf("id" to it.id, "name" to it.name, "startTime" to it.startTime,
                "endTime" to it.endTime, "toleranceMin" to it.toleranceMin)
        }))
    }
}
