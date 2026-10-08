package com.spendwise.app.data.repo

import com.spendwise.app.data.local.BudgetDao
import com.spendwise.app.data.local.BudgetEntity
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.SyncState
import com.spendwise.app.data.local.budgetId
import com.spendwise.app.sync.SyncTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val auth: AuthRepository,
    private val syncTrigger: SyncTrigger,
    private val clock: Clock,
) {
    fun observeBudgets(): Flow<List<BudgetEntity>> =
        auth.uidFlow.flatMapLatest { uid -> if (uid == null) emptyFlow() else dao.observe(uid) }

    suspend fun setLimit(category: Category, limitMinor: Long) = write(category, limitMinor, deleted = false)

    /** Clearing a budget is a soft delete so it propagates to other devices. */
    suspend fun clear(category: Category) = write(category, 0, deleted = true)

    private suspend fun write(category: Category, limitMinor: Long, deleted: Boolean) {
        val uid = checkNotNull(auth.currentUid()) { "Not signed in" }
        val id = budgetId(uid, category)
        val existing = dao.getById(id)
        dao.upsert(
            BudgetEntity(id, uid, category, limitMinor, nextTimestamp(clock.millis(), existing?.updatedAt), deleted, SyncState.PENDING)
        )
        syncTrigger.requestSync()
    }
}
