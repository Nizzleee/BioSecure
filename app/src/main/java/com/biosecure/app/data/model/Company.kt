package com.biosecure.app.data.model

data class Company(
    val id: String = "",
    val name: String = "",
    val horaEntrada: String = "08:00",
    val toleranciaMin: Int = 15,
    val timezone: String = "UTC-5",
    val geoFence: GeoFence = GeoFence()
)

data class GeoFence(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = 100.0
)
