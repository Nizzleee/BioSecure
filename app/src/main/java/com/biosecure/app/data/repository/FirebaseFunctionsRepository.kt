package com.biosecure.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.IOException

data class AttendanceResponseData(
    val type: String,
    val status: String,
    val checkIn: String,
    val checkOut: String? = null,
    val date: String,
    val userName: String
)

sealed class AttendanceResult {
    data class Success(val data: AttendanceResponseData) : AttendanceResult()
    /** [score] solo viene informado en FACE_MISMATCH (similitud 0..1). */
    data class Error(val code: String, val score: Double? = null) : AttendanceResult()
}

class FirebaseFunctionsRepository {
    private val functions = FirebaseFunctions.getInstance("us-central1")

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
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun registerAttendance(
        tipo: String, // "HUELLA" | "FACIAL" | "QR"
        lat: Double,
        lng: Double,
        accuracy: Float,
        isMock: Boolean,
        requestId: String,
        qrToken: String? = null,
        faceEmbedding: List<Double>? = null,
        faceEmbeddingModel: String? = null
    ): AttendanceResult {
        val payload = mutableMapOf<String, Any>(
            "tipo" to tipo,
            "lat" to lat,
            "lng" to lng,
            "accuracy" to accuracy.toDouble(),
            "isMock" to isMock,
            "requestId" to requestId
        )
        if (!qrToken.isNullOrBlank()) {
            payload["qrToken"] = qrToken
        }
        if (faceEmbedding != null && faceEmbeddingModel != null) {
            payload["faceEmbedding"] = faceEmbedding
            payload["faceEmbeddingModel"] = faceEmbeddingModel
        }

        var attempt = 0
        val maxAttempts = 3
        var backoffMs = 500L

        while (attempt < maxAttempts) {
            attempt++
            try {
                val callableResult = functions.getHttpsCallable("registerAttendance").call(payload).await()
                val resultMap = callableResult.data as? Map<*, *> ?: return AttendanceResult.Error("CORRUPT_CHECKIN_DATA")

                val response = AttendanceResponseData(
                    type = resultMap["type"] as? String ?: "",
                    status = resultMap["status"] as? String ?: "",
                    checkIn = resultMap["checkIn"] as? String ?: "",
                    checkOut = resultMap["checkOut"] as? String,
                    date = resultMap["date"] as? String ?: "",
                    userName = resultMap["userName"] as? String ?: ""
                )
                return AttendanceResult.Success(response)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                var score: Double? = null
                val code = if (e is FirebaseFunctionsException) {
                    val detailsMap = e.details as? Map<*, *>
                    score = (detailsMap?.get("score") as? Number)?.toDouble()
                    detailsMap?.get("code") as? String ?: e.code.name
                } else if (e is IOException) {
                    "NO_NETWORK"
                } else {
                    "INTERNAL_ERROR"
                }

                if ((code == "RETRY" || code == "UNAVAILABLE" || code == "NO_NETWORK") && attempt < maxAttempts) {
                    delay(backoffMs)
                    backoffMs *= 2
                    continue
                }

                return AttendanceResult.Error(code, score)
            }
        }
        return AttendanceResult.Error("RETRY")
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
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
