package com.biosecure.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.Company
import com.biosecure.app.data.model.GeoFence
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceRepository {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("attendances")

    /**
     * Observa las asistencias de hoy en tiempo real para una empresa específica.
     * Requiere el siguiente índice compuesto en Firestore:
     * Colección: attendances
     * Campos:
     *   1. companyId (Ascending)
     *   2. date (Ascending)
     *   3. checkIn (Descending)
     */
    fun observeTodayAttendances(companyId: String): Flow<List<Attendance>> = callbackFlow {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val query = collection
            .whereEqualTo("companyId", companyId)
            .whereEqualTo("date", today)
            .orderBy("checkIn", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val items = snapshot?.documents?.mapNotNull { doc ->
                try {
                    Attendance(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        companyId = doc.getString("companyId") ?: "",
                        sedeId = doc.getString("sedeId") ?: "",
                        userName = doc.getString("userName") ?: "Empleado",
                        date = doc.getString("date") ?: "",
                        checkIn = doc.getString("checkIn") ?: "",
                        status = AttendanceStatus.fromString(doc.getString("status")),
                        type = com.biosecure.app.data.model.ScanType.fromString(doc.getString("type")),
                        location = doc.getString("location") ?: doc.getString("locationName") ?: doc.getString("sedeName") ?: "Sede"
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    suspend fun saveAttendance(attendance: Attendance): Result<String> {
        return try {
            val data = hashMapOf(
                "userId" to attendance.userId,
                "companyId" to attendance.companyId,
                "sedeId" to attendance.sedeId,
                "userName" to attendance.userName,
                "date" to attendance.date,
                "checkIn" to attendance.checkIn,
                "status" to attendance.status.name,
                "type" to attendance.type.name,
                "location" to attendance.location,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            val ref = collection.add(data).await()

            try {
                val notif = hashMapOf(
                    "type" to "attendance",
                    "userName" to attendance.userName,
                    "checkIn" to attendance.checkIn,
                    "timestamp" to Timestamp.now(),
                    "read" to false
                )
                db.collection("notifications").add(notif).await()
            } catch (_: Exception) {}

            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttendances(): List<Map<String, Any>> {
        return try {
            collection.get().await().documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAttendancesByUser(uid: String): List<Map<String, Any>> {
        return try {
            collection.whereEqualTo("userId", uid).get().await()
                .documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAttendanceByUser(uid: String): List<Map<String, Any>> {
        return try {
            db.collection("attendances")
                .whereEqualTo("userId", uid)
                .orderBy("checkIn", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllAttendances(): List<Map<String, Any>> {
        return try {
            db.collection("attendances")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .map { it.data?.plus("id" to it.id) ?: emptyMap() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun observeAllAttendances(companyId: String): Flow<List<Attendance>> = callbackFlow {
        val query = collection
            .whereEqualTo("companyId", companyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val items = snapshot?.documents?.mapNotNull { doc ->
                try {
                    Attendance(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        companyId = doc.getString("companyId") ?: "",
                        sedeId = doc.getString("sedeId") ?: "",
                        userName = doc.getString("userName") ?: "Empleado",
                        date = doc.getString("date") ?: "",
                        checkIn = doc.getString("checkIn") ?: "",
                        status = AttendanceStatus.fromString(doc.getString("status")),
                        type = com.biosecure.app.data.model.ScanType.fromString(doc.getString("type")),
                        location = doc.getString("location") ?: doc.getString("locationName") ?: doc.getString("sedeName") ?: "Sede"
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    fun observeUserAttendances(uid: String): Flow<List<Attendance>> = callbackFlow {
        val query = collection
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val items = snapshot?.documents?.mapNotNull { doc ->
                try {
                    Attendance(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        companyId = doc.getString("companyId") ?: "",
                        sedeId = doc.getString("sedeId") ?: "",
                        userName = doc.getString("userName") ?: "Empleado",
                        date = doc.getString("date") ?: "",
                        checkIn = doc.getString("checkIn") ?: "",
                        status = AttendanceStatus.fromString(doc.getString("status")),
                        type = com.biosecure.app.data.model.ScanType.fromString(doc.getString("type")),
                        location = doc.getString("location") ?: doc.getString("locationName") ?: doc.getString("sedeName") ?: "Sede"
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    suspend fun getEmployees(): List<Map<String, Any>> {
        return db.collection("users")
            .whereEqualTo("role", "employee")
            .get()
            .await()
            .documents
            .map { doc -> doc.data?.plus("uid" to doc.id) ?: emptyMap() }
    }

    suspend fun deleteEmployee(uid: String): Boolean {
        return try {
            db.collection("users").document(uid).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateEmployee(uid: String, firstName: String, lastName: String, email: String, cargo: String = "", department: String = ""): Boolean {
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "name" to "$firstName $lastName",
                    "email" to email,
                    "cargo" to cargo,
                    "department" to department
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateEmployeeStatus(uid: String, isActive: Boolean): Boolean {
        return try {
            db.collection("users").document(uid).update("isActive", isActive).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateUserPhoto(uid: String, imageUrl: String): Boolean {
        return try {
            db.collection("users").document(uid).update("image", imageUrl).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadUserPhoto(uid: String, uri: android.net.Uri): String? {
        return try {
            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
            val photoRef = storageRef.child("profile_photos/$uid.jpg")
            photoRef.putFile(uri).await()
            photoRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun assignShiftToEmployee(uid: String, shiftId: String): Boolean {
        return try {
            db.collection("users").document(uid).update("shiftId", shiftId).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getShifts(companyId: String): List<com.biosecure.app.data.model.Shift> {
        return try {
            db.collection("companies").document(companyId).collection("shifts")
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    com.biosecure.app.data.model.Shift(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        startTime = doc.getString("startTime") ?: "08:00",
                        toleranceMin = doc.getLong("toleranceMin")?.toInt() ?: 15,
                        endTime = doc.getString("endTime") ?: "17:00"
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveShift(companyId: String, shift: com.biosecure.app.data.model.Shift): Boolean {
        return try {
            val data = hashMapOf(
                "name" to shift.name,
                "startTime" to shift.startTime,
                "toleranceMin" to shift.toleranceMin,
                "endTime" to shift.endTime
            )
            if (shift.id.isEmpty()) {
                db.collection("companies").document(companyId).collection("shifts").add(data).await()
            } else {
                db.collection("companies").document(companyId).collection("shifts").document(shift.id).set(data).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSedes(companyId: String): List<com.biosecure.app.data.model.Sede> {
        return try {
            db.collection("companies").document(companyId).collection("sedes")
                .whereEqualTo("activa", true)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    com.biosecure.app.data.model.Sede(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "",
                        lat = doc.getDouble("lat") ?: 0.0,
                        lng = doc.getDouble("lng") ?: 0.0,
                        radioMetros = doc.getLong("radioMetros")?.toInt() ?: 100,
                        activa = doc.getBoolean("activa") ?: true
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveSede(companyId: String, sede: com.biosecure.app.data.model.Sede): Boolean {
        return try {
            val data = hashMapOf(
                "nombre" to sede.nombre,
                "lat" to sede.lat,
                "lng" to sede.lng,
                "radioMetros" to sede.radioMetros,
                "activa" to sede.activa,
                "creadaEn" to com.google.firebase.Timestamp.now()
            )
            if (sede.id.isEmpty()) {
                db.collection("companies").document(companyId).collection("sedes").add(data).await()
            } else {
                db.collection("companies").document(companyId).collection("sedes").document(sede.id).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun assignSedeToEmployee(uid: String, sedeId: String): Boolean {
        return try {
            db.collection("users").document(uid).update("sedeId", sedeId).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getCompanyConfig(companyId: String): Company? {
        return try {
            val doc = db.collection("companies").document(companyId).get().await()
            if (doc.exists()) {
                Company(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    horaEntrada = doc.getString("horaEntrada") ?: "08:00",
                    toleranciaMin = doc.getLong("toleranciaMin")?.toInt() ?: 15,
                    timezone = doc.getString("timezone") ?: "UTC-5"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateCompanyConfig(companyId: String, horaEntrada: String, toleranciaMin: Int): Boolean {
        return try {
            db.collection("companies").document(companyId).update(
                mapOf(
                    "horaEntrada" to horaEntrada,
                    "toleranciaMin" to toleranciaMin
                )
            ).await()
            true
        } catch (e: Exception) {
            try {
                db.collection("companies").document(companyId).set(
                    mapOf(
                        "name" to companyId,
                        "horaEntrada" to horaEntrada,
                        "toleranciaMin" to toleranciaMin,
                        "timezone" to "UTC-5"
                    )
                ).await()
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
