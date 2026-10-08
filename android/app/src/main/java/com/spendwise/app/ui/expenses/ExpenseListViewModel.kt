package com.spendwise.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.startMillis
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.repo.DayGroup
import com.spendwise.app.data.repo.ExpenseFilter
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.data.repo.groupByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class DateRange(val start: LocalDate, val end: LocalDate) // both inclusive

data class ExpenseListUiState(
    val loading: Boolean = true,
    val query: String = "",
    val category: Category? = null,
    val range: DateRange? = null,
    val groups: List<DayGroup> = emptyList(),
    val totalMinor: Long = 0,
    val error: String? = null,
) {
    val hasFilters get() = query.isNotBlank() || category != null || range != null
    val isEmpty get() = groups.isEmpty()
}

/** One-off effects (snackbars) go through a Channel, not UiState, so they aren't replayed on rotation. */
sealed interface ListEvent {
    data class Deleted(val id: String) : ListEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val repo: ExpenseRepository,
    private val clock: Clock,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<Category?>(null)
    private val range = MutableStateFlow<DateRange?>(null)
    private val events = Channel<ListEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ListEvent> = events.receiveAsFlow()

    private val results: Flow<List<ExpenseEntity>> = combine(
        // Debounce typing only; clearing the box (empty) applies instantly.
        query.debounce { if (it.isEmpty()) 0L else 250L }.distinctUntilChanged(),
        category,
        range,
    ) { q, c, r ->
        ExpenseFilter(
            query = q.trim(),
            category = c,
            fromMs = r?.start?.startMillis(clock.zone),
            toMs = r?.end?.plusDays(1)?.startMillis(clock.zone), // exclusive upper bound = start of the day after
        )
    }.flatMapLatest { repo.observeExpenses(it) }

    val uiState: StateFlow<ExpenseListUiState> = combine(query, category, range, results) { q, c, r, list ->
        ExpenseListUiState(
            loading = false, query = q, category = c, range = r,
            groups = groupByDay(list, clock.zone), totalMinor = list.sumOf { it.amountMinor },
        )
    }.catch { emit(ExpenseListUiState(loading = false, error = it.message ?: "Could not load expenses")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseListUiState())

    fun onQuery(v: String) { query.value = v }
    fun onCategory(c: Category?) { category.value = c }
    fun onRange(r: DateRange?) { range.value = r }
    fun clearFilters() { query.value = ""; category.value = null; range.value = null }

    fun onDelete(id: String) = viewModelScope.launch {
        repo.delete(id)
        events.send(ListEvent.Deleted(id))
    }

    fun onUndo(id: String) = viewModelScope.launch { repo.undoDelete(id) }
}
