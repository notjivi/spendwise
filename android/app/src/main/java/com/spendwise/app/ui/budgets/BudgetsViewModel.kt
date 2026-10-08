package com.spendwise.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.Money
import com.spendwise.app.core.bounds
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.repo.AlertLevel
import com.spendwise.app.data.repo.BudgetRepository
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.data.repo.alertLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

data class BudgetRow(val category: Category, val limitMinor: Long?, val spentMinor: Long) {
    val level: AlertLevel = if (limitMinor == null) AlertLevel.NONE else alertLevel(spentMinor, limitMinor)
    val progress: Float = if (limitMinor == null || limitMinor <= 0) 0f else (spentMinor.toFloat() / limitMinor).coerceIn(0f, 1f)
}

data class BudgetsUiState(val loading: Boolean = true, val rows: List<BudgetRow> = emptyList())

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    expenses: ExpenseRepository,
    clock: Clock,
) : ViewModel() {
    private val b = YearMonth.now(clock).bounds(clock.zone)

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgets.observeBudgets(),
        expenses.observeCategoryTotals(b.fromMs, b.toMs),
    ) { limits, totals ->
        val limitBy = limits.associate { it.category to it.monthlyLimitMinor }
        val spentBy = totals.associate { it.category to it.total }
        BudgetsUiState(false, Category.entries.map { BudgetRow(it, limitBy[it], spentBy[it] ?: 0L) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    /** Returns false if the text isn't a valid amount (the dialog shows an error and stays open). */
    fun save(category: Category, text: String): Boolean {
        val minor = Money.parseToMinor(text) ?: return false
        viewModelScope.launch { budgets.setLimit(category, minor) }
        return true
    }

    fun clear(category: Category) { viewModelScope.launch { budgets.clear(category) } }
}
