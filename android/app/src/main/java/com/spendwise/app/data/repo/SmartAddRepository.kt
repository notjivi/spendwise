package com.spendwise.app.data.repo

import com.spendwise.app.core.AppResult
import com.spendwise.app.core.map
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.remote.ApiService
import com.spendwise.app.data.remote.ParseExpenseRequest
import com.spendwise.app.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/** A SUGGESTION from the AI. It is never persisted; the user confirms it in the form first. */
data class ParsedExpense(
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val category: Category,
    val spentAt: Long?,
    val confidence: Double,
)

@Singleton
class SmartAddRepository @Inject constructor(private val api: ApiService) {
    suspend fun parse(text: String): AppResult<ParsedExpense> =
        safeApiCall { api.parseExpense(ParseExpenseRequest(text.trim())) }.map {
            ParsedExpense(it.amountMinor, it.currency, it.merchant, Category.fromWire(it.category), it.spentAt, it.confidence)
        }
}
