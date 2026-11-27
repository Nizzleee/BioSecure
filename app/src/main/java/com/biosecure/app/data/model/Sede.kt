package com.biosecure.app.data.model

data class Sede(
    val id: String = "",
    val nombre: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radioMetros: Int = 100,
    val creadaEn: String? = null,
    val activa: Boolean = true
)
