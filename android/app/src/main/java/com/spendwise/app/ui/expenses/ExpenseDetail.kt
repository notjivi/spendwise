package com.spendwise.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.Money
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.ui.common.LoadingView
import com.spendwise.app.ui.common.MessageView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

sealed interface DetailState {
    data object Loading : DetailState
    data object NotFound : DetailState
    data class Content(val expense: ExpenseEntity) : DetailState
}

@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ExpenseRepository,
) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<DetailState> = repo.observeExpense(id)
        .map { e -> if (e == null || e.isDeleted) DetailState.NotFound else DetailState.Content(e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: ExpenseDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    (state as? DetailState.Content)?.let { c ->
                        IconButton(onClick = { onEdit(c.expense.id) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            DetailState.Loading -> LoadingView(Modifier.padding(padding))
            DetailState.NotFound -> MessageView(Icons.Default.ErrorOutline, "Expense not found", "It may have been deleted.", Modifier.padding(padding), "Go back", onBack)
            is DetailState.Content -> {
                val e = s.expense
                Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(Money.format(e.amountMinor, e.currency), style = MaterialTheme.typography.displaySmall)
                    Field("Merchant", e.merchant ?: "—")
                    Field("Category", e.category.label)
                    Field("Paid with", e.paymentMethod.label)
                    Field("Date", DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(e.spentAt).atZone(ZoneId.systemDefault())))
                    e.note?.let { Field("Note", it) }
                    Field("Sync", e.syncState.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete expense?") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.delete(onBack) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
