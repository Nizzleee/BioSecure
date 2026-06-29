package com.biosecure.app.data.repository

import com.biosecure.app.data.model.Shift
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CompanyRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getCompany(companyId: String): Map<String, Any>? {
        return try {
            db.collection("companies").document(companyId).get().await().data
        } catch (e: Exception) { null }
    }

    suspend fun createCompany(companyId: String, name: String, checkInStart: String, checkInEnd: String) {
        db.collection("companies").document(companyId).set(
            mapOf("name" to name, "checkInStart" to checkInStart, "checkInEnd" to checkInEnd)
        ).await()
    }

    suspend fun getShifts(companyId: String): List<Shift> {
        return try {
            val doc = db.collection("companies").document(companyId).get().await()
            @Suppress("UNCHECKED_CAST")
            val rawShifts = doc.get("shifts") as? List<Map<String, Any>> ?: emptyList()
            rawShifts.map { map ->
                Shift(
                    id = map["id"] as? String ?: "",
                    name = map["name"] as? String ?: "",
                    startTime = (map["startTime"] as? String) ?: (map["checkInStart"] as? String) ?: "",
                    toleranceMin = (map["toleranceMin"] as? Long)?.toInt() ?: 15,
                    endTime = (map["endTime"] as? String) ?: (map["checkInEnd"] as? String) ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun saveShifts(companyId: String, shifts: List<Shift>) {
        val shiftsData = shifts.map { shift ->
            mapOf(
                "name" to shift.name,
                "startTime" to shift.startTime,
                "checkInStart" to shift.startTime,
                "toleranceMin" to shift.toleranceMin,
                "endTime" to shift.endTime,
                "checkInEnd" to shift.endTime
            )
        }
        db.collection("companies").document(companyId)
            .update("shifts", shiftsData).await()
    }
}
