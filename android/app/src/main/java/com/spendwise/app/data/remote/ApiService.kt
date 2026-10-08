package com.spendwise.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("ai/parse-expense")
    suspend fun parseExpense(@Body body: ParseExpenseRequest): ParseExpenseResponse

    @POST("sync/expenses")
    suspend fun syncExpenses(@Body body: SyncRequestDto<ExpenseDto>): SyncResponseDto<ExpenseDto>

    @POST("sync/budgets")
    suspend fun syncBudgets(@Body body: SyncRequestDto<BudgetDto>): SyncResponseDto<BudgetDto>
}
