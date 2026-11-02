package com.mycompany.datatracker.data

data class DataPlan(
    val id: Long,
    val sizeBytes: Long,
    val carrier: String,
    val startMs: Long,
    val expiryMs: Long,
    val priceNaira: Double
)
