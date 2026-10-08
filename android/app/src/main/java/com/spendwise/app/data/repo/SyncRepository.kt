package com.spendwise.app.data.repo

import com.spendwise.app.core.AppError
import com.spendwise.app.core.AppResult
import com.spendwise.app.data.local.BudgetDao
import com.spendwise.app.data.local.BudgetEntity
import com.spendwise.app.data.local.ExpenseDao
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.remote.ApiService
import com.spendwise.app.data.remote.BudgetDto
import com.spendwise.app.data.remote.ExpenseDto
import com.spendwise.app.data.remote.SyncRequestDto
import com.spendwise.app.data.remote.SyncResponseDto
import com.spendwise.app.data.remote.safeApiCall
import com.spendwise.app.data.remote.toDto
import com.spendwise.app.data.remote.toEntity
import com.spendwise.app.security.SecureStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncOutcome { DONE, RETRY, FAILED }

/** Describes how to sync one entity type, so expenses and budgets share ONE algorithm. */
private class SyncSpec<E, D>(
    val name: String,
    val pending: suspend (uid: String, limit: Int) -> List<E>,
    val idOf: (E) -> String,
    val updatedAtOf: (E) -> Long,
    val toDto: (E) -> D,
    val call: suspend (SyncRequestDto<D>) -> SyncResponseDto<D>,
    val markSynced: suspend (id: String, updatedAt: Long) -> Unit,
    val markFailed: suspend (ids: List<String>) -> Unit,
    val applyRemote: suspend (uid: String, items: List<D>) -> Unit,
)

@Singleton
class SyncRepository @Inject constructor(
    private val auth: AuthRepository,
    private val api: ApiService,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    private val store: SecureStore,
) {
    // Periodic + one-time workers could overlap; two concurrent syncs would race on the same cursor.
    private val mutex = Mutex()

    private val expenseSpec = SyncSpec<ExpenseEntity, ExpenseDto>(
        name = "expenses",
        pending = { uid, n -> expenseDao.pending(uid, n) },
        idOf = { it.id }, updatedAtOf = { it.updatedAt }, toDto = { it.toDto() },
        call = { api.syncExpenses(it) },
        markSynced = { id, at -> expenseDao.markSyncedIfUnchanged(id, at) },
        markFailed = { expenseDao.markFailed(it) },
        applyRemote = { uid, items -> expenseDao.applyRemote(items.map { it.toEntity(uid) }) },
    )

    private val budgetSpec = SyncSpec<BudgetEntity, BudgetDto>(
        name = "budgets",
        pending = { uid, n -> budgetDao.pending(uid, n) },
        idOf = { it.id }, updatedAtOf = { it.updatedAt }, toDto = { it.toDto() },
        call = { api.syncBudgets(it) },
        markSynced = { id, at -> budgetDao.markSyncedIfUnchanged(id, at) },
        markFailed = { budgetDao.markFailed(it) },
        applyRemote = { uid, items -> budgetDao.applyRemote(items.map { it.toEntity(uid) }) },
    )

    suspend fun syncAll(): SyncOutcome = mutex.withLock {
        val uid = auth.currentUid() ?: return@withLock SyncOutcome.DONE
        val first = sync(uid, expenseSpec)
        if (first != SyncOutcome.DONE) first else sync(uid, budgetSpec)
    }

    private suspend fun <E, D> sync(uid: String, spec: SyncSpec<E, D>): SyncOutcome {
        repeat(MAX_BATCHES) {
            val pending = spec.pending(uid, BATCH)
            val sentVersions = pending.associate { spec.idOf(it) to spec.updatedAtOf(it) }
            val request = SyncRequestDto(pending.map(spec.toDto), cursor(spec, uid))

            when (val result = safeApiCall { spec.call(request) }) {
                is AppResult.Failure -> return failureOutcome(result.error, sentVersions.keys.toList(), spec)
                is AppResult.Success -> {
                    val res = result.value
                    res.accepted.forEach { id -> sentVersions[id]?.let { spec.markSynced(id, it) } }
                    // "stale" isn't a failure: the server's newer version arrives in serverChanges and overwrites ours.
                    val hardRejects = res.rejected.filter { it.reason != "stale" }.map { it.id }
                    if (hardRejects.isNotEmpty()) spec.markFailed(hardRejects)
                    spec.applyRemote(uid, res.serverChanges)
                    store.putString(cursorKey(spec, uid), res.serverTime.toString())
                    if (pending.size < BATCH) return SyncOutcome.DONE
                }
            }
        }
        return SyncOutcome.DONE
    }

    private suspend fun <E, D> failureOutcome(error: AppError, ids: List<String>, spec: SyncSpec<E, D>): SyncOutcome =
        when (error) {
            // Transient: leave rows PENDING and let WorkManager back off and retry.
            AppError.Offline, AppError.Unauthorized, is AppError.Server, is AppError.RateLimited -> SyncOutcome.RETRY
            // Permanent: our payload is wrong. Retrying forever won't help; surface as FAILED.
            is AppError.Validation, is AppError.Unknown -> { spec.markFailed(ids); SyncOutcome.FAILED }
        }

    private fun <E, D> cursorKey(spec: SyncSpec<E, D>, uid: String) = "cursor_${spec.name}_$uid"

    private suspend fun <E, D> cursor(spec: SyncSpec<E, D>, uid: String): Long =
        store.getString(cursorKey(spec, uid))?.toLongOrNull() ?: 0L

    private companion object {
        const val BATCH = 500 // matches the server's max changes per request
        const val MAX_BATCHES = 5
    }
}
