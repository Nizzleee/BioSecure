package com.biosecure.app.data.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Respuesta estándar del backend BioSecure (MongoDB). */
data class ApiResponse<T>(
    val ok: Boolean = true,
    val data: T? = null,
    val error: String? = null,
    val code: String? = null
)

data class ApiUser(
    val uid: String = "",
    val email: String = "",
    val role: String = "employee",
    val name: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val department: String = "",
    val cargo: String = "",
    val companyId: String = "",
    val shiftId: String = "",
    val sedeId: String = "",
    val image: String = "",
    val isActive: Boolean = true
)

data class LoginRequest(val email: String, val password: String)
data class LoginData(val token: String, val user: ApiUser)

data class AttendanceDto(
    val id: String? = null,
    val _id: String? = null,
    val userId: String? = null,
    val uid: String? = null,
    val companyId: String? = null,
    val sedeId: String? = null,
    val userName: String? = null,
    val date: String? = null,
    val checkIn: String? = null,
    val checkOut: String? = null,
    val status: String? = null,
    val type: String? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: String? = null
) {
    val docId: String get() = id ?: _id ?: ""
}

data class RegisterAttendanceRequest(
    val tipo: String = "HUELLA",
    val qrToken: String = "",
    val lat: Double,
    val lng: Double,
    val accuracy: Double = 50.0,
    val requestId: String
)

data class RegisterAttendanceResult(
    val status: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val date: String = "",
    val userName: String = "",
    val type: String = ""
)

data class SedeDto(
    val id: String? = null,
    val _id: String? = null,
    val nombre: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val radioMetros: Int? = null,
    val activa: Boolean? = null,
    val companyId: String? = null
) {
    val docId: String get() = id ?: _id ?: ""
}

data class ShiftDto(
    val id: String? = null,
    val _id: String? = null,
    val name: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val toleranceMin: Int? = null,
    val companyId: String? = null
) {
    val docId: String get() = id ?: _id ?: ""
}

data class CompanyDto(
    val name: String? = null,
    val horaEntrada: String? = null,
    val toleranciaMin: Int? = 15,
    val timezone: String? = null,
    val geoFence: GeoFenceDto? = null,
    val shifts: List<ShiftDto>? = null
)
data class GeoFenceDto(val latitude: Double? = null, val longitude: Double? = null, val radius: Double? = null)

interface MongoApi {
    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): ApiResponse<LoginData>

    @POST("api/auth/google")
    suspend fun loginGoogle(@Body body: Map<String, String>): ApiResponse<LoginData>

    @GET("api/auth/me")
    suspend fun me(@Header("Authorization") auth: String): ApiResponse<ApiUser>

    @GET("api/users")
    suspend fun getUsers(@Header("Authorization") auth: String, @Query("companyId") companyId: String? = null, @Query("role") role: String? = null): ApiResponse<List<ApiUser>>

    @POST("api/users")
    suspend fun createUser(@Header("Authorization") auth: String, @Body user: Map<String, Any?>): ApiResponse<ApiUser>

    @PUT("api/users/{uid}")
    suspend fun updateUser(@Header("Authorization") auth: String, @Path("uid") uid: String, @Body data: Map<String, Any?>): ApiResponse<ApiUser>

    @DELETE("api/users/{uid}")
    suspend fun deleteUser(@Header("Authorization") auth: String, @Path("uid") uid: String): ApiResponse<Boolean>

    @GET("api/companies/{id}")
    suspend fun getCompany(@Header("Authorization") auth: String, @Path("id") id: String): ApiResponse<CompanyDto>

    @PUT("api/companies/{id}")
    suspend fun updateCompany(@Header("Authorization") auth: String, @Path("id") id: String, @Body data: Map<String, Any?>): ApiResponse<CompanyDto>

    @PUT("api/companies/{id}/shifts")
    suspend fun saveShifts(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: Map<String, Any>): ApiResponse<List<ShiftDto>>

    @GET("api/sedes")
    suspend fun getSedes(@Header("Authorization") auth: String, @Query("companyId") companyId: String): ApiResponse<List<SedeDto>>

    @POST("api/sedes")
    suspend fun saveSede(@Header("Authorization") auth: String, @Body sede: Map<String, Any?>): ApiResponse<SedeDto>

    @DELETE("api/sedes/{id}")
    suspend fun deleteSede(@Header("Authorization") auth: String, @Path("id") id: String): ApiResponse<Boolean>

    @GET("api/attendances")
    suspend fun getAttendances(@Header("Authorization") auth: String, @Query("companyId") companyId: String? = null, @Query("uid") uid: String? = null): ApiResponse<List<AttendanceDto>>

    @POST("api/attendances/register")
    suspend fun registerAttendance(@Header("Authorization") auth: String, @Body req: RegisterAttendanceRequest): ApiResponse<RegisterAttendanceResult>

    @PUT("api/attendances/{id}/confirm")
    suspend fun confirmAttendance(@Header("Authorization") auth: String, @Path("id") id: String): ApiResponse<AttendanceDto>

    @GET("api/notifications")
    suspend fun getNotifications(@Header("Authorization") auth: String): ApiResponse<List<Map<String, Any>>>
}
