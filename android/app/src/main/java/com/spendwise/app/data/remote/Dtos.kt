package com.spendwise.app.data.remote

import kotlinx.serialization.Serializable

// userId is deliberately absent: the server derives it from the verified token.
@Serializable
data class ExpenseDto(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val merchant: String? = null,
    val note: String? = null,
    val paymentMethod: String,
    val spentAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable
data class BudgetDto(
    val id: String,
    val category: String,
    val monthlyLimitMinor: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable data class SyncRequestDto<T>(val changes: List<T>, val lastSyncAt: Long)

@Serializable data class RejectedDto(val id: String, val reason: String)

@Serializable
data class SyncResponseDto<T>(
    val accepted: List<String>,
    val rejected: List<RejectedDto> = emptyList(),
    val serverChanges: List<T> = emptyList(),
    val serverTime: Long,
)

@Serializable data class ParseExpenseRequest(val text: String)

@Serializable
data class ParseExpenseResponse(
    val amountMinor: Long,
    val currency: String,
    val merchant: String? = null,
    val category: String,
    val spentAt: Long? = null,
    val confidence: Double,
    val rawEcho: String = "",
)
