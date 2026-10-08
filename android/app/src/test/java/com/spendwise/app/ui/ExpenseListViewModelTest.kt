package com.spendwise.app.ui

import app.cash.turbine.test
import com.spendwise.app.MainDispatcherRule
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.repo.ExpenseFilter
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.expense
import com.spendwise.app.ui.expenses.DateRange
import com.spendwise.app.ui.expenses.ExpenseListViewModel
import com.spendwise.app.ui.expenses.ListEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = Clock.fixed(Instant.parse("2025-10-13T10:00:00Z"), ZoneOffset.UTC)
    private val repo = mockk<ExpenseRepository>()
    private val filters = mutableListOf<ExpenseFilter>()

    private fun vm(vararg items: com.spendwise.app.data.local.ExpenseEntity): ExpenseListViewModel {
        every { repo.observeExpenses(capture(filters)) } returns flowOf(items.toList())
        return ExpenseListViewModel(repo, clock)
    }

    @Test fun emitsLoadingThenGroupedContent() = runTest(main.dispatcher) {
        val vm = vm(expense("a", amount = 100, spentAt = 1_760_000_000_000), expense("b", amount = 50, spentAt = 1_760_000_000_000))
        vm.uiState.test {
            assertTrue(awaitItem().loading)
            val s = awaitItem()
            assertFalse(s.loading)
            assertEquals(1, s.groups.size)
            assertEquals(150L, s.totalMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun emptyListIsReportedAsEmptyNotLoading() = runTest(main.dispatcher) {
        val vm = vm()
        vm.uiState.test {
            awaitItem() // loading
            val s = awaitItem()
            assertTrue(s.isEmpty && !s.loading && !s.hasFilters)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun searchIsDebouncedButClearingIsInstant() = runTest(main.dispatcher) {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        advanceUntilIdle()
        filters.clear()

        vm.onQuery("sw"); vm.onQuery("swi")
        advanceTimeBy(249)
        assertTrue("no query yet during debounce window", filters.none { it.query.isNotEmpty() })
        advanceTimeBy(2)
        assertEquals(listOf("swi"), filters.map { it.query }) // "sw" was dropped; only the final text hit the DB

        filters.clear()
        vm.onQuery("")
        advanceUntilIdle()
        assertEquals(listOf(""), filters.map { it.query })
    }

    @Test fun dateRangeBecomesInclusiveStartAndExclusiveEnd() = runTest(main.dispatcher) {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.onRange(DateRange(LocalDate.parse("2025-10-01"), LocalDate.parse("2025-10-03")))
        vm.onCategory(Category.FOOD)
        advanceUntilIdle()

        val last = filters.last()
        assertEquals(Instant.parse("2025-10-01T00:00:00Z").toEpochMilli(), last.fromMs)
        assertEquals("end is the start of the NEXT day", Instant.parse("2025-10-04T00:00:00Z").toEpochMilli(), last.toMs)
        assertEquals(Category.FOOD, last.category)
    }

    @Test fun clearFiltersResetsEverything() = runTest(main.dispatcher) {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.onCategory(Category.BILLS); vm.onQuery("x")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasFilters)
        vm.clearFilters(); advanceUntilIdle()
        assertFalse(vm.uiState.value.hasFilters)
    }

    @Test fun deleteEmitsUndoEventAndUndoRestores() = runTest(main.dispatcher) {
        val vm = vm()
        coEvery { repo.delete("a") } just Runs
        coEvery { repo.undoDelete("a") } just Runs

        vm.onDelete("a"); advanceUntilIdle()
        vm.eventFlow.test { assertEquals(ListEvent.Deleted("a"), awaitItem()); cancelAndIgnoreRemainingEvents() }

        vm.onUndo("a"); advanceUntilIdle()
        coVerify { repo.delete("a"); repo.undoDelete("a") }
    }

    @Test fun repositoryErrorSurfacesAsErrorState() = runTest(main.dispatcher) {
        every { repo.observeExpenses(any()) } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("db broke") }
        val vm = ExpenseListViewModel(repo, clock)
        vm.uiState.test {
            awaitItem()
            val s = awaitItem()
            assertEquals("db broke", s.error)
            assertFalse(s.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
