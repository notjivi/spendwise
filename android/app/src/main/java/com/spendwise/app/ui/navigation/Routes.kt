package com.spendwise.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation: routes are @Serializable types, arguments are constructor params (no string building).
@Serializable data object LoginRoute
@Serializable data object RegisterRoute
@Serializable data object ExpensesRoute
@Serializable data object DashboardRoute
@Serializable data object BudgetsRoute
@Serializable data object SettingsRoute
@Serializable data class ExpenseFormRoute(val expenseId: String? = null)
@Serializable data class ExpenseDetailRoute(val id: String)
