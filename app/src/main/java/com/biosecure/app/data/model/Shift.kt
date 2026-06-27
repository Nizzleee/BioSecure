package com.biosecure.app.data.model

data class Shift(
    val id: String = "",
    val name: String = "",
    val startTime: String = "08:00",
    val toleranceMin: Int = 15,
    val endTime: String = "17:00"
) {
    // Computed aliases for backward compatibility
    val checkInStart: String get() = startTime
    val checkInEnd: String get() = endTime
}
