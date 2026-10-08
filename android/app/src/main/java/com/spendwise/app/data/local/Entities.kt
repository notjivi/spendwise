package com.spendwise.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["userId", "spentAt"]), Index(value = ["userId", "syncState"])],
)
data class ExpenseEntity(
    @PrimaryKey val id: String, // client-generated UUID: creatable offline, no server round trip
    val userId: String,
    val amountMinor: Long,
    val currency: String,
    val category: Category,
    val merchant: String?,
    val note: String?,
    val paymentMethod: PaymentMethod,
    val spentAt: Long,
    val createdAt: Long,
    val updatedAt: Long, // the last-write-wins clock
    val isDeleted: Boolean, // soft delete: tombstone must survive until the server has seen it
    val syncState: SyncState,
)

@Entity(tableName = "budgets", indices = [Index(value = ["userId", "syncState"])])
data class BudgetEntity(
    // Deterministic "<uid>:<CATEGORY>": two offline devices setting the same budget collide on the
    // same row (resolved by LWW) instead of creating duplicates.
    @PrimaryKey val id: String,
    val userId: String,
    val category: Category,
    val monthlyLimitMinor: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
    val syncState: SyncState,
)

fun budgetId(userId: String, category: Category) = "$userId:${category.name}"
