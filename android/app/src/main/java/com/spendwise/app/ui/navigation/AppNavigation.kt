package com.spendwise.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendwise.app.data.repo.AuthState
import com.spendwise.app.security.LockState
import com.spendwise.app.ui.auth.AuthScreen
import com.spendwise.app.ui.budgets.BudgetsScreen
import com.spendwise.app.ui.dashboard.DashboardScreen
import com.spendwise.app.ui.expenses.ExpenseDetailScreen
import com.spendwise.app.ui.expenses.ExpenseFormScreen
import com.spendwise.app.ui.expenses.ExpenseListScreen
import com.spendwise.app.ui.lock.LockScreen
import com.spendwise.app.ui.settings.SettingsScreen

/**
 * The "auth gate": instead of one graph with redirects, we swap between independent graphs based on state.
 * Signing out can't leave stale screens on the back stack because the whole graph leaves composition.
 */
@Composable
fun AppRoot(vm: AuthGateViewModel) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val lock by vm.lockState.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize()) {
        when (auth) {
            AuthState.Loading -> Box(Modifier.fillMaxSize()) // splash screen is still covering this
            AuthState.SignedOut -> AuthNavHost()
            is AuthState.SignedIn -> when (lock) {
                LockState.Unknown -> Box(Modifier.fillMaxSize())
                LockState.Locked -> LockScreen(onUnlocked = vm::unlock)
                LockState.Unlocked -> MainNavHost()
            }
        }
    }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = LoginRoute) {
        composable<LoginRoute> { AuthScreen(isRegister = false, onSwitch = { nav.navigate(RegisterRoute) }) }
        composable<RegisterRoute> { AuthScreen(isRegister = true, onSwitch = { nav.popBackStack() }) }
    }
}

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

@Composable
private fun MainNavHost() {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(ExpensesRoute, "Expenses", Icons.AutoMirrored.Filled.ReceiptLong),
        Tab(DashboardRoute, "Dashboard", Icons.Default.PieChart),
        Tab(BudgetsRoute, "Budgets", Icons.Default.AccountBalanceWallet),
        Tab(SettingsRoute, "Settings", Icons.Default.Settings),
    )
    val destination = nav.currentBackStackEntryAsState().value?.destination
    val showBar = tabs.any { destination?.hasRoute(it.route::class) == true }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true } // keep each tab's scroll/filter state
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        // consumeWindowInsets stops inner Scaffolds from adding the same system-bar padding a second time.
        NavHost(nav, startDestination = ExpensesRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<ExpensesRoute> {
                ExpenseListScreen(onAdd = { nav.navigate(ExpenseFormRoute()) }, onOpen = { nav.navigate(ExpenseDetailRoute(it)) })
            }
            composable<DashboardRoute> { DashboardScreen() }
            composable<BudgetsRoute> { BudgetsScreen() }
            composable<SettingsRoute> { SettingsScreen() }
            composable<ExpenseFormRoute> { ExpenseFormScreen(onDone = { nav.popBackStack() }) }
            composable<ExpenseDetailRoute> {
                ExpenseDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(ExpenseFormRoute(it)) })
            }
        }
    }
}
