package com.spendwise.app.data.remote

import com.spendwise.app.data.local.BudgetEntity
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.local.PaymentMethod
import com.spendwise.app.data.local.SyncState

fun ExpenseEntity.toDto() = ExpenseDto(
    id, amountMinor, currency, category.name, merchant, note, paymentMethod.name,
    spentAt, createdAt, updatedAt, isDeleted,
)

/** Rows that come FROM the server are SYNCED by definition. userId comes from the local session, never the wire. */
fun ExpenseDto.toEntity(userId: String) = ExpenseEntity(
    id, userId, amountMinor, currency, Category.fromWire(category), merchant, note,
    PaymentMethod.fromWire(paymentMethod), spentAt, createdAt, updatedAt, isDeleted, SyncState.SYNCED,
)

fun BudgetEntity.toDto() = BudgetDto(id, category.name, monthlyLimitMinor, updatedAt, isDeleted)

fun BudgetDto.toEntity(userId: String) = BudgetEntity(
    id, userId, Category.fromWire(category), monthlyLimitMinor, updatedAt, isDeleted, SyncState.SYNCED,
)
