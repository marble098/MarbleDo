package com.marbledo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Debt(
    val id: Long = 0,
    val title: String,
    val amount: Double = 0.0,
    val currency: String = "IRR",
    val dueAtEpochMillis: Long? = null,
    val isPaid: Boolean = false,
    val creditor: String = "",
    val description: String = "",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    init {
        require(title.isNotBlank()) { "Debt title cannot be blank" }
        require(amount.isFinite() && amount >= 0.0) { "Debt amount must be finite and cannot be negative" }
    }
}

@Serializable
data class Check(
    val id: Long = 0,
    val title: String,
    val amount: Double = 0.0,
    val currency: String = "IRR",
    val dueAtEpochMillis: Long? = null,
    val isCleared: Boolean = false,
    val bankName: String = "",
    val accountNumber: String = "",
    val description: String = "",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    init {
        require(title.isNotBlank()) { "Check title cannot be blank" }
        require(amount.isFinite() && amount >= 0.0) { "Check amount must be finite and cannot be negative" }
    }
}
