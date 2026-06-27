package com.biosecure.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class FirebaseFunctionsRepository {
    private val functions = FirebaseFunctions.getInstance()

    suspend fun setUserClaims(uid: String, role: String, companyId: String): Result<Boolean> {
        return try {
            val data = hashMapOf(
                "uid" to uid,
                "role" to role,
                "companyId" to companyId
            )
            functions.getHttpsCallable("setUserClaims").call(data).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun issueCheckInToken(): Result<String> {
        return try {
            val result = functions.getHttpsCallable("issueCheckInToken").call().await()
            val token = (result.data as Map<*, *>)["token"] as String
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerAttendance(qrToken: String, lat: Double, lng: Double, sedeId: String?): Result<Map<String, Any>> {
        return try {
            val data = hashMapOf(
                "qrToken" to qrToken,
                "sedeId" to (sedeId ?: ""),
                "location" to hashMapOf(
                    "lat" to lat,
                    "lng" to lng
                )
            )
            val result = functions.getHttpsCallable("registerAttendance").call(data).await()
            @Suppress("UNCHECKED_CAST")
            Result.success(result.data as Map<String, Any>)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeTrends(companyId: String, history: List<Map<String, Any>>): Result<String> {
        return try {
            val data = hashMapOf(
                "companyId" to companyId,
                "history" to history.take(20)
            )
            val result = functions.getHttpsCallable("analyzeTrends").call(data).await()
            val analysis = (result.data as Map<*, *>)["analysis"] as String
            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
