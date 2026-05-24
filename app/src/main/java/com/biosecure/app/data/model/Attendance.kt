package com.biosecure.app.data.model

data class Attendance(
    val id: Int,
    val userId: Int,
    val userName: String,
    val userImage: String,
    val date: String,
    val checkIn: String,
    val checkOut: String,
    val status: AttendanceStatus,
    val type: ScanType,
    val location: String = "Sede Central"
)

enum class AttendanceStatus {
    PUNTUAL,
    TARDANZA,
    INASISTENCIA,
    EXITOSO,
    FALLIDO
}

enum class ScanType {
    HUELLA,
    FACIAL
}