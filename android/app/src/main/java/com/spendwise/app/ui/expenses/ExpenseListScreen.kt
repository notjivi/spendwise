package com.spendwise.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.core.Money
import com.spendwise.app.core.utcPickerMillisToDate
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.local.SyncState
import com.spendwise.app.ui.common.ErrorView
import com.spendwise.app.ui.common.LoadingView
import com.spendwise.app.ui.common.MessageView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, vm: ExpenseListViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRange by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.eventFlow.collect { event ->
            when (event) {
                is ListEvent.Deleted -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar("Expense deleted", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) vm.onUndo(event.id)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add expense") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query, onValueChange = vm::onQuery, singleLine = true,
                placeholder = { Text("Search merchant or note") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = { if (state.range == null) showRange = true else vm.onRange(null) },
                        label = { Text(state.range?.let { "${it.start.fmt()} – ${it.end.fmt()}  ✕" } ?: "Date range") },
                    )
                }
                item { FilterChip(selected = state.category == null, onClick = { vm.onCategory(null) }, label = { Text("All") }) }
                items(Category.entries) { c ->
                    FilterChip(selected = state.category == c, onClick = { vm.onCategory(if (state.category == c) null else c) }, label = { Text(c.label) })
                }
            }
            when {
                state.loading -> LoadingView()
                state.error != null -> ErrorView(state.error!!)
                state.isEmpty && state.hasFilters -> MessageView(Icons.Default.Search, "No matches", "Try different filters.", actionLabel = "Clear filters", onAction = vm::clearFilters)
                state.isEmpty -> MessageView(Icons.AutoMirrored.Filled.ReceiptLong, "No expenses yet", "Tap + to add one, or paste an SMS and let AI fill it in.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        item(key = "h-${group.date}") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceBetween) {
                                Text(group.date.dayLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(Money.format(group.totalMinor), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        items(group.items, key = { it.id }) { e ->
                            SwipeRow(onDelete = { vm.onDelete(e.id) }) { ExpenseRow(e, onClick = { onOpen(e.id) }) }
                        }
                    }
                }
            }
        }
    }

    if (showRange) {
        val picker = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                    onClick = {
                        vm.onRange(DateRange(utcPickerMillisToDate(picker.selectedStartDateMillis!!), utcPickerMillisToDate(picker.selectedEndDateMillis!!)))
                        showRange = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } },
        ) { DateRangePicker(state = picker, modifier = Modifier.height(500.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp), Alignment.CenterEnd) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    ) { Surface { content() } } // opaque surface so the red background only shows while swiping
}

@Composable
private fun ExpenseRow(e: ExpenseEntity, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(e.merchant ?: e.category.label) },
        supportingContent = { Text("${e.category.label} · ${e.paymentMethod.label}") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (e.syncState) { // tiny status icon: user can see what hasn't synced yet
                    SyncState.PENDING -> Icon(Icons.Default.CloudQueue, "Waiting to sync", Modifier.padding(end = 8.dp), MaterialTheme.colorScheme.outline)
                    SyncState.FAILED -> Icon(Icons.Default.ErrorOutline, "Sync failed", Modifier.padding(end = 8.dp), MaterialTheme.colorScheme.error)
                    SyncState.SYNCED -> Unit
                }
                Text(Money.format(e.amountMinor, e.currency), style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}

private fun LocalDate.fmt(): String = format(DateTimeFormatter.ofPattern("d MMM"))

private fun LocalDate.dayLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
    }
}
