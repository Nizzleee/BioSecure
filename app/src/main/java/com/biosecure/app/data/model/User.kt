package com.biosecure.app.data.model

data class User(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val image: String,
    val role: String = "employee" // "employee" o "admin"
)