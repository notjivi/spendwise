package com.spendwise.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.bounds
import com.spendwise.app.data.local.CategoryTotal
import com.spendwise.app.data.local.MerchantTotal
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.data.repo.MonthTotal
import com.spendwise.app.data.repo.buildTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val monthLabel: String = "",
    val monthTotalMinor: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val trend: List<MonthTotal> = emptyList(),
    val topMerchants: List<MerchantTotal> = emptyList(),
    val error: String? = null,
) {
    val isEmpty get() = monthTotalMinor == 0L && trend.all { it.totalMinor == 0L }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(repo: ExpenseRepository, clock: Clock) : ViewModel() {
    private val month = YearMonth.now(clock)
    private val thisMonth = month.bounds(clock.zone)
    private val trendFrom = month.minusMonths(5).bounds(clock.zone).fromMs

    val uiState: StateFlow<DashboardUiState> = combine(
        repo.observeCategoryTotals(thisMonth.fromMs, thisMonth.toMs),
        repo.observeAmounts(trendFrom, thisMonth.toMs),
        repo.observeTopMerchants(thisMonth.fromMs, thisMonth.toMs),
    ) { cats, amounts, merchants ->
        DashboardUiState(
            loading = false,
            monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            monthTotalMinor = cats.sumOf { it.total },
            categories = cats.sortedByDescending { it.total },
            trend = buildTrend(amounts, month, clock.zone),
            topMerchants = merchants,
        )
    }.catch { emit(DashboardUiState(loading = false, error = it.message ?: "Could not load dashboard")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
