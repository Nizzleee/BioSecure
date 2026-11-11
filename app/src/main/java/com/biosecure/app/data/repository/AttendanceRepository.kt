package com.biosecure.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.biosecure.app.data.model.Attendance
import com.biosecure.app.data.model.AttendanceStatus
import com.biosecure.app.data.model.Company
import com.biosecure.app.data.model.GeoFence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Resultado de un marcaje escrito directamente por el cliente. */
sealed class AttendanceWriteResult {
    data class Success(
        val movement: String, // CHECK_IN | CHECK_OUT
        val status: String,
        val checkIn: String,
        val checkOut: String?,
        val date: String,
        val userName: String
    ) : AttendanceWriteResult()

    data class Error(val code: String) : AttendanceWriteResult()
}

class AttendanceRepository {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("attendances")

    private companion object {
        const val MAX_ACCURACY_METERS = 50.0
        const val EARLY_CHECKIN_MINUTES = 60
        const val MIN_MINUTES_BEFORE_CHECKOUT = 5
        const val MAX_CHECKOUT_OVERTIME_MINUTES = 120

        val REQUEST_ID_REGEX = Regex("^[A-Za-z0-9-]{8,64}$")
        val TIME_REGEX = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
    }

    /**
     * Registra un marcaje propio (FACIAL/HUELLA) directamente en Firestore,
     * sin Cloud Functions. Es la sustitución local de `registerAttendance`.
     *
     * Replica el schema que escribía el servidor:
     * userId, companyId, sedeId, userName, date (dd/MM/yyyy), checkIn (HH:mm),
     * checkOut (""), status, type, location, latitude, longitude, accuracy,
     * distanceMeters, requestId, marcadoPor, clientMockFlag, createdAt y
     * updatedAt. NO se escribe shiftId/turno.
     *
     * Check-in: crea `attendances/{uid}_{yyyyMMdd}`.
     * Check-out: actualiza únicamente `checkOut` y `updatedAt` del mismo doc.
     *
     * Validaciones locales: autenticación, tipo, coordenadas, precisión <= 50m,
     * usuario activo/empleado, empresa, zona horaria, sede activa, geocerca,
     * turno válido y ventana horaria. Si algo falla no se escribe nada.
     */
    suspend fun registerLocalAttendance(
        uid: String,
        tipo: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        isMock: Boolean,
        requestId: String,
        faceVerified: Boolean
    ): AttendanceWriteResult {
        return try {
            if (uid.isBlank() || FirebaseAuth.getInstance().currentUser?.uid != uid) {
                return AttendanceWriteResult.Error("UNAUTHENTICATED")
            }
            if (tipo != "FACIAL" && tipo != "HUELLA") {
                return AttendanceWriteResult.Error("INVALID_TYPE")
            }
            if (tipo == "FACIAL" && !faceVerified) {
                return AttendanceWriteResult.Error("FACE_NOT_VERIFIED")
            }
            if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                !longitude.isFinite() || longitude !in -180.0..180.0
            ) {
                return AttendanceWriteResult.Error("INVALID_COORDINATES")
            }
            if (!accuracy.isFinite() || accuracy < 0f) {
                return AttendanceWriteResult.Error("INVALID_ACCURACY")
            }
            if (accuracy.toDouble() > MAX_ACCURACY_METERS) {
                return AttendanceWriteResult.Error("ACCURACY_TOO_HIGH")
            }
            if (!REQUEST_ID_REGEX.matches(requestId)) {
                return AttendanceWriteResult.Error("INVALID_REQUEST_ID")
            }

            val userRef = db.collection("users").document(uid)
            val userDoc = userRef.get(Source.SERVER).await()
            if (!userDoc.exists()) {
                return AttendanceWriteResult.Error("CALLER_NOT_FOUND")
            }
            if (userDoc.getBoolean("isActive") == false) {
                return AttendanceWriteResult.Error("CALLER_INACTIVE")
            }

            val role = userDoc.getString("role") ?: "employee"
            if (role == "admin") {
                return AttendanceWriteResult.Error("ADMIN_CANNOT_MARK_SELF")
            }
            if (role != "employee") {
                return AttendanceWriteResult.Error("TARGET_NOT_EMPLOYEE")
            }

            val companyId = userDoc.getString("companyId").orEmpty()
            if (companyId.isBlank()) {
                return AttendanceWriteResult.Error("NO_COMPANY_ASSIGNED")
            }

            val sedeId = userDoc.getString("sedeId").orEmpty()
            if (sedeId.isBlank()) {
                return AttendanceWriteResult.Error("NO_SEDE_ASSIGNED")
            }

            val shiftId = userDoc.getString("shiftId").orEmpty()
            if (shiftId.isBlank()) {
                return AttendanceWriteResult.Error("NO_SHIFT_ASSIGNED")
            }

            val userName = userDoc.getString("name")
                ?.takeIf { it.isNotBlank() }
                ?: "Empleado"

            val companyRef = db.collection("companies").document(companyId)
            val companyDoc = companyRef.get(Source.SERVER).await()
            if (!companyDoc.exists()) {
                return AttendanceWriteResult.Error("COMPANY_NOT_FOUND")
            }

            val timezone = companyDoc.getString("timezone")
            if (timezone.isNullOrBlank()) {
                return AttendanceWriteResult.Error("INVALID_TIMEZONE")
            }
            val zoneId = try {
                ZoneId.of(if (timezone.trim() == "UTC-5") "America/Lima" else timezone.trim())
            } catch (e: DateTimeException) {
                return AttendanceWriteResult.Error("INVALID_TIMEZONE")
            }
            val now = ZonedDateTime.now(zoneId)
            val dateYyyyMmDd = now.format(DateTimeFormatter.ofPattern("uuuuMMdd"))
            val dateDisplay = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            val currentHhMm = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            val currentMinutes = now.hour * 60 + now.minute

            val sedeDoc = companyRef.collection("sedes").document(sedeId)
                .get(Source.SERVER).await()
            if (!sedeDoc.exists() || sedeDoc.getBoolean("activa") == false) {
                return AttendanceWriteResult.Error("INVALID_SEDE_CONFIG")
            }
            val sedeData = sedeDoc.data.orEmpty()
            val sedeLat = (sedeData["lat"] as? Number)?.toDouble()
            val sedeLng = (sedeData["lng"] as? Number)?.toDouble()
            val radioMetros = (sedeData["radioMetros"] as? Number)?.toDouble()
            if (sedeLat == null || !sedeLat.isFinite() || sedeLat !in -90.0..90.0 ||
                sedeLng == null || !sedeLng.isFinite() || sedeLng !in -180.0..180.0 ||
                radioMetros == null || !radioMetros.isFinite() || radioMetros !in 20.0..500.0
            ) {
                return AttendanceWriteResult.Error("INVALID_SEDE_CONFIG")
            }

            val distance = haversineMeters(latitude, longitude, sedeLat, sedeLng)
            if (!distance.isFinite()) {
                return AttendanceWriteResult.Error("INVALID_COORDINATES")
            }
            if (distance > radioMetros) {
                return AttendanceWriteResult.Error("OUT_OF_RANGE_GEOFENCE")
            }

            val shiftDoc = companyRef.collection("shifts").document(shiftId)
                .get(Source.SERVER).await()
            if (!shiftDoc.exists()) {
                return AttendanceWriteResult.Error("NO_SHIFT_ASSIGNED")
            }
            val shiftData = shiftDoc.data.orEmpty()
            val startRaw = shiftData["startTime"] as? String
            val endRaw = shiftData["endTime"] as? String
            val toleranceRaw = (shiftData["toleranceMin"] as? Number)?.toDouble()
            if (startRaw == null || endRaw == null || toleranceRaw == null ||
                !toleranceRaw.isFinite()
            ) {
                return AttendanceWriteResult.Error("INVALID_SHIFT_CONFIG")
            }

            val startTime = startRaw.trim()
            val endTime = endRaw.trim()
            if (!TIME_REGEX.matches(startTime) || !TIME_REGEX.matches(endTime)) {
                return AttendanceWriteResult.Error("INVALID_SHIFT_CONFIG")
            }
            val startMinutes = hhMmToMinutes(startTime)
                ?: return AttendanceWriteResult.Error("INVALID_SHIFT_CONFIG")
            val endMinutes = hhMmToMinutes(endTime)
                ?: return AttendanceWriteResult.Error("INVALID_SHIFT_CONFIG")
            if (endMinutes <= startMinutes) {
                return AttendanceWriteResult.Error("UNSUPPORTED_SHIFT")
            }

            val attendanceId = "${uid}_$dateYyyyMmDd"
            val attendanceRef = collection.document(attendanceId)
            // La regla de lectura permite resource == null para poder consultar
            // si el documento del día ya existe sin depender de una query.
            val existing = attendanceRef.get(Source.SERVER).await()

            if (!existing.exists()) {
                val toleranceMin = toleranceRaw.coerceIn(0.0, 120.0).toInt()
                val minAllowedMinutes = startMinutes - EARLY_CHECKIN_MINUTES
                if (currentMinutes < minAllowedMinutes || currentMinutes > endMinutes) {
                    return AttendanceWriteResult.Error("OUT_OF_SCHEDULE_WINDOW")
                }
                val status = if (currentMinutes <= startMinutes + toleranceMin) {
                    "PUNTUAL"
                } else {
                    "TARDANZA"
                }

                val data = hashMapOf<String, Any>(
                    "userId" to uid,
                    "companyId" to companyId,
                    "sedeId" to sedeId,
                    "userName" to userName,
                    "date" to dateDisplay,
                    "checkIn" to currentHhMm,
                    "checkOut" to "",
                    "status" to status,
                    "type" to tipo,
                    "location" to ((sedeData["nombre"] as? String)
                        ?.takeIf { it.isNotBlank() } ?: "Sede"),
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "accuracy" to accuracy.toDouble(),
                    "distanceMeters" to distance.roundToLong(),
                    "requestId" to requestId,
                    "marcadoPor" to uid,
                    "clientMockFlag" to isMock,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                attendanceRef.set(data).await()
                AttendanceWriteResult.Success(
                    movement = "CHECK_IN",
                    status = status,
                    checkIn = currentHhMm,
                    checkOut = null,
                    date = dateDisplay,
                    userName = userName
                )
            } else {
                // Reintento del mismo check-in (respuesta perdida): el
                // requestId quedó guardado en el documento. Devolvemos el
                // mismo resultado en vez de intentar un check-out prematuro.
                if (existing.getString("requestId") == requestId) {
                    return AttendanceWriteResult.Success(
                        movement = "CHECK_IN",
                        status = existing.getString("status").orEmpty(),
                        checkIn = existing.getString("checkIn").orEmpty(),
                        checkOut = existing.getString("checkOut")
                            ?.takeIf { it.isNotBlank() },
                        date = existing.getString("date") ?: dateDisplay,
                        userName = userName
                    )
                }

                val existingCheckOut = existing.getString("checkOut").orEmpty()
                if (existingCheckOut.isNotEmpty()) {
                    return AttendanceWriteResult.Error("ALREADY_COMPLETE")
                }

                val existingStatus = existing.getString("status")
                    ?.takeIf { it.isNotBlank() }
                    ?: return AttendanceWriteResult.Error("CORRUPT_CHECKIN_DATA")
                val existingCheckIn = existing.getString("checkIn")
                    ?.takeIf { it.isNotBlank() }
                    ?: return AttendanceWriteResult.Error("CORRUPT_CHECKIN_DATA")
                val checkInMinutes = hhMmToMinutes(existingCheckIn)
                    ?: return AttendanceWriteResult.Error("CORRUPT_CHECKIN_DATA")

                if (currentMinutes < checkInMinutes + MIN_MINUTES_BEFORE_CHECKOUT) {
                    return AttendanceWriteResult.Error("TOO_SOON")
                }
                if (currentMinutes > endMinutes + MAX_CHECKOUT_OVERTIME_MINUTES) {
                    return AttendanceWriteResult.Error("OUT_OF_WINDOW")
                }

                attendanceRef.update(
                    mapOf(
                        "checkOut" to currentHhMm,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                AttendanceWriteResult.Success(
                    movement = "CHECK_OUT",
                    status = existingStatus,
                    checkIn = existingCheckIn,
                    checkOut = currentHhMm,
                    date = existing.getString("date") ?: dateDisplay,
                    userName = userName
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            AttendanceWriteResult.Error(
                when (e.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE -> "NO_NETWORK"
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> "ATTENDANCE_WRITE_DENIED"
                    else -> "INTERNAL_ERROR"
                }
            )
        } catch (e: Exception) {
            AttendanceWriteResult.Error("INTERNAL_ERROR")
        }
    }

    private fun hhMmToMinutes(value: String): Int? {
        val trimmed = value.trim()
        if (!TIME_REGEX.matches(trimmed)) return null
        val parts = trimmed.split(":").map { it.toIntOrNull() ?: return null }
        return parts[0] * 60 + parts[1]
    }

    /** Haversine en metros; misma fórmula que functions/src/logic/haversine.ts. */
    private fun haversineMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

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
            }?.sortedByDescending { it.checkIn } ?: emptyList()

            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    suspend fun saveAttendance(attendance: Attendance): Result<String> {
        return Result.failure(
            IllegalStateException("El registro manual está deshabilitado hasta que exista registerManualAttendance")
        )
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
            }?.sortedWith(compareByDescending<Attendance> { dateSortKey(it.date) }.thenByDescending { it.checkIn }) ?: emptyList()

            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    fun observeUserAttendances(uid: String): Flow<List<Attendance>> = callbackFlow {
        val query = collection
            .whereEqualTo("userId", uid)

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
            }?.sortedWith(compareByDescending<Attendance> { dateSortKey(it.date) }.thenByDescending { it.checkIn }) ?: emptyList()

            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    private fun dateSortKey(date: String): String {
        val parts = date.split("/")
        return if (parts.size == 3) "${parts[2]}${parts[1].padStart(2, '0')}${parts[0].padStart(2, '0')}" else date
    }

    suspend fun getEmployees(companyId: String = ""): List<Map<String, Any>> {
        var query = db.collection("users")
            .whereEqualTo("role", "employee")
        if (companyId.isNotEmpty()) {
            query = query.whereEqualTo("companyId", companyId)
        }
        return query
            .get()
            .await()
            .documents
            .map { doc -> doc.data?.plus("uid" to doc.id) ?: emptyMap() }
    }

    fun observeEmployees(companyId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        if (companyId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = db.collection("users")
            .whereEqualTo("role", "employee")
            .whereEqualTo("companyId", companyId)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.map { doc ->
                doc.data?.plus("uid" to doc.id) ?: emptyMap()
            } ?: emptyList()
            trySend(items)
        }
        awaitClose { registration.remove() }
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

    suspend fun deleteShift(companyId: String, shiftId: String): Boolean {
        return try {
            db.collection("companies").document(companyId).collection("shifts").document(shiftId).delete().await()
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
            val data = hashMapOf<String, Any>(
                "nombre" to sede.nombre,
                "lat" to sede.lat,
                "lng" to sede.lng,
                "radioMetros" to sede.radioMetros,
                "activa" to sede.activa
            )
            if (sede.id.isEmpty()) {
                data["creadaEn"] = com.google.firebase.Timestamp.now()
                db.collection("companies").document(companyId).collection("sedes").add(data).await()
            } else {
                db.collection("companies").document(companyId).collection("sedes").document(sede.id).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteSede(companyId: String, sedeId: String): Boolean {
        return try {
            db.collection("companies").document(companyId).collection("sedes").document(sedeId).delete().await()
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
