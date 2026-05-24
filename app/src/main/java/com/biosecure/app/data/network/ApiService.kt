package com.biosecure.app.data.network

import com.biosecure.app.data.model.CreateUserRequest
import com.biosecure.app.data.model.DeleteResponse
import com.biosecure.app.data.model.UpdateUserRequest
import com.biosecure.app.data.model.User
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    @GET("users")
    suspend fun getUsers(): UsersResponse

    @GET("users/{id}")
    suspend fun getUserById(@Path("id") id: Int): User

    @POST("posts/add")
    suspend fun registerAttendance(@Body request: AttendanceRequest): PostResponse

    @POST("users/add")
    suspend fun createEmployee(@Body request: EmployeeRequest): EmployeeResponse

    @POST("users/add")
    suspend fun createUser(@Body user: CreateUserRequest): User

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body user: UpdateUserRequest): User

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): DeleteResponse

    @GET("employees/")
    suspend fun getDjangoEmployees(): List<DjangoEmployee>

    @POST("employees/")
    suspend fun createDjangoEmployee(@Body employee: DjangoEmployee): DjangoEmployee

    @PUT("employees/{id}/")
    suspend fun updateDjangoEmployee(@Path("id") id: Int, @Body employee: DjangoEmployee): DjangoEmployee

    @DELETE("employees/{id}/")
    suspend fun deleteDjangoEmployee(@Path("id") id: Int)
}

data class UsersResponse(
    val users: List<User>,
    val total: Int,
    val skip: Int,
    val limit: Int
)

data class AttendanceRequest(
    val userId: Int,
    val userName: String,
    val checkIn: String,
    val scanType: String,
    val location: String
)

data class PostResponse(
    val id: Int,
    val userId: Int? = null,
    val title: String? = null,
    val body: String? = null
)

data class EmployeeRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val cargo: String,
    val departamento: String
)

data class EmployeeResponse(
    val id: Int,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null
)

data class DjangoEmployee(
    val id: Int = 0,
    val first_name: String = "",
    val last_name: String = "",
    val email: String = "",
    val department: String = ""
)