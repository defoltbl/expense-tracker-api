package com.andrii

import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    val id: Int,
    val description: String,
    val amount: Double,
    val category: String,
    val date: String,
    val userId: Int
)