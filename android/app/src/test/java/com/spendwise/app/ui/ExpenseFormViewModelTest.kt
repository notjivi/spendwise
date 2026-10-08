package com.spendwise.app.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.spendwise.app.MainDispatcherRule
import com.spendwise.app.core.AppError
import com.spendwise.app.core.AppResult
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.repo.ExpenseDraft
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.data.repo.ParsedExpense
import com.spendwise.app.data.repo.SmartAddRepository
import com.spendwise.app.expense
import com.spendwise.app.ui.expenses.ExpenseFormViewModel
import com.spendwise.app.ui.expenses.FormEvent
import com.spendwise.app.ui.expenses.MAX_SMART_CHARS
import com.spendwise.app.ui.expenses.SmartState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ExpenseFormViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = Instant.parse("2025-10-13T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = mockk<ExpenseRepository>()
    private val smart = mockk<SmartAddRepository>()

    private fun vm(id: String? = null) =
        ExpenseFormViewModel(SavedStateHandle(if (id == null) emptyMap() else mapOf("expenseId" to id)), repo, smart, clock)

    @Test fun invalidAmountShowsErrorAndDoesNotSave() = runTest(main.dispatcher) {
        val vm = vm()
        listOf("", "abc", "0", "1.234").forEach {
            vm.onAmount(it); vm.onSave(); advanceUntilIdle()
            assertNotNull("'$it' should be rejected", vm.state.value.amountError)
        }
        coVerify(exactly = 0) { repo.save(any()) }
    }

    @Test fun typingClearsTheError() = runTest(main.dispatcher) {
        val vm = vm()
        vm.onSave(); assertNotNull(vm.state.value.amountError)
        vm.onAmount("5"); assertNull(vm.state.value.amountError)
    }

    @Test fun validFormSavesDraftInMinorUnitsAndSignalsSaved() = runTest(main.dispatcher) {
        val draft = slot<ExpenseDraft>()
        coEvery { repo.save(capture(draft)) } returns "id1"
        val vm = vm()
        vm.onAmount("450.50"); vm.onCategory(Category.FOOD); vm.onMerchant("  Swiggy ")

        vm.eventFlow.test {
            vm.onSave(); advanceUntilIdle()
            assertEquals(FormEvent.Saved, awaitItem())
        }
        assertEquals(45_050L, draft.captured.amountMinor)
        assertNull("new expense has no id yet", draft.captured.id)
        assertEquals(Category.FOOD, draft.captured.category)
    }

    @Test fun futureDatesAreRejected() = runTest(main.dispatcher) {
        val vm = vm()
        vm.onAmount("10")
        vm.onDatePicked(java.time.LocalDate.parse("2025-12-25"))
        vm.onSave(); advanceUntilIdle()
        assertNotNull(vm.state.value.dateError)
        coVerify(exactly = 0) { repo.save(any()) }
    }

    @Test fun saveFailureIsShownAndFormStaysEditable() = runTest(main.dispatcher) {
        coEvery { repo.save(any()) } throws IllegalStateException("disk full")
        val vm = vm(); vm.onAmount("10"); vm.onSave(); advanceUntilIdle()
        assertNotNull(vm.state.value.saveError)
        assertEquals(false, vm.state.value.saving)
    }

    @Test fun smartAddPrefillsFormButNeverSaves() = runTest(main.dispatcher) {
        val spent = Instant.parse("2025-10-12T06:30:00Z").toEpochMilli()
        coEvery { smart.parse("Rs 450 at Swiggy") } returns AppResult.Success(ParsedExpense(45_000, "INR", "Swiggy", Category.FOOD, spent, 0.93))
        val vm = vm()
        vm.onSmartText("Rs 450 at Swiggy"); vm.onParse(); advanceUntilIdle()

        val s = vm.state.value
        assertEquals("450.00", s.amount); assertEquals("Swiggy", s.merchant); assertEquals(Category.FOOD, s.category); assertEquals(spent, s.spentAt)
        assertEquals(SmartState.Filled(0.93), s.smart)
        coVerify(exactly = 0) { repo.save(any()) } // AI output requires explicit confirmation
    }

    @Test fun smartAddKeepsExistingDateWhenModelFoundNone() = runTest(main.dispatcher) {
        coEvery { smart.parse(any()) } returns AppResult.Success(ParsedExpense(100, "INR", null, Category.OTHER, null, 0.5))
        val vm = vm(); val before = vm.state.value.spentAt
        vm.onSmartText("x"); vm.onParse(); advanceUntilIdle()
        assertEquals(before, vm.state.value.spentAt)
        assertEquals("", vm.state.value.merchant)
    }

    @Test fun smartAddFailureShowsMessageAndLeavesFieldsAlone() = runTest(main.dispatcher) {
        coEvery { smart.parse(any()) } returns AppResult.Failure(AppError.Validation("This doesn't look like an expense"))
        val vm = vm(); vm.onAmount("77")
        vm.onSmartText("refund of 500"); vm.onParse(); advanceUntilIdle()
        assertEquals(SmartState.Error("This doesn't look like an expense"), vm.state.value.smart)
        assertEquals("77", vm.state.value.amount)
    }

    @Test fun smartTextIsCappedAtServerLimit() = runTest(main.dispatcher) {
        val vm = vm(); vm.onSmartText("x".repeat(MAX_SMART_CHARS + 50))
        assertEquals(MAX_SMART_CHARS, vm.state.value.smartText.length)
    }

    @Test fun editModeLoadsExistingExpense() = runTest(main.dispatcher) {
        coEvery { repo.getExpense("e1") } returns expense("e1", amount = 12_345, merchant = "Uber", category = Category.TRANSPORT)
        val vm = vm("e1"); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.isEdit); assertEquals(false, s.loading)
        assertEquals("123.45", s.amount); assertEquals("Uber", s.merchant); assertEquals(Category.TRANSPORT, s.category)
    }

    @Test fun editModeForMissingOrDeletedExpenseShowsNotFound() = runTest(main.dispatcher) {
        coEvery { repo.getExpense("gone") } returns expense("gone", deleted = true)
        val vm = vm("gone"); advanceUntilIdle()
        assertTrue(vm.state.value.notFound)
    }
}
