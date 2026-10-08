package com.spendwise.app.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.AppResult
import com.spendwise.app.core.Money
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.PaymentMethod
import com.spendwise.app.data.repo.ExpenseDraft
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.data.repo.SmartAddRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import javax.inject.Inject

sealed interface SmartState {
    data object Idle : SmartState
    data object Loading : SmartState
    data class Error(val message: String) : SmartState
    data class Filled(val confidence: Double) : SmartState
}

data class ExpenseFormState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val notFound: Boolean = false,
    val saving: Boolean = false,
    val amount: String = "",
    val currency: String = "INR",
    val category: Category = Category.OTHER,
    val merchant: String = "",
    val note: String = "",
    val payment: PaymentMethod = PaymentMethod.UPI,
    val spentAt: Long = 0,
    val amountError: String? = null,
    val dateError: String? = null,
    val saveError: String? = null,
    val smartText: String = "",
    val smart: SmartState = SmartState.Idle,
)

sealed interface FormEvent { data object Saved : FormEvent }

const val MAX_SMART_CHARS = 1000

@HiltViewModel
class ExpenseFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ExpenseRepository,
    private val smartAdd: SmartAddRepository,
    private val clock: Clock,
) : ViewModel() {

    // Type-safe nav stores route args in SavedStateHandle under the property name.
    private val expenseId: String? = savedStateHandle["expenseId"]

    private val _state = MutableStateFlow(ExpenseFormState(isEdit = expenseId != null, loading = expenseId != null, spentAt = clock.millis()))
    val state: StateFlow<ExpenseFormState> = _state.asStateFlow()

    private val events = Channel<FormEvent>(Channel.BUFFERED)
    val eventFlow: Flow<FormEvent> = events.receiveAsFlow()

    init {
        if (expenseId != null) viewModelScope.launch {
            val e = repo.getExpense(expenseId)
            _state.update {
                if (e == null || e.isDeleted) it.copy(loading = false, notFound = true)
                else it.copy(
                    loading = false, amount = Money.toInput(e.amountMinor), currency = e.currency, category = e.category,
                    merchant = e.merchant.orEmpty(), note = e.note.orEmpty(), payment = e.paymentMethod, spentAt = e.spentAt,
                )
            }
        }
    }

    fun onAmount(v: String) = _state.update { it.copy(amount = v, amountError = null) }
    fun onCategory(c: Category) = _state.update { it.copy(category = c) }
    fun onMerchant(v: String) = _state.update { it.copy(merchant = v.take(100)) }
    fun onNote(v: String) = _state.update { it.copy(note = v.take(500)) }
    fun onPayment(p: PaymentMethod) = _state.update { it.copy(payment = p) }
    fun onSmartText(v: String) = _state.update { it.copy(smartText = v.take(MAX_SMART_CHARS), smart = SmartState.Idle) }

    fun onDatePicked(date: LocalDate) = _state.update {
        // Keep the time-of-day, change only the date.
        val time = Instant.ofEpochMilli(it.spentAt).atZone(clock.zone).toLocalTime()
        it.copy(spentAt = date.atTime(time).atZone(clock.zone).toInstant().toEpochMilli(), dateError = null)
    }

    /** Smart Add: ask the backend, then PRE-FILL the form. Nothing is saved until the user presses Save. */
    fun onParse() {
        val text = _state.value.smartText.trim()
        if (text.isEmpty() || _state.value.smart is SmartState.Loading) return
        _state.update { it.copy(smart = SmartState.Loading) }
        viewModelScope.launch {
            when (val r = smartAdd.parse(text)) {
                is AppResult.Failure -> _state.update { it.copy(smart = SmartState.Error(r.error.message)) }
                is AppResult.Success -> _state.update {
                    val p = r.value
                    it.copy(
                        amount = Money.toInput(p.amountMinor), currency = p.currency, category = p.category,
                        merchant = p.merchant.orEmpty().take(100), spentAt = p.spentAt ?: it.spentAt,
                        amountError = null, smart = SmartState.Filled(p.confidence),
                    )
                }
            }
        }
    }

    fun onSave() {
        val s = _state.value
        if (s.saving) return
        val minor = Money.parseToMinor(s.amount)
        val tooFuture = s.spentAt > clock.millis() + ONE_DAY_MS
        if (minor == null || tooFuture) {
            _state.update {
                it.copy(
                    amountError = if (minor == null) "Enter an amount like 450 or 450.50" else null,
                    dateError = if (tooFuture) "Date can't be in the future" else null,
                )
            }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                repo.save(ExpenseDraft(expenseId, minor, s.currency, s.category, s.merchant, s.note, s.payment, s.spentAt))
                events.send(FormEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveError = "Could not save. Please try again.") }
            }
        }
    }

    private companion object { const val ONE_DAY_MS = 24L * 60 * 60 * 1000 }
}
