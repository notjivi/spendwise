package com.spendwise.app.ui.budgets

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.core.Money
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.repo.AlertLevel
import com.spendwise.app.ui.common.LoadingView

@Composable
fun BudgetsScreen(vm: BudgetsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }

    // Android 13+ needs runtime consent before we can post the 80%/100% alerts. Ask once, here, where it's relevant.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (state.loading) return LoadingView()

    LazyColumn(Modifier.fillMaxSize()) {
        items(state.rows, key = { it.category }) { row ->
            ListItem(
                modifier = Modifier.clickable { editing = row },
                headlineContent = { Text(row.category.label) },
                supportingContent = {
                    Column(Modifier.fillMaxWidth()) {
                        if (row.limitMinor == null) Text("No budget set · tap to add")
                        else {
                            Text("${Money.format(row.spentMinor)} of ${Money.format(row.limitMinor)}")
                            LinearProgressIndicator(
                                progress = { row.progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                color = when (row.level) {
                                    AlertLevel.OVER -> MaterialTheme.colorScheme.error
                                    AlertLevel.WARN -> Color(0xFFF9A825)
                                    AlertLevel.NONE -> MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    editing?.let { row ->
        var text by remember(row.category) { mutableStateOf(row.limitMinor?.let { Money.toInput(it) }.orEmpty()) }
        var invalid by remember(row.category) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${row.category.label} monthly budget") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; invalid = false }, singleLine = true,
                    label = { Text("Limit (INR)") }, isError = invalid,
                    supportingText = if (invalid) ({ Text("Enter an amount like 5000") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = { TextButton(onClick = { if (vm.save(row.category, text)) editing = null else invalid = true }) { Text("Save") } },
            dismissButton = {
                Column {
                    if (row.limitMinor != null) TextButton(onClick = { vm.clear(row.category); editing = null }) { Text("Remove") }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
