package com.spendwise.app

import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.local.PaymentMethod
import com.spendwise.app.data.local.SyncState
import com.spendwise.app.data.remote.ExpenseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

fun expense(
    id: String,
    userId: String = "u1",
    amount: Long = 10_000,
    category: Category = Category.FOOD,
    merchant: String? = "Swiggy",
    note: String? = null,
    spentAt: Long = 1_000,
    updatedAt: Long = 1_000,
    deleted: Boolean = false,
    sync: SyncState = SyncState.PENDING,
) = ExpenseEntity(id, userId, amount, "INR", category, merchant, note, PaymentMethod.UPI, spentAt, 1, updatedAt, deleted, sync)

fun expenseDto(id: String, amount: Long = 10_000, updatedAt: Long = 1_000, deleted: Boolean = false) =
    ExpenseDto(id, amount, "INR", "FOOD", "Swiggy", null, "UPI", 1_000, 1, updatedAt, deleted)

/** Replaces Dispatchers.Main (absent on the JVM) with a test dispatcher, then restores it. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
