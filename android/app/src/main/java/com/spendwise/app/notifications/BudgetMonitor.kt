package com.spendwise.app.notifications

import com.spendwise.app.core.bounds
import com.spendwise.app.data.local.BudgetEntity
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.repo.AlertLevel
import com.spendwise.app.data.repo.AuthRepository
import com.spendwise.app.data.repo.BudgetRepository
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.data.repo.alertLevel
import com.spendwise.app.di.ApplicationScope
import com.spendwise.app.security.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Watches Room (budgets + this month's spend) and fires a notification the first time a threshold is crossed. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BudgetMonitor @Inject constructor(
    private val auth: AuthRepository,
    private val budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
    private val notifier: BudgetNotifier,
    private val store: SecureStore,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private data class Snapshot(val uid: String, val month: YearMonth, val limits: Map<Category, BudgetEntity>, val spent: Map<Category, Long>)

    fun start() {
        scope.launch {
            auth.uidFlow.flatMapLatest { uid ->
                if (uid == null) emptyFlow<Snapshot>() else {
                    val month = YearMonth.now(clock)
                    val b = month.bounds(clock.zone)
                    combine(budgets.observeBudgets(), expenses.observeCategoryTotals(b.fromMs, b.toMs)) { limits, totals ->
                        Snapshot(uid, month, limits.associateBy { it.category }, totals.associate { it.category to it.total })
                    }
                }
            }.collect { snap ->
                for ((category, budget) in snap.limits) {
                    val spentMinor = snap.spent[category] ?: 0L
                    val level = alertLevel(spentMinor, budget.monthlyLimitMinor)
                    if (level == AlertLevel.NONE) continue
                    // Dedupe per (user, category, month): notify once per level, and never re-notify a lower one.
                    val key = "alert_${snap.uid}_${category.name}_${snap.month}"
                    val last = store.getString(key)?.let { runCatching { AlertLevel.valueOf(it) }.getOrNull() } ?: AlertLevel.NONE
                    if (level > last && notifier.notify(category, level, spentMinor, budget.monthlyLimitMinor)) {
                        store.putString(key, level.name)
                    }
                }
            }
        }
    }
}
