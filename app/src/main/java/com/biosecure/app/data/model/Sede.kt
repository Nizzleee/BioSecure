package com.biosecure.app.data.model

import com.google.firebase.Timestamp

data class Sede(
    val id: String = "",
    val nombre: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radioMetros: Int = 100,
    val creadaEn: Timestamp? = null,
    val activa: Boolean = true
)
