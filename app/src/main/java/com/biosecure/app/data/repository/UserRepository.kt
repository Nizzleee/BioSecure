package com.biosecure.app.data.repository

import com.biosecure.app.data.model.User
import com.biosecure.app.data.model.CreateUserRequest
import com.biosecure.app.data.model.UpdateUserRequest
import com.biosecure.app.data.network.AttendanceRequest
import com.biosecure.app.data.network.EmployeeRequest
import com.biosecure.app.data.network.EmployeeResponse
import com.biosecure.app.data.network.PostResponse
import com.biosecure.app.data.network.RetrofitInstance

class UserRepository {

    suspend fun getUsers(): List<User> {
        return try {
            RetrofitInstance.api.getUsers().users
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUserById(id: Int): User? {
        return try {
            RetrofitInstance.api.getUserById(id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun registerAttendance(request: AttendanceRequest): PostResponse? {
        return try {
            RetrofitInstance.api.registerAttendance(request)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createEmployee(request: EmployeeRequest): EmployeeResponse? {
        return try {
            RetrofitInstance.api.createEmployee(request)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createUser(request: CreateUserRequest): User? {
        return try {
            RetrofitInstance.api.createUser(request)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUser(id: Int, request: UpdateUserRequest): User? {
        return try {
            RetrofitInstance.api.updateUser(id, request)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteUser(id: Int): Boolean {
        return try {
            RetrofitInstance.api.deleteUser(id).isDeleted
        } catch (e: Exception) {
            false
        }
    }
}
