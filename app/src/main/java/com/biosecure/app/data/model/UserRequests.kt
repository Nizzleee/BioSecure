package com.biosecure.app.data.model

data class CreateUserRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val company: CompanyRequest
)

data class CompanyRequest(
    val name: String,
    val title: String,
    val department: String
)

data class UpdateUserRequest(
    val firstName: String,
    val lastName: String,
    val email: String
)

data class DeleteResponse(
    val id: Int,
    val isDeleted: Boolean
)
