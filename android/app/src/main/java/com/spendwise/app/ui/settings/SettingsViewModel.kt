package com.spendwise.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.repo.AuthRepository
import com.spendwise.app.data.repo.ExpenseRepository
import com.spendwise.app.export.CsvExporter
import com.spendwise.app.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lock: AppLockManager,
    private val auth: AuthRepository,
    private val expenses: ExpenseRepository,
    private val clock: Clock,
) : ViewModel() {
    val lockEnabled: StateFlow<Boolean> = lock.enabled

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow: Flow<String> = messages.receiveAsFlow()

    fun suggestedFileName() = "spendwise-${LocalDate.now(clock)}.csv"

    fun setLock(enabled: Boolean) { viewModelScope.launch { lock.setEnabled(enabled) } }

    fun export(uri: Uri) {
        viewModelScope.launch {
            try {
                val rows = expenses.exportAll()
                val csv = CsvExporter.toCsv(rows, clock.zone)
                withContext(Dispatchers.IO) {
                    // UTF-8 BOM so Excel renders ₹ and Hindi correctly.
                    context.contentResolver.openOutputStream(uri)?.use { it.write(("\uFEFF" + csv).toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Could not open destination")
                }
                messages.send("Exported ${rows.size} expenses")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                messages.send("Export failed. Try another location.")
            }
        }
    }

    fun signOut() = auth.signOut()
}
