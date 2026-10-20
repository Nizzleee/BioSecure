package com.biosecure.app.data.model

data class Attendance(
    val id: String = "",
    val userId: String = "",
    val companyId: String = "",
    val sedeId: String = "",
    val userName: String = "",
    val userImage: String = "",
    val date: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val status: AttendanceStatus = AttendanceStatus.FALLIDO,
    val type: ScanType = ScanType.HUELLA,
    val location: String = "Sede Central",
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class ConfirmationData(
    val userName: String,
    val checkIn: String,
    val date: String,
    val scanType: String,
    val location: String = "Sede Central",
    val status: String = "TARDANZA"
)

enum class AttendanceStatus {
    PUNTUAL,
    TARDANZA,
    INASISTENCIA,
    EXITOSO,
    FALLIDO;

    companion object {
        fun fromString(value: String?): AttendanceStatus {
            return try {
                valueOf(value?.uppercase() ?: "FALLIDO")
            } catch (e: Exception) {
                EXITOSO
            }
        }
    }
}

enum class ScanType {
    HUELLA,
    FACIAL,
    QR;

    companion object {
        fun fromString(value: String?): ScanType {
            return try {
                valueOf(value?.uppercase() ?: "QR")
            } catch (e: Exception) {
                QR
            }
        }
    }
}
