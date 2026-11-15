package com.marbledo.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debts",
    indices = [Index(value = ["dueAtEpochMillis"]), Index(value = ["isPaid"])],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double = 0.0,
    val currency: String = "IRR",
    val dueAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val isPaid: Boolean = false,
    val creditor: String = "",
    val description: String = "",
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long,
)
