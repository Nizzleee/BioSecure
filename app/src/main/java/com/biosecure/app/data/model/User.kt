package com.biosecure.app.data.model

data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val image: String = "",
    val role: String = "employee",
    val cargo: String = "",
    val department: String = "",
    val companyId: String = "",
    val fcmToken: String = "",
    val isActive: Boolean = true,
    val shiftId: String = "",
    val sedeId: String = ""
) {
    val id: Int get() = try { uid.hashCode() } catch (e: Exception) { 0 }
}
