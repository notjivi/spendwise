package com.spendwise.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.core.toLocalDate
import com.spendwise.app.core.toUtcPickerMillis
import com.spendwise.app.core.utcPickerMillisToDate
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.PaymentMethod
import com.spendwise.app.ui.common.LoadingView
import com.spendwise.app.ui.common.MessageView
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormScreen(onDone: () -> Unit, vm: ExpenseFormViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.eventFlow.collect { if (it is FormEvent.Saved) onDone() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isEdit) "Edit expense" else "Add expense") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        when {
            s.loading -> LoadingView(Modifier.padding(padding))
            s.notFound -> MessageView(Icons.Default.AutoAwesome, "Expense not found", "It may have been deleted.", Modifier.padding(padding), "Go back", onDone)
            else -> Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!s.isEdit) SmartAddCard(s, vm)

                OutlinedTextField(
                    value = s.amount, onValueChange = vm::onAmount, label = { Text("Amount (${s.currency})") }, singleLine = true,
                    isError = s.amountError != null, supportingText = s.amountError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("amount"),
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c -> FilterChip(selected = s.category == c, onClick = { vm.onCategory(c) }, label = { Text(c.label) }) }
                }
                Text("Paid with", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.forEach { p -> FilterChip(selected = s.payment == p, onClick = { vm.onPayment(p) }, label = { Text(p.label) }) }
                }
                OutlinedTextField(value = s.merchant, onValueChange = vm::onMerchant, label = { Text("Merchant (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = s.note, onValueChange = vm::onNote, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(s.spentAt.toLocalDate(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
                }
                s.dateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                s.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::onSave, enabled = !s.saving, modifier = Modifier.fillMaxWidth().testTag("save")) {
                    if (s.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save")
                }
            }
        }
    }

    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = s.spentAt.toLocalDate(ZoneId.systemDefault()).toUtcPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { picker.selectedDateMillis?.let { vm.onDatePicked(utcPickerMillisToDate(it)) }; showDate = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun SmartAddCard(s: ExpenseFormState, vm: ExpenseFormViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("Smart add", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(
                value = s.smartText, onValueChange = vm::onSmartText, minLines = 2, maxLines = 5,
                placeholder = { Text("Paste an SMS or receipt, e.g. “Rs 450 spent at Swiggy on 12 Oct via UPI”") },
                supportingText = { Text("${s.smartText.length}/$MAX_SMART_CHARS") },
                modifier = Modifier.fillMaxWidth().testTag("smartText"),
            )
            Button(onClick = vm::onParse, enabled = s.smartText.isNotBlank() && s.smart !is SmartState.Loading) {
                if (s.smart is SmartState.Loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Fill form")
            }
            when (val st = s.smart) {
                is SmartState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error)
                is SmartState.Filled -> Text(
                    if (st.confidence < 0.6) "Low confidence (${(st.confidence * 100).toInt()}%). Please double-check every field."
                    else "Filled from your text (${(st.confidence * 100).toInt()}% sure). Review, then Save.",
                    color = if (st.confidence < 0.6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
        }
    }
}
